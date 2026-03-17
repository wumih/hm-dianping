package com.hmdp;
public class UnsafeCounter {
    // 普通的 long 变量
    private static long count = 0;

    public static void main(String[] args) throws InterruptedException {
        Thread[] threads = new Thread[100];

        // 创建 100 个线程
        for (int i = 0; i < 100; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    count++; // 这里会发生数据踩踏！
                }
            });
            threads[i].start();
        }

        // 等所有线程干完活
        for (Thread t : threads) {
            t.join();
        }

        System.out.println("普通 long 最终结果: " + count);
        // 运行结果绝对不是 100000！可能是 98321，每次运行都不一样。
    }
}
