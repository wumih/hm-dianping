package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
}
