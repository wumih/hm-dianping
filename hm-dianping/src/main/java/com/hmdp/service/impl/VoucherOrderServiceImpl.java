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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // B. 基于 Java 内存的阻塞队列（存放门票）
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    // Spring 提供的代理对象，为了确保事务生效
    private IVoucherOrderService proxy;

    // 在类初始化之后立刻执行这个老黄牛线程任务
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 后台老黄牛线程任务：死循环从队列里取单据写库
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1. 获取队列中的订单信息（没拿到就会一直阻塞等待）
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2. 创建订单写库
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
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

        // ④ 既然 Lua 判定通过（返回了 0），马上生成业务凭证（实体类封装）
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        // ⑤ 将凭证扔进阻塞队列，让老黄牛线程慢慢去扣 MySQL
        orderTasks.add(voucherOrder);

        // 获取并保存当前代理对象，专门给底下的异步线程去调用写库事务方法用
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
}
