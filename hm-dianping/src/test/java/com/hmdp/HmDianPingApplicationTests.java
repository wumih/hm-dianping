package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


@SpringBootTest
public class HmDianPingApplicationTests {

    @Resource
    private RedisIdWorker redisIdWorker;

    // 创建一个包含500个线程的线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        // 创建一个 CountDownLatch，初始值为 300
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            // 每次任务执行完，由这个线程去把倒计时减1
            latch.countDown();
        };

        // 记录开始时间
        long begin = System.currentTimeMillis();

        // 提交流程，让 300 个线程去跑上面的任务
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        // 主线程在这卡死等待！等 latch 倒计时走到 0 (即300个线程全喊完工)
        latch.await();

        // 记录结束时间
        long end = System.currentTimeMillis();
        System.out.println("生成 30000 个ID，总共耗时 time = " + (end - begin) + " 毫秒");
    }

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Test
    void testSeckill() throws InterruptedException {
        Long voucherId = 10L;
        int threadNum = 100;
        CountDownLatch latch = new CountDownLatch(threadNum);

        Runnable task = () -> {
            // 设置用户信息，模拟登录
            UserDTO userDTO = new UserDTO();
            userDTO.setId(1L);
            UserHolder.saveUser(userDTO);

            // 执行秒杀
            voucherOrderService.seckillVoucher(voucherId);

            latch.countDown();
        };

        for (int i = 0; i < threadNum; i++) {
            es.submit(task);
        }
        latch.await();
    }

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void loadShopData() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Resource
    private com.hmdp.service.IUserService userService;

    @Test
    void testMultiLogin() throws java.io.IOException {
        // 1. 获取至少1000个用户，如果不够则直接创建
        List<com.hmdp.entity.User> userList = userService.list();
        if (userList == null || userList.size() < 1000) {
            System.out.println("数据库中用户数量不足1000，正在自动生成充当群演...");
            List<com.hmdp.entity.User> newUsers = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                com.hmdp.entity.User u = new com.hmdp.entity.User();
                u.setPhone("138" + cn.hutool.core.util.RandomUtil.randomNumbers(8));
                u.setNickName("user_" + cn.hutool.core.util.RandomUtil.randomString(6));
                newUsers.add(u);
            }
            userService.saveBatch(newUsers);
            userList = userService.list();
        }

        System.out.println("总用户数：" + userList.size());
        // 取前1000个
        List<com.hmdp.entity.User> targetUsers = userList.stream().limit(1000).collect(Collectors.toList());

        // 输出的文件路径（放在项目根目录即可方便找到）
        java.io.File file = new java.io.File("tokens.txt");
        System.out.println("Token将会输出到文件: " + file.getAbsolutePath());
        java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(file));

        System.out.println("========== 开始生成 Token 并将状态写入 Redis =========");
        int count = 0;
        for (com.hmdp.entity.User user : targetUsers) {
            // 2. 生成 token
            String token = cn.hutool.core.lang.UUID.randomUUID().toString(true);
            UserDTO userDTO = cn.hutool.core.bean.BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = cn.hutool.core.bean.BeanUtil.beanToMap(userDTO, new java.util.HashMap<>(),
                    cn.hutool.core.bean.copier.CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue != null ? fieldValue.toString() : ""));

            // 3. 存入 Redis (设置久一点，压测专用)
            String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            // 这里设置1天过期足够压测使用了
            stringRedisTemplate.expire(tokenKey, 24, java.util.concurrent.TimeUnit.HOURS);

            // 4. 写入本地 TXT 文件
            writer.println(token);
            count++;
            if (count % 100 == 0) {
                System.out.println("已生成: " + count + " 个Token并存入Redis");
            }
        }
        writer.flush();
        writer.close();
        System.out.println("========== 完工！1000 个 Token 已经保存在根目录的 tokens.txt 中 =========");
    }

    @Test
    void testHyperLogLog() {
        String[] users = new String[1000];
        int index = 0;
        for (int i = 1; i <= 1000000; i++) {
            users[index++] = "user_" + i;
            if (i % 1000 == 0) {
                index = 0;
                stringRedisTemplate.opsForHyperLogLog().add("hll1", users);
            }
        }
        // 统计数量
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hll1");
        System.out.println("size = " + size);
    }
}

