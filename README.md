# Heima Dianping (黑马点评) 🚀

这是一个基于 Spring Boot + Redis 的高性能移动端点餐/点评平台。本项目核心在于深度应用 Redis 解决分布式场景下的各种挑战，如高并发秒杀、缓存一致性、分布式锁等。
# Heima Dianping (黑马点评) 🚀



🔗 **相关项目**：
- [前端项目仓库](https://github.com/wumih/hm-dianping-frontend.git)
- [后端项目仓库](https://github.com/wumih/hm-dianping.git)


## 🌟 核心技术亮点

本项目主要展示了 Redis 在实战中的多种应用场景：

### 1. 认证与安全 (Authentication)
- **短信登录**：使用 Redis 替代 Session 存储验证码和用户信息，解决分布式 Session 共享问题。
- **Token 机制**：基于 UUID 生成 Token，结合 Redis Hash 结构记录登录状态，实现无状态认证。

### 2. 缓存实战 (Cache Strategies)
- **缓存穿透**：封装工具类，使用**空对象缓存**策略。
- **缓存雪崩**：通过**随机过期时间 (TTL)** 规避大规模缓存同时失效。
- **缓存击穿**：实现**互斥锁 (Mutex Lock)** 和**逻辑过期 (Logical Expiration)** 两种方案，保证高并发下的热点数据安全。
- **封装通用工具**：提供 `CacheClient` 支持链式编程，解耦业务代码与缓存逻辑。

### 3. 分布式 ID 生成器 (Global ID Worker)
- 基于 Redis 自增和时间戳，实现高性能、全局唯一的分布式 ID，保障分表分库后的 ID 冲突问题。

### 4. 高并发秒杀下单 (Seckill Optimization)
- **超卖问题**：使用 **CAS 乐观锁方案（乐观锁 + 版本号）** 解决库存超卖。
- **性能优化**：
    - 使用 **Lua 脚本** 实现秒杀资格判断（库存校验 + 一人一单校验）的原子性。
    - 引入 **Redis Stream** 作为消息队列，实现异步下单逻辑，大幅提升接口响应速度。

### 5. 分布式锁 (Distributed Lock)
- **Redisson 集成**：利用 Redisson 解决传统分布式锁的不可重入、超时释放、无法重试、多节点一致性（Watchdog 机制）等问题。

### 6. 实战功能扩展
- **附近商户**：利用 Redis **GEO** 实现地理位置检索及距离计算。
- **用户签到**：利用 **Bitmap** 实现高效的签到日历及连续签到统计，极致节省内存。
- **UV 统计**：利用 **HyperLogLog** 进行大数据的去重统计（PV/UV）。
- **Feed 流**：利用 **ZSet** (Sorted Set) 实现基于时间线的关注推送功能。

---

## 🛠 技术栈

| 维度 | 技术选型 |
| :--- | :--- |
| **后端** | Spring Boot 2.3.x, MySQL 8.x, MyBatis-Plus |
| **缓存/NoSQL** | **Redis** (核心), Redisson (分布式锁) |
| **前端** | Vue 2.x, Axios, Nginx |
| **工具类** | Hutool, Lombok |

---

## 📂 项目目录说明

```text
hm-dianping
├── src/main/java/com/hmdp
│   ├── config        # 技术组件配置 (Redisson, MVC, MyBatisPlus)
│   ├── controller    # RESTful API 控制层
│   ├── dto           # 数据传输对象 (UserDTO, Result)
│   ├── entity        # 数据库实体类
│   ├── mapper        # MyBatis Mapper 接口
│   ├── service       # 业务逻辑层 (包括核心秒杀逻辑)
│   └── utils         # 工具类 (RedisIdWorker, CacheClient, UserHolder)
└── src/main/resources
    ├── lua           # 秒杀判断 Lua 脚本
    └── application.yml # 环境配置
```

---

## 🚀 快速入门

### 1. 环境准备
- JDK 1.8+
- MySQL 8.0+
- Redis 6.0+

### 2. 数据库初始化
- 执行 `src/main/resources` 下的 SQL 脚本，创建 `hmdp` 数据库并导入初始数据。

### 3. 修改配置
- 在 `application.yml` 中修改 MySQL 和 Redis 的连接信息（地址、端口、密码）。

### 4. 启动项目
- 运行 `HmDianPingApplication` 主类即可启动后端。
- 配合配套的 Nginx/前端项目即可完成完整交互。

---

## 📜 更新日志
详见 [CHANGELOG.md](file:///d:/computer2/7%E3%80%81Redis%E5%85%A5%E9%97%A8%E5%88%B0%E5%AE%9E%E6%88%98%E6%95%99%E7%A8%8B/Redis-%E7%AC%94%E8%AE%B0%E8%B5%84%E6%96%99/02-%E5%AE%9E%E6%88%98%E7%AF%87/%E8%B5%84%E6%96%99/hm-dianping/CHANGELOG.md)

---

> [!TIP]
> 这是一个用于演示 Redis 实战的高质量项目，如果你在学习过程中遇到各种并发或缓存一致性问题，可以参考 `service` 层的源码实现。
