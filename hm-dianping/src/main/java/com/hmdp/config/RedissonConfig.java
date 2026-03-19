package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        // 配置
        Config config = new Config();
        // 根据目前 application.yaml 中的配置，redis 是本地的，且没有密码
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");

        // 如果 application.yaml 以后加上了密码，请解开下方的注释并填入密码
        // config.useSingleServer().setPassword("your_password");

        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
