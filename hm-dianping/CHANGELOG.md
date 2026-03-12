# Changelog

## [v1.2.0] - 2026-03-13

### ✨ 新增功能（Features）

#### 缓存工具类封装 `CacheClient`
- **新增** `com.hmdp.utils.CacheClient` 通用缓存工具类，基于 `StringRedisTemplate` 封装了 5 个核心方法：
  - `set()`：将任意对象序列化为 JSON 存入 Redis，支持自定义 TTL
  - `setWithLogicalExpire()`：附带逻辑过期时间存入 Redis（不设 TTL，用于热点数据预热）
  - `queryWithPassThrough()`：带防缓存穿透的通用查询（空值写入策略）
  - `queryWithLogicalExpire()`：带防缓存击穿的通用查询（逻辑过期方案）
  - `queryWithMutex()`：带防缓存击穿的通用查询（互斥锁方案）
- 工具类通过 **Java 泛型** 和 **函数式接口（Function）** 实现对任意实体类型的通用支持
- 内置固定大小线程池（10线程）用于异步重建缓存，防止线程无限膨胀

#### 商铺缓存策略升级
- **新增** 互斥锁方案解决缓存击穿（`queryWithMutex`），核心机制：
  - 基于 Redis `SETNX` 命令实现分布式互斥锁（`tryLock` / `unlock`）
  - 锁带 10 秒 TTL，防止程序崩溃后死锁（防死锁设计）
  - 拿到锁后执行 Double Check，避免重复查询数据库
  - 未获取到锁的线程休眠 50ms 后递归重试
- **新增** 逻辑过期方案解决缓存击穿（`queryWithLogicalExpire`），核心机制：
  - Redis key 永不过期，在 value 中嵌入逻辑过期时间戳（`RedisData` 对象）
  - 发现逻辑过期时，当前线程立刻返回旧数据（零等待）
  - 异步开启后台线程重建缓存，互斥锁防止多线程重复重建

### 🔧 重构（Refactor）

#### `ShopServiceImpl` 大幅简化
- 将原有 120+ 行的手写缓存逻辑全部移至 `CacheClient` 工具类
- `queryById` 方法精简为 7 行调用（原为 60+ 行），消除重复代码
- 通过注释标注了如何一行代码在互斥锁方案和逻辑过期方案之间切换

#### `IShopService` 接口
- 新增 `queryById(Long id)` 和 `update(Shop shop)` 方法签名

#### `ShopController` 更新
- `queryShopById` 接口对接 `queryById` 服务方法
- `updateShop` 接口对接 `update` 服务方法

### 🐛 修复（Bug Fixes）

- 修复 `application.yaml` 中 Redis 连接地址配置错误（`192.168.150.101` → `127.0.0.1`）

### 📖 技术说明（Technical Notes）

#### 缓存三大问题及对应解决方案

| 问题 | 原因 | 解决方案 |
|---|---|---|
| **缓存穿透** | 查询不存在的数据，绕过缓存直打数据库 | 空值写入（`queryWithPassThrough`） |
| **缓存击穿** | 热点 key 过期，大量并发同时查数据库 | 互斥锁方案 / 逻辑过期方案 |
| **缓存雪崩** | 大量 key 同时过期导致数据库崩溃 | 随机 TTL / Redis 集群 |

#### 互斥锁 vs 逻辑过期 选型建议

| 维度 | 互斥锁方案 | 逻辑过期方案 |
|---|---|---|
| 数据一致性 | 强（总是最新数据） | 弱（短暂旧数据） |
| 用户体验 | 有卡顿感 | 极速丝滑 |
| 适用场景 | 价格、库存等强一致性数据 | 商铺展示等高并发热点数据 |

---

## [v1.1.0] - 2026-03-10

### ✨ 新增功能

- 实现基于 Redis 的用户登录 Token 方案（替代 Session）
- 新增 `LoginInterceptor` 和 `RefreshTokenInterceptor` 登录拦截器
- 使用 `StringRedisTemplate` 存储验证码和用户 Token

---

## [v1.0.0] - 2026-03-06

### ✨ 初始版本

- 完成项目基础框架搭建（Spring Boot + MyBatis-Plus + Redis）
- 实现手机号验证码登录/注册功能
- 连接 MySQL 数据库，完成基础商铺信息查询接口
