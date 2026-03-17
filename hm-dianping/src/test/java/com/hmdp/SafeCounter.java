package com.hmdp;

import java.util.concurrent.atomic.AtomicLong;

public class SafeCounter {
    // 换成自带“无敌金身”的 AtomicLong
    private static AtomicLong count = new AtomicLong(0);

    public static void main(String[] args) throws InterruptedException {
        Thread[] threads = new Thread[100];

        for (int i = 0; i < 100; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    count.incrementAndGet(); // 安全的 +1 操作
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        System.out.println("AtomicLong 最终结果: " + count.get());
        // 运行结果永远是稳稳的 100000！
    }
}
