package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 新增 Redis Stream 与 Hutool 相关依赖
import cn.hutool.core.bean.BeanUtil;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private com.hmdp.service.IVoucherService voucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 静态加载 Lua 脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // A. 异步处理线程池（老黄牛）
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // B. 不再使用 Java 内存的阻塞队列，废弃！
    // private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    // Spring 提供的代理对象，为了确保事务生效
    private IVoucherOrderService proxy;

    // 在类初始化之后立刻执行这个老黄牛线程任务
    @PostConstruct
    private void init() {
        try {
            // 项目启动时，自动尝试并创建 Redis Stream 队列与消费者组：XGROUP CREATE stream.orders g1 0 MKSTREAM
            stringRedisTemplate.opsForStream().createGroup("stream.orders", "g1");
        } catch (Exception e) {
            log.error("消费者组已经存在或者创建失败", e);
        }
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 后台老黄牛线程任务：死循环从 Redis Stream 里取单据写库
    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";

        @Override
        public void run() {
            while (true) {
                try {
                    // 1. 获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );

                    // 2. 判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        // 如果获取失败，说明没有消息，继续下一次循环重试
                        continue;
                    }

                    // 3. 解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true, false);

                    // 4. 获取成功，真正执行写入 MySQL 数据库的锁操作
                    handleVoucherOrder(voucherOrder);

                    // 5. ACK确认 XACK stream.orders g1 id
                    // 极其重要！告知 Redis 这个包裹已经完美处理！
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理正常流订单异常，准备去 pending-list 去拯救:", e);
                    // 发生异常，该订单会驻留在 pending-list 中，交给此方法去循环重试
                    handlePendingList();
                }
            }
        }

        // 拯救因为宕机、突然报错等遗留在“待确认列表”里的孤儿大怨种单据
        private void handlePendingList() {
            while (true) {
                try {
                    // 1. 获取 pending-list 中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                    // 注意这里的 0，代表拿历史遗留的、已被读取但没 ACK 的孤儿数据！
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );

                    // 2. 判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        // 如果获取失败，说明 pending-list 没有异常消息了，拯救任务结束，跳出循环！
                        break;
                    }

                    // 3. 解析消息中的孤儿订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true, false);

                    // 4. 再次尝试下单
                    handleVoucherOrder(voucherOrder);

                    // 5. 再次确认！拯救成功后从 pending 表彻底划掉！
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理 pending-list 孤儿订单依旧异常", e);
                    // 依然异常？休眠一下再次重试，不要打爆 CPU
                    try {
                        Thread.sleep(20);
                    } catch (Exception ex) {
                        log.error("休眠被打断", ex);
                    }
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1. 获取用户
        Long userId = voucherOrder.getUserId();
        // 2. 依然需要创建锁对象兜底（虽然 Lua 已经挡住了并发，但为了万无一失）
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 3. 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 4. 判断是否获得锁成功
        if (!isLock) {
            log.error("不允许重复下单！");
            return;
        }
        try {
            // 注意：此时是在子线程中，不能用 AopContext 去获取代理，我们要使用主线程传过来的 proxy（这是个教学代码的常见坑）
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // ① 获取用户和订单 ID
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // ② 执行 Lua 脚本（极速判断资格兼扣 Redis 库存）
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );

        int r = result.intValue();

        // ③ 判断 Lua 返回结果
        if (r != 0) {
            // 不为0，代表没有购买资格。1 是库存不足，2 是一人一单限制
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // ④ Lua 判定完成（如果通过返回 0），同时 Lua 脚本内已经发出了 XADD 指向了 Stream。
        // 所以我们现在代码里不需要再去自己组装 VoucherOrder 对象往 JVM 内存的 BlockingQueue 里扔了。

        // 获取并保存当前代理对象，专门给底下的异步线程去调用事务方法用
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        // ⑥ 不等 MySQL 写完，立刻给前端小伙子报喜！
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // 5.1 查询订单验证
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2 判断是否真的存在（兜底，其实 Lua 已经拦住了）
        if (count > 0) {
            log.error("经过异步核对，发现该用户实际上已经购买过一次！");
            return;
        }

        // 6. 物理扣减 MySQL 的真实库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            log.error("经过异步核对，发现 MySQL 库存已不足！");
            return;
        }

        // 7. 正式写入数据库创建订单记录
        save(voucherOrder);
    }

    @Override
    public Result queryMyOrders(Integer current) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();

        // 2. 分页查询
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VoucherOrder> pageInfo = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, 10);
        query().eq("user_id", userId).orderByDesc("create_time").page(pageInfo);

        // 3. 拿到订单基本数据 (长得全是无意义纯数字 ID，前端没法看)
        java.util.List<VoucherOrder> records = pageInfo.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok(java.util.Collections.emptyList());
        }

        // 4. ================= 全新升级：组装高级显示数据 =================
        java.util.List<com.hmdp.dto.VoucherOrderVO> voList = new java.util.ArrayList<>();
        for (VoucherOrder order : records) {
            com.hmdp.dto.VoucherOrderVO vo = new com.hmdp.dto.VoucherOrderVO();
            // ① 复制订单基础属性 (ID 强行转为 String 防网页渲染崩坏)
            vo.setId(String.valueOf(order.getId()));
            vo.setVoucherId(order.getVoucherId());
            vo.setStatus(order.getStatus());
            vo.setCreateTime(order.getCreateTime());

            // ② 重头戏：查询此订单对应的真正的代金券是谁
            com.hmdp.entity.Voucher voucher = voucherService.getById(order.getVoucherId());
            if (voucher != null) {
                vo.setTitle(voucher.getTitle());
                vo.setSubTitle(voucher.getSubTitle());
                vo.setPayValue(voucher.getPayValue());
                vo.setActualValue(voucher.getActualValue());
            } else {
                vo.setTitle("神秘代金券（已失效）");
            }
            voList.add(vo);
        }

        // 5. 将这堆包装精美的中文字段集合，华丽地抛还给前端！
        return Result.ok(voList);
    }
}
