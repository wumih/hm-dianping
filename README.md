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

### 7. 性能调优与容量评估 (Performance Benchmarks)

- **多环境隔离最佳实践**：采用 Spring Boot Profile 机制严格分离 `dev` 与 `test` 配置。
- **连接池扩容调优**：突破默认参数死锁，将 Redis 客户端 (Lettuce) 的 `max-active` 激增至 200，并设置 `max-wait` 熔断生命线，解决高并发踩踏拥堵导致的 Tomcat 假死。
- **[场景一] 高并发读优化基准测试 (JMeter 3000 量级瞬时并发 / 查询商铺)**：
  
  通过对比不同缓存状态下的压测表现，验证了 Redis 架构在极高并发下的吞吐量跃升与防击穿能力：

  | 测试阶段状态 | 样本数 | 平均响应 (RT) | 最大毛刺 | 异常率 | 吞吐量 (QPS) |
  | :--- | :--- | :--- | :--- | :--- | :--- |
  | **① 裸查 MySQL (基准)** | 3000 | 2 ms | 43 ms | 0.00% | 2371.5/sec |
  | **② Redis 冷启动 (防击穿)** | 3000 | 297 ms | - | 0.00% | 1765.0/sec |
  | **③ Redis 完全预热 (满血)** | 3000 | **1 ms** | **16 ms** | **0.00%** | **2437.0/sec** |

  - **裸查 MySQL (内存 Buffer 级命中小表)**：作为基准测试，平均 RT 2ms，峰值 QPS 约 `2371/sec`。
  - **[容灾护航] Redis 冷启动防击穿**：大量请求被互斥锁拦截，在自旋中等待单点锁创建。平均 RT 被刻意拉长至 `297ms`，并发降至 `1765/sec`，但成功保住底层 DB 命脉，实现零穿透。
  - **[全热点满血态] Redis 缓存完全预热**：命中率达 100%，消除互斥锁自旋。平均 RT **暴降至物理极限 1ms**，最大毛刺收敛至仅 16ms，系统整体吞吐量升维至 **`2437/sec`**。

- **[场景二] 极限并发写抗压测试 (JMeter 1000 量级千人千面抢单 / 异步秒杀)**：
  - **大流量防刷与超卖防线**：使用批量生成脚本制造 1000 个带真实独立 Token 的测试并发，狂轰仅有 100 库存的单品。
  - **性能表现与数据一致性**：凭借 Redis Lua 脚本前置拦截和 Stream 异步队列落库架构，**吞吐量维持在 `932/sec` 峰值级水平，不仅平均 RT 压控在极速的 `64ms`**，且订单落盘后数据库 `stock` **完美精准扣减至 `0`**，在 1 秒内吃下 10 倍秒杀洪峰的前提下，彻底杜绝超卖灾难！
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
