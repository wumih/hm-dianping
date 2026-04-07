# 项目更新日志 (Changelog)

---

## [v2.11.0] - 2026-04-08 | 系统巅峰架构性能压测与容量摸底评估

### ✨ 工程化实践与性能摸底

#### 1. 高并发工程自动化压测环境构筑
- **环境隔离建设**：利用 Spring Boot Profile 机制创建了专用于极限性能压测的 `application-test.yaml` 隔离环境，彻底杜绝测试污染。
- **并发弹药库生成脚本**：在 `HmDianPingApplicationTests` 中新增了硬核的压测物料生成机器 `testMultiLogin` 方法。通过自动化脚本向 DB 与 Redis 一次性灌入 1000 名虚假“群演用户”并导出授权 Token 到本地 `tokens.txt`，为突破单发 Token 防刷拦截从而触达深水区锁机制，提供了工业级的千人千面模拟流量前置库。

#### 2. 量化压测数据指标录入（JMeter 3000 并发级）
- **高并发读场景 (缓存预热防击穿)**：成功使用 JMeter 完成对单节点应用大阅兵。验证了裸奔 MySQL 的虚高吞吐假象，利用控制变量法比对了“缓存冷启动（由于自旋互斥锁阵列导致 RT 拉抬至 297ms）”与“缓存全数预热后（1ms 直接读穿内存，QPS 暴涨至 2437/sec）”的前后天壤之别，印证了系统化预热的战略必要性。
- **高频写防超卖场景 (异步秒杀抗洪)**：利用生成的 千人独立 Token 在 1 秒内集中爆破 100 张优惠券的写库接口。利用 Redis Lua 结合异步消息组件处理洪峰，平均下单响应时间死死拿捏在 64ms 极限段位，QPS 高达 932+/sec 且全程无 JVM 内存溢出、死锁。最重要的是：在极高水压冲刷下，数据库库存精确到 0！完美守住了 0 异常与 0 超卖系统绝对一致性防线。

### 🔧 性能调优

#### 中间件参数深度破局调优 (`application.yaml`)
- **根治并发假死病根**：压测过程中通过观察排队异象与请求挂死，精准捕捉到隐藏极深的 Redis 客户端 Lettuce 连接池的默认瓶颈死锁（其默认最大连接池容量 `max-active` 形如鸡肋、仅有可怜的 8 个！）。
- **极客破局**：在压测特供配置中，重拳出击将池子 `max-active` 疯狂激增拓展至 200 个，同时辅以 `max-wait: 1000ms` 的熔断级排队超时生命线。彻底打通了 Tomcat 多线程并发争抢底层缓存资源时的连接高速公路，让 CPU 的吞吐与压榨效率达到巅峰状态。

---

## [v2.10.0] - 2026-04-02 | 前端视觉重构与质感升级体验版

### ✨ 新增功能与 UI 重构

#### 1. 全局 UI “莫兰迪绿” 主题重塑 (`main.css`)
- **新增** 摒弃默认橙红色基调，引入高级文艺的“莫兰迪主题（#609981）”作为主品牌色。
- **设计细节**：采用纯 CSS 拦截覆写技巧，创造性地使用 `hue-rotate` 滤镜技术将无法改色的前端硬编码 PNG 按钮平滑变色；并运用属性选择器拦截核心 SVG 点赞图标色温，实现全站零死角的品牌色深度渗透。
- **修改文件**：`main.css`

#### 2. “拟物悬浮大圆角”现代化排版体系 (`main.css`)
- **新增** 全局彻底推翻原本的扁平直角设计，更迭为极度现代的悬浮质感架构。
- **设计细节**：各大功能板块与标签统一使用胶囊修角与 16px 无边线设计，施以浅绿弥散阴影 `0 4px 16px rgba(96, 153, 129, 0.08)`。并在外围 Body 底层铺设高级的高级拟物宣纸渐变（`radial-gradient` 网格噪点），实现页面空灵清新的顶级产品视觉表现。
- **修改文件**：`main.css`

#### 3. “一键防流失”沉浸导航胶囊 (`shop-list.html`、`shop-detail.html`、`blog-detail.html`)
- **新增** 于所有易流失用户的深层交互页面底部加入 “返回全局首页” 的全局悬浮导航球（FAB）。
- **设计细节**：巧妙置于防误触的安全黄金三角区域（`bottom: 80px`），采用 95% 白色磨砂感遮挡防止被滚动图层冲叠。点击反馈精准挂载 `location.replace`，清空深潜历史栈，根治了深层退出的心智痛点。
- **修改文件**：`shop-list.html`、`shop-detail.html`、`blog-detail.html`

---

## [v2.9.0] - 2026-04-02 | 探店笔记全链路评论系统与前端 UI 深度改造

### ✨ 新增功能

#### 探店笔记多级评论架构 (`BlogComments`)
- **新增** `tb_blog_comments` 表及对应实体类、Mapper，采用 `parent_id` 配合 `answer_id` 进行树状回复折叠及楼中楼回复防迷路的高阶数据模型设计。
- **服务端实现**：新增 `saveBlogComment` 采用极简参数接收所有级评论；新增 `queryBlogComments` 提供完整的分页拉取能力，在后端拼装楼主红标、精确识别被回复人并组装至 `BlogCommentsVO` 下发给前端，完美隔离数据库单表结构和业务展示逻辑。
- **前端深度集成**：彻底铲除 `blog-detail.html` 的死板假数据，利用 Vue.js 动态渲染出带有真实头像及互动机制的评论列表，并使用响应式弹框组件 `el-dialog` 实现沉浸式的独立评论提交视图。
- **修改文件**：`BlogComments.java`、`BlogCommentsVO.java`、`BlogCommentsController.java`、`BlogCommentsMapper.java`、`IBlogCommentsService.java`、`BlogCommentsServiceImpl.java`、`tb_blog_comments.sql`、`blog-detail.html`


## [v2.8.0] - 2026-04-01 | 查询我的订单闭环与商铺全量分页修复

### ✨ 新增功能

#### 个人中心：“查询我的订单”全链路联表组装实现 (`VoucherOrderServiceImpl`)
- **接口打通**：新增 `GET /voucher-order/list` 接口，打通前端个人中心“我的订单”与后端高并发订单存储库的数据闭环，为用户提供秒杀战果的回显视图。
- **高级联表聚合组装**：服务层一改仅返回冷冰冰的、毫无意义的原始订单 `VoucherOrder` 基础对象的做法。在服务内部实现了一次极为优雅的高级内存联查重写：遍历当前页查出的所有单据，拿着 `voucherId` 去反向探点 `tb_voucher` 实体表，将“代金券标题、副标题、原价面值、实付金额”等饱满的图文与面值信息精准拼接并封装。
- **前端防崩架构处理 (`VoucherOrderVO`)**：专门针对此功能新增数据传输大将 `VoucherOrderVO`。在序列化组装数据时，警觉地将雪花算法生成的长达 19 位的极长 Long 类型 `orderId` **强行转化为 String 类型**后再向前端投放。此极其老辣的防崩盘操作，从架构源头彻底斩断了前端 JavaScript 在反序列化超过 `Number.MAX_SAFE_INTEGER` 的超大数值时，因算力丢失导致订单号末尾被野蛮清零为 `0` 的史诗级前端灾难。
- **修改文件**：`VoucherOrderController.java`、`IVoucherOrderService.java`、`VoucherOrderServiceImpl.java`、`VoucherOrderVO.java`

### 🐛 重点修复 (Bug Fixes) - 着重强调

#### 彻底修复前端“美食频道”商铺数据莫名截断、无法向下级联加载的怪异故障 (`ShopServiceImpl`)
- **致命症状**：测试验收时发现，尽管 MySQL 的 `tb_shop` 库中赫然躺着 9 条对应的同类商铺数据，但在屏幕上永远只见前 5 条。即便拼命向下滑动手机屏幕尝试触发经典的 H5“触底加载（无限滚动分批加载）”机制，依然毫无波澜，永远等不到那第 6 家店的出现。
- **根因深度解离**：这不是一个普普通通的前后端分页 BUG！而是因为前端在首次发包时，顺手带上了用户的模拟测试环境坐标 `(x,y)`，引发了后台极其隐秘的高级别分支——**Redis GEO 空间检索定位引擎** 的全功率苏醒！由于在该检索引擎的最底层核心管线里，被硬核挂载了一把长达 5 公里的锁 (`new Distance(5000)`)，导致库里剩余那 4 家稍微远了一点（超出了这个绝对结界物理限制）的商铺，在抵达前端的半路，被 Redis 利用暴烈的内存空间计算强行“人道抹除”了！
- **降维极客修复法**：在 `ShopServiceImpl` 的 `queryShopByType` 方法进入核心分支门线的前一厘米，主动拔枪斩断了那两根万恶的经纬度神经（强行阻断信号：`x=null; y=null;`）。此等破釜沉舟之举，瞬间屏蔽了上层苛刻的 Redis 空间拦截器大阵，用魔法打败了魔法，迫使用户查询流量在一脸懵逼中，全部重新从正门汇入了那条最原始、最纯正、没有任何偏见的 `MySQL query().page()` 全量基础引擎大水管中。这区区 2 行暴力的阻断代码立竿见影，让那 4 家含冤被拒的失联店铺重新借由瀑布流顺滑喷涌而出，完美抹平了前端页面那令人吐血的假死断层问题。
- **修改文件**：`ShopServiceImpl.java`

---

## [v2.7.0] - 2026-03-29 | 商铺分类列表 Redis 缓存

### ✨ 新增功能

#### 商铺分类列表缓存 (`ShopTypeServiceImpl` / `queryTypeList()`)
- **新增** `IShopTypeService.queryTypeList()` 方法，将缓存逻辑下沉至 Service 层，Controller 仅做委托调用，符合职责单一原则。
- **实现标准旁路缓存（Cache-Aside）三步走**：
  1. ① 先查 Redis（Key: `cache:shop:type:list`）→ 命中则反序列化后直接返回（约 <1ms）
  2. ② 未命中则查 MySQL（`query().orderByAsc("sort").list()`）
  3. ③ 查到数据后序列化写入 Redis（TTL: 30 分钟），再返回结果
- **数据结构选型**：`String`（JSON 整体序列化）。分类列表全局唯一，无需拼接 ID，Key 本身即完整唯一标识，简洁高效。
- **避免缓存穿透**：若数据库返回空列表则直接报错，不写入空缓存（分类数据属于静态参考数据，空列表代表异常而非正常业务逻辑）。
- **修改文件**：`RedisConstants.java`（新增常量）、`IShopTypeService.java`、`ShopTypeServiceImpl.java`、`ShopTypeController.java`

---

## [v2.6.0] - 2026-03-29 | 用户个人资料修改全链路实现

### ✨ 新增功能

#### 1. 修改用户基础资料接口 (`PUT /user/me`)
- **新增** `PUT /user/me` 接口，支持修改当前登录用户的**昵称（nickName）**和**头像（icon）**。
- **安全设计**：无需前端传 `userId`，服务层通过 `UserHolder.getUser().getId()` 从 `ThreadLocal` 自动取用，防止越权修改他人资料。
- **字段白名单**：仅允许更新 `nickName` 和 `icon` 两个字段，其他字段完全忽略，规避了恶意字段注入。
- **双写同步**：MySQL 更新成功后，立即通过 `stringRedisTemplate.opsForHash().put()` 同步更新 Redis Hash（Key: `login:token:{token}`），保证 `GET /user/me` 接口不存在脏缓存，立即返回最新数据。
- **修改文件**：`UserController.java`、`IUserService.java`、`UserServiceImpl.java`

#### 2. 修改用户详细资料接口 (`PUT /user/info`)
- **新增** `PUT /user/info` 接口，支持修改当前登录用户的**城市、个人介绍、性别、生日**等 `tb_user_info` 表字段。
- **SaveOrUpdate 兼容设计**：服务层使用 `userInfoService.saveOrUpdate(userInfo)` 执行 UPSERT 操作，完美兼容新注册用户无 `user_info` 记录和老用户更新两种场景，无需预先检查记录是否存在。
- **主键自动注入**：服务层内部从 `ThreadLocal` 取出 `userId` 并通过 `userInfo.setUserId(userId)` 注入，彻底杜绝客户端伪造 `userId` 的越权攻击面。
- **修改文件**：`UserController.java`、`IUserService.java`、`UserServiceImpl.java`

---

## [v2.5.0] - 2026-03-28 | 密码登录全链路实现与演示体验优化

### ✨ 新增功能

#### 1. 密码登录分支 (`UserServiceImpl` / `login()`)
- **重构** `login()` 方法，在原有验证码登录逻辑基础上，新增密码登录分支，两种方式共存互不影响。
- **智能路由判断**：前端只需传 `phone` + `password`（不传 `code`）即可触发密码登录分支；传 `code` 则走原验证码逻辑。
- **密码登录三层防护**：
  1. 用户不存在 → 返回"该手机号尚未注册，请先通过验证码登录完成注册"
  2. 用户未设置密码 → 返回"您尚未设置密码，请使用验证码登录后再设置密码"
  3. 密码比对失败（`PasswordEncoder.matches()`）→ 返回"密码错误"
- **修改文件**：`UserServiceImpl.java`

#### 2. 设置/修改密码接口 (`POST /user/password`)
- **新增** `POST /user/password?phone=xxx&newPassword=xxx` 接口。
- **安全防护**：接口受 `LoginInterceptor` 拦截保护，未登录用户直接返回 401，无法调用，杜绝匿名越权修改他人密码。
- **服务层逻辑** (`setPassword()`)：手机号格式校验 → 密码长度≥6位校验 → 用户存在性校验 → `PasswordEncoder.encode()` 加盐加密 → MyBatis-Plus 链式 `update()` 写库。
- **修改文件**：`IUserService.java`、`UserServiceImpl.java`、`UserController.java`

### 🔧 优化

#### 演示体验优化：验证码透传 (`sendCode()`)
- **场景**：本地开发/演示环境中，真实短信 SDK 未接入，验证码仅打印在 IDEA 日志面板，面试官无法看到。
- **改动**：将 `sendCode` 响应从 `Result.ok()` 改为 `Result.ok(code)`，在 HTTP 响应的 `data` 字段中附带验证码明文（同时保留原有日志输出）。
- **配套前端改动**：`login.html` 的 `sendCode()` 方法接住响应中的 `data`，自动填入验证码输入框并弹出提示。
- **设计说明**：这是标准的"演示/生产模式差异化"实践，接入真实短信 SDK 时仅需改回 `Result.ok()` 一行即可无缝切换。
- **修改文件**：`UserServiceImpl.java`（后端）、`login.html`（前端 Nginx）

---

## [v2.4.0] - 2026-03-27 | 博客 CRUD 完整闭环与 Agent Skill 工程化实践

### ✨ 新增功能

#### 1. 用户退出登录 (`UserController` / `UserServiceImpl`)
- **新增** `POST /user/logout` 接口，完成双重注销：
  - 从请求头 `Authorization` 中提取 Token，调用 `stringRedisTemplate.delete()` 销毁 Redis 中的登录凭证（Key: `login:token:xxx`）。
  - 调用 `UserHolder.removeUser()` 清除当前线程的 `ThreadLocal` 绑定，防止线程复用时的用户信息"幽灵残留"。
- **安全设计**：Token 为空时直接返回成功，防止无 Token 请求触发异常。
- **修改文件**：`UserController.java`、`IUserService.java`、`UserServiceImpl.java`

#### 2. 博客修改功能（防越权编辑）(`BlogController` / `BlogServiceImpl`)
- **新增** `PUT /blog` 接口，接收 `@RequestBody Blog` 对象（仅需传 `id`、`title`、`content`、`images`）。
- **双层权限保障**（前后端双锁）：
  1. 查询数据库旧博客，确认博客存在性。
  2. 通过 `UserHolder.getUser().getId()` 对比操作人与博客 `userId`，不匹配则 `Result.fail("您没有权限")`。
- 利用 MyBatis-Plus `updateById` 的**动态 SQL** 特性，只更新前端传入的非 null 字段，不误覆盖未传字段。
- **修改文件**：`BlogController.java`、`IBlogService.java`、`BlogServiceImpl.java`

#### 3. 博客删除功能（含 Redis 全链路缓存清理）(`BlogController` / `BlogServiceImpl`)
- **新增** `DELETE /blog/{id}` 接口，完成 MySQL + Redis 双重清理闭环：
  - **第一层**：`removeById(id)` 删除 `tb_blog` 数据库记录。
  - **第二层**：`stringRedisTemplate.delete(BLOG_LIKED_KEY + id)` 删除该博客的 Redis 点赞 ZSet 集合，防止脏数据残留。
  - **第三层**：查询博主所有粉丝，遍历执行 `opsForZSet().remove(FEED_KEY + fanId, id)` 将该博客的 ID 从每位粉丝的 Feed 流收件箱中抹除，保障粉丝动态列表的完整性。
- **修改文件**：`BlogController.java`、`IBlogService.java`、`BlogServiceImpl.java`

#### 4. 前端联动 Agent Skill 技能包 (`frontend-auto-sync`)
- **新增** `.agents/skills/frontend-auto-sync/SKILL.md`，为黑马点评项目注入工作区级别的 AI 指令手册。
- **核心能力**：当 AI 检测到后端新增或修改了 API，但前端缺少对应 UI 时，自动触发该 Skill，按规范生成 Vue 2 前端页面与 Axios 交互代码。
- **关键设计约束**（固化进 Skill，避免 AI 犯错）：
  - 强制锁定 **Vue 2 Options API** 语法，杜绝生成 Vue 3 不兼容代码。
  - 内置前端路径识别策略（基于 Nginx `<nginx-root>/html/hmdp/`，可跨平台）。
  - 内置决策树（是否需要新建页面 / 是否需要权限 `v-if` / 是否需要确认弹窗）。
  - **强制要求**：每次生成代码后必须输出《新增前端页面功能说明书》，包含用户交互流程、接口对接详情与权限安全说明。

---

## [v2.3.0] - 当前 | 面试级演进：Redis 主从架构与高可用哨兵 (Sentinel) 集群部署

### ✨ 基础设施演进 (Infrastructure as Code)

#### 1. 高可用架构搭建与验证 (Master-Slave & Sentinel)
- **架构拓扑**：在 `WSL` 环境中手工剥离并部署了**一主二从**的 Redis 实例（端口 7001-7003），规避单点故障。
- **读写分离测试**：验证了从节点的默认 `readonly` 配置，保障数据流向的单纯性与一致性。
- **哨兵故障转移验证 (Chaos Testing)**：配置三节点哨兵集群（端口 27001-27003）。通过暴力手段 (`kill -9`) 模拟主节点断电暴毙宕机，从底层日志完整观察并验证了哨兵的 TILT 模式、**主观下线 (`sdown`)**、**客观下线 (`odown`)**、投票领头人选举 (`vote-for-leader`) 以及最终强制提拔从库的**故障转移 (`switch-master`)** 全过程，最终达成生产级的高可用保障。
- **文档沉淀**：相关配置文件与验证日记已归档至项目 `deploy/redis-sentinel/` 目录中，随时可复现。

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
