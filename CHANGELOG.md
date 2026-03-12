# 项目更新日志 (Changelog)

---

## [v1.2.0] - 2026-03-13 | Redis 缓存策略升级

### ✨ 新增功能

#### 缓存工具类封装 `CacheClient`
- **新增** `com.hmdp.utils.CacheClient` 通用缓存工具类，封装 5 个核心方法：
  - `set()`：将任意对象序列化为 JSON 存入 Redis，支持自定义 TTL
  - `setWithLogicalExpire()`：附带逻辑过期时间存入 Redis（热点数据预热）
  - `queryWithPassThrough()`：带防缓存穿透的通用查询（空值写入策略）
  - `queryWithLogicalExpire()`：带防缓存击穿的通用查询（逻辑过期方案）
  - `queryWithMutex()`：带防缓存击穿的通用查询（互斥锁方案）
- 工具类通过 **Java 泛型** 和 **函数式接口（Function）** 实现对任意实体类型的通用支持
- 内置固定大小线程池（10线程）用于异步重建缓存，防止线程无限膨胀

#### 商铺缓存策略升级
- 新增**互斥锁方案**解决缓存击穿：
  - 基于 Redis `SETNX` 命令实现分布式互斥锁
  - 锁带 10 秒 TTL 防止死锁
  - Double Check 避免重复查询数据库
- 新增**逻辑过期方案**解决缓存击穿：
  - Redis key 永不过期，value 中嵌入逻辑过期时间戳
  - 发现逻辑过期时立刻返回旧数据（零等待）
  - 异步开启后台线程重建缓存

### 🔧 重构

- `ShopServiceImpl` 从 120+ 行精简为 ~40 行，全部委托 `CacheClient` 工具类处理
- `IShopService` 新增 `queryById` 和 `update` 方法签名

### 🐛 修复

- 修复 `application.yaml` 中 Redis 连接地址错误（`192.168.150.101` → `127.0.0.1`）

### 📖 技术说明

| 缓存问题 | 原因 | 解决方案 |
|---|---|---|
| 缓存穿透 | 查询不存在的数据，绕过缓存打穿数据库 | 空值写入（`queryWithPassThrough`） |
| 缓存击穿 | 热点 key 过期，大量并发同时查数据库 | 互斥锁方案 / 逻辑过期方案 |
| 缓存雪崩 | 大量 key 同时过期导致数据库崩溃 | 随机 TTL / Redis 集群 |

---

## [v1.1.0] - 2026-03-10 | 双层拦截器架构，重构用户登录鉴权防护

### ✨ 新增功能

**1. 全局 Token 无感续期（`RefreshTokenInterceptor`）**
- 文件：`src/main/java/com/hmdp/utils/RefreshTokenInterceptor.java`
- 第一层拦截器（Order=0），拦截全局 `/**` 路由。携带合法 Token 即触发 `ThreadLocal` 用户信息注入，并重置 Redis Token 过期时间至 30 分钟。`afterCompletion` 强制清除 `ThreadLocal` 防止内存泄漏。

**2. 核心业务拦截管控（`LoginInterceptor`）**
- 文件：`src/main/java/com/hmdp/utils/LoginInterceptor.java`
- 第二层拦截器（Order=1），专门负责业务鉴权。依赖 `ThreadLocal` 中是否存在用户信息，无则返回 `401`。

**3. MVC 拦截器注册中心（`MvcConfig`）**
- 文件：`src/main/java/com/hmdp/config/MvcConfig.java`
- 通过 `registry.addInterceptor()` 将两层拦截器挂载，`.order()` 锁定执行优先级。

**4. 用户控制器重构（`UserController`）**
- `login` 逻辑下发至 Service 层，`me()` 接口接入真实 `UserHolder.getUser()`。

### 🐛 修复

- 修复 Nginx 静态页 `login.html` 中用户协议勾选框无法点击的 Bug（`radio` 标签缺失 `id="readed"`）

### 💡 架构亮点

- **避免单点雪崩**：拦截器拆分分离了"Token 续期"与"登录鉴权"职能
- **并发安全**：全面弃用 Session，转向 `ThreadLocal` + Redis Token 方案

---

## [v1.0.0] - 2026-03-06 | 初始版本

### ✨ 初始版本

- 完成项目基础框架搭建（Spring Boot + MyBatis-Plus + Redis）
- 实现手机号验证码登录/注册功能
- 连接 MySQL 数据库，完成基础商铺信息查询接口
