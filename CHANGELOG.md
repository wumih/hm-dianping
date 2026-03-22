# 项目更新日志 (Changelog)

---

## [v2.2.0] - 2026-03-22 | Redis 进阶应用：BitMap 签到、GEO 搜索与 HLL 统计

### ✨ 新增功能

#### 1. 基于 BitMap 的高性能用户签到 (`UserServiceImpl`)
- **签到功能 (`sign`)**：利用 Redis `SETBIT` 指令，以用户 ID 和月份为 Key，将日期偏移量设为 1。**优势**：极度节省空间，1 个月仅需约 4 字节即可记录一个用户的签到状态。
- **连续签到统计 (`signCount`)**：利用 `BITFIELD` 一次性获取本月至今的所有位数据，配合 **位运算 (`& 1` 和 `>>> 1`)** 从今日起向后倒数连续的 1。**核心优化**：避免了循环查询 Redis 的低效操作，整个统计过程在内存中完成，性能达到 O(1) 级别。

#### 2. 基于 GEO 的附近商铺搜索 (`ShopController`)
- **地理位置搜索**：升级 `queryShopByType` 接口，引入 `GEOSEARCH` 命令（由 Spring Data Redis 2.6.2+ 支持）。
- **功能增强**：支持根据用户当前的经纬度坐标，自动寻找指定范围（如 5km）内的商铺，并按距离从近到远排序，同时将计算出的精确 `distance` 返回前端。

#### 3. 基于 HyperLogLog 的巨量 UV 统计 (单元测试)
- **UV 去重计数**：在 `HmDianPingApplicationTests` 中实现 `testHyperLogLog` 验证案例。
- **核心价值**：演示了如何在 **误差率 < 0.81%** 的前提下，仅用 **16KB** 内存完成 1,000,000 级不重复访客的基数统计（PFADD/PFCOUNT），完美替代了传统的巨型 Set 集合。

### 🔧 优化与调整 (Improvements)

- **Redis 常量统一化**：在 `RedisConstants` 中新增 `USER_SIGN_KEY`，规范化 Key 构建路径。
- **接口扩展**：在 `UserController` 和 `IUserService` 中平滑接入签到相关业务。
- **测试覆盖**：新增针对大数据量场景下的 Redis 特种数据结构压力测试。

---

## [v2.1.0] - 2026-03-22 | 关注、Feed流核心功能与全链路Bug修复

### ✨ 新增功能

#### 1. 用户关注与取关机制 (`FollowServiceImpl`)
- 实现对关注用户的双写拦截：数据同步持久化至 MySQL `tb_follow` 并结构化缓存至 Redis `Set` 数据结构，为后续求两个用户的“共同关注”交集（`SINTER`）提供 O(1) 性能保障。

#### 2. Feed 流主动推送与滚动分页拉取 (`BlogServiceImpl`)
- **推模式推送 (Push)**：在用户发布探店笔记时，利用已建好的 Redis `Set` 集合查询该用户的所有粉丝，并将笔记 ID 以增量形式群发推送至每一位粉丝的 Redis 收件箱（使用 `ZSet` 表，Score 设为发文时间戳）。
- **拉模式收取 (Pull)**：在个人主页“关注”分页，利用 `ZREVRANGEBYSCORE` 实现极其复杂的游标查询分页机制。完美解决由于传统 `limit offset` 翻页在连续高频发文期产生的**数据漏读或重复读**致命问题。

### 🐛 重点修复 (Bug Fixes) - 着重强调

#### 1. 修复“关注/粉丝数目永久为0不变”问题的底层大坑
- **症状**：在个人主页“关注”界面，无论关注多少人，UI 始终雷打不动地显示 `关注(0)` 和 `粉丝(0)`。
- **根因分析**：黑马点评原始的“手机验证码快速注册”功能代码，偷工减料地只往主表 `tb_user` 插入基本数据，**竟完全遗漏了为新用户初始化用来计数子表 `tb_user_info`！**这导致后来只要试图去查用户的行执行 `followee + 1`，整条 UPDATE 语句就会像打在棉花上一样静默失败（影响 0 行）。
- **极客抢救方案**：在 `FollowServiceImpl` 的关注动作逻辑里追加极具进攻性的抢救包——若检测到 `UPDATE` 影响 0 行，则当机立断直接 `new` 一个全新的 `UserInfo` 实体对象；同时发现在强行绑定主键 ID 存库时被实体类里硬编码的自增生成策略（`IdType.AUTO`）阻挠，一并杀入实体类将其改为 `IdType.INPUT`，一气呵成地执行 `save()`，彻底盘活了这座长期陷入死局的数据孤岛。

#### 2. 修复个人主页“无法点击进入关注人详情和笔记详情”前端失联
- **症状**：测试过程中，在查阅关注列表发出的动态卡片时，鼠标点击那个人的小号头像毫无反应，狂点他的那篇巨大配图的笔记也弹不出详情页。
- **根因分析**：原作者前端在此处严重缺斤少两，整个列表竟然活生生漏写了最常用的组件 Vue onClick 互动事件监听。
- **极客抢救方案**：深入 Nginx 的纯前端静态页面体系，亲自手动为相关的头像和昵称 `div` 逐一补齐 `@click="toOtherInfo(b)"` 方法；为配图与正文标签补齐 `@click="toBlogDetail(b)"` 并在下方的原生 JavaScript 代码块 `methods` 里生生手撕打通了跳往 `other-info.html` 及 `blog-detail.html` 的全部链接流转与防御拦截线路。

#### 3. 解决 Java 21 与 MyBatis-Plus 3.4.3 核心反射击溃报错
- **症状**：点“取消关注”时后台系统发生极其恐怖的 500 错误：`java.lang.InaccessibleObjectException: Unable to make field private final java.lang.Class java.lang.invoke.SerializedLambda.capturingClass accessible` 引发全线崩溃报错。
- **根因分析**：环境实在太潮——开发者使用的 Java 21 拥有地表最强的底层模块反射防卫装甲（Strong Encapsulation），遇到老古董框架 MyBatis-Plus 那一手不讲武德强行去解构反射方法名的操作（`LambdaQueryWrapper<Follow>()`），当场就被 JVM 主权击杀。
- **极客抢救方案**：不再头铁硬拼，极简地将所有被拦截的 Lambda 包装器降维重构为最朴实无华的常规防卡版本：`QueryWrapper<Follow>().eq("user_id", ...)` 采用纯字符串名进行指代，0成本完美、优雅地从侧面绕开了新版 JDK 的这把达摩克利斯之剑。

---

## [v2.0.0] - 2026-03-20 | 秒杀终极架构：Redis Stream 消费者组队列

### ✨ 新增功能

#### 1. Redis Stream 持久化消息队列 (`seckill.lua`)
- **解封** `seckill.lua` 脚本末尾的 `xadd` 指令，实现 Lua 层面极速资格校验并通过 Redis 底层将明细直写 `stream.orders` 队列。
- **目的**：彻底告别断电易丢失的 Java 内存阻塞队列，依靠 Redis Stream 的能力获得媲美专业 MQ 的消息持久化与防止漏读的工业级表现。

### 🔧 重构

#### 消费者组双循环老黄牛 (`VoucherOrderServiceImpl`)
- **彻底废弃** 内存杀手 `ArrayBlockingQueue<VoucherOrder>` 及其相关的存取逻辑。
- **增强型自动建群（`@PostConstruct`）**：项目启动时不仅拉起单据处理线程池，同时通过 `createGroup` 自动化地尝试在 Redis 环境中建立 `stream.orders` 队列与 `g1` 消费者处理小组。
- **双循环神级消费逻辑**：
  - **主循环（接新客）**：使用 `XREADGROUP ... >` 阻塞式（`BLOCK 2000`）等待最新订单，利用 Hutool `BeanUtil` 安全转换实体，依然加上 Aop 代理事务加锁落地 MySQL，最后强推 `XACK` 告知 Redis 安全销账。
  - **副循环（拯救孤儿单）**：如果主流程写库抛出异常，则立即降级进入内层 `handlePendingList()` 循环。凭借终极游标 `0` 无限爬取 `Pending-List` 这个异常备份表单里的停滞单据，持续重试并发送 `XACK`，构筑了**死不丢单的终极防线**。

---

## [v1.7.0] - 2026-03-20 | 秒杀性能极致优化：基于阻塞队列的异步架构

### ✨ 新增功能

#### 1. Lua 脚本原子性预判 (`src/main/resources/seckill.lua`)
- **新增** Lua 脚本，将“查询库存”和“单用户防刷单”逻辑下沉至 Redis 内存执行。
- 利用 Redis 执行指令的单线程特性，保证高并发下的绝对原子性。
- 通过返回码 `0`(成功)、`1`(库存不足)、`2`(重复下单) 实现 O(1) 级别的快速资格校验。

#### 2. 秒杀库存预热 (`VoucherServiceImpl`)
- 在新增秒杀券时（`addSeckillVoucher`），同时向 Redis 写入对应 ID 的库存余量。
- **目的**：为 Lua 脚本在后续高并发下提供初始判断依据。

### 🔧 重构

#### 异步秒杀流水线重构 (`VoucherOrderServiceImpl`)
- **废弃** 过去冗长、缓慢的同步流程（排队抢锁 -> 查 MySQL 库存 -> 查 MySQL 订单 -> 扣减并建单）。
- 拆分为**极速前台**与**老黄牛后台**两套逻辑：
  - **前台逻辑**：直接调用 Lua 脚本。若返回 `0`，立即生成不含数据库交互的轻量级订单凭证并封装至 `VoucherOrder` 对象，**秒回**前端下单成功。此举将极度占用 Tomcat 线程的耗时缩短至几毫秒。
  - **后台解耦**：引入单机版 `ArrayBlockingQueue` 兜底暂存前端产生的轻量级单据。
  - **异步消费**：依靠 Spring `@PostConstruct` 拉起专属守护线程池 `SECKILL_ORDER_EXECUTOR`。该老黄牛线程执行死循环 `take()`，平滑取出内存队列中的业务单据，重新套上 Redisson 锁后慢慢执行写库与真实库存扣减。

### ⚠️ 已知缺陷（To Do）
此版本虽能应付大流量，但内存 `BlockingQueue` 为过渡型方案，存在两大致命风险，为后续（V2.0 Redis Stream）埋下伏笔：
1. **内存泄漏 / OOM**：物理内存容量固定（当前定频 `1024*1024`），若下沉效率不及前台积压速度，仍面临暴毙风险。
2. **数据丢失**：纯内存存储，遇 Tomcat 崩溃、机房断电，所有已收号未写库订单将不可逆丢失。

## [v1.6.0] - 2026-03-19 | 引入 Redisson 替代自研分布式锁

### ✨ 新增功能

#### Redisson 客户端配置 (`RedissonConfig`)
- **新增** `com.hmdp.config.RedissonConfig` 配置类，向 Spring 容器注册 `RedissonClient` Bean。
- 通过 `config.useSingleServer()` 指向本地 Redis（`redis://127.0.0.1:6379`），与 `application.yaml` 保持一致。

### 🔧 重构

#### 优惠券秒杀加锁机制 (`VoucherOrderServiceImpl`)
- **废弃** 自研 `SimpleRedisLock`，全面切换为 **Redisson `RLock`**。
- 注入 `RedissonClient`，通过 `redissonClient.getLock("lock:order:" + userId)` 获取可重入分布式锁对象。
- 改用 `tryLock()` 无参形式加锁，底层自动启用 **WatchDog（看门狗）** 机制，无需手动指定锁超时时间。
- 保留 `try-finally` 结构确保 `lock.unlock()` 在任何情况下必定执行。

### 📖 技术说明

| 对比项 | `SimpleRedisLock`（旧） | Redisson `RLock`（新） |
|---|---|---|
| 实现方式 | 手写 Lua 脚本 + `SETNX` | Redisson 内置 Lua 脚本 |
| 可重入支持 | ❌ 不支持 | ✅ 支持（Hash 结构计数） |
| 自动续期 | ❌ 需手动指定超时 | ✅ WatchDog 每 10s 自动续期至 30s |
| 宕机自动释放 | ✅（TTL 到期） | ✅（续约停止，30s 内自动过期） |

---

## [v1.5.0] - 2026-03-19 | 分布式锁容错与原子性升级 (V2.0 & V3.0)

### ✨ 新增功能

#### 锁的防误删机制 (V2.0)
- **优化** `SimpleRedisLock` 获取锁与释放锁逻辑。
- 采用 `UUID + ThreadId` 作为锁的 Value 标识，解决多节点并发下标识碰撞问题。
- 释放锁前增加一致性校验，确保线程仅释放自己的锁，完美解决因业务阻塞导致的“锁超时误删”重大隐患。

#### 解锁原子性保证 (V3.0)
- **新增** `unlock.lua` 脚本至 `src/main/resources` 目录。
- **重构** `SimpleRedisLock` 的 `unlock()` 方法：
  - 废除本地 JVM `get` 与 `delete` 散件拼装的非原子性操作。
  - 引入 `DefaultRedisScript` 进行脚本缓存预热，规避频繁 IO。
  - 通过 `redisTemplate.execute()` 一键下发 Lua 脚本，利用 Redis 单命令执行机制，物理隔绝高并发环境下的插队操作。

---

## [v1.4.0] - 2026-03-18 | 集群环境分布式锁 (V1.0)

### ✨ 新增功能

#### 分布式锁 `SimpleRedisLock`
- **新增** `ILock` 接口，规范化分布式锁的 `tryLock` 和 `unlock` 行为。
- **新增** `SimpleRedisLock` 基础实现类 (V1.0)：
  - 利用 Redis `SETNX` (对应 Spring Data Redis 的 `setIfAbsent`) 实现并发互斥。
  - 加锁时自动设置 `timeoutSec` 过期时间，防止由于服务器宕机引发的死锁问题。

### 🔧 重构

#### 优惠券秒杀下单 (`VoucherOrderServiceImpl`)
- 废弃单机版的 `synchronized` 锁，全面接入 `SimpleRedisLock`。
- 修改 `seckillVoucher` 逻辑，在 `try-finally` 结构中保证业务异常中断也能安全 `unlock`。

---

## [v1.3.0] - 2026-03-18 | 秒杀下单并发安全处理

### ✨ 新增功能

#### 优惠券秒杀下单逻辑 (`VoucherOrderServiceImpl`)
- **新增** `seckillVoucher(Long voucherId)` 接口处理秒杀券抢购请求。
- 实现了完整的秒杀前置校验拦截逻辑：
  - 判断秒杀活动是否已开始/已结束。
  - 判断优惠券库存是否充足。
- 引入 **一人一单** 并发安全机制：
  - 基于 `synchronized(userId.toString().intern())` 实现用户维度的细粒度锁，防止同一个用户利用脚本并发抢单。
  - 利用 `AopContext.currentProxy()` 强行获取当前类的 Spring 代理对象。
  - 借代理对象调用受 `@Transactional` 保护的内层 `createVoucherOrder` 方法，完美解决“锁和事务顺序”引起的空窗期脏读问题（确保**先上锁 -> 再开事务 -> 提交事务 -> 后解锁**）。



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
