# 项目更新日志 (Changelog)

## 🚀 Feat: 引入双层拦截器架构，重构用户登录鉴权防护 (d644c8cf507ca84b9a5785fc0d5abc79f2edd10b)
**摘要 (Summary):**
本次提交核心解决了系统无登录鉴权的问题，通过引入基于 ThreadLocal 和双层 Spring MVC Interceptor 的架构，实现了高安全等级的 Token 校验与无感续期，同时修复了前端的用户协议 Bug。

### 🛠️ 核心变更点 (Key Changes)

**1. [新增] 全局 Token 无感续期 (RefreshTokenInterceptor)**
- **文件**: `src/main/java/com/hmdp/utils/RefreshTokenInterceptor.java`
- **说明**: 第一层拦截器 (Order=0)，拦截全局 `/**` 路由。无论用户访问何种页面，只要携带了合法的 Token 即可触发 `ThreadLocal` 用户信息注入，并重置 Redis 中的 Token 过期时间至 30 分钟，确保活跃用户永不掉线。最后通过 `afterCompletion` 强制清除 `ThreadLocal` 防止内存泄漏与线程复用导致的串号风险。

**2. [新增] 核心业务拦截管控 (LoginInterceptor)**
- **文件**: `src/main/java/com/hmdp/utils/LoginInterceptor.java`
- **说明**: 第二层拦截器 (Order=1)，专门负责业务鉴权。采用极简防御策略，仅依赖判定 `ThreadLocal` 是否存在用户信息，若无则拦截并抛出 `401 Unauthorized`。同时隔离了白名单接口（如注册登录、前端静态页等），避免循环拦截。

**3. [新增] MVC 拦截器注册中心 (MvcConfig)**
- **文件**: `src/main/java/com/hmdp/config/MvcConfig.java`
- **说明**: 构建 Spring MVC 的底层配置类，通过 `registry.addInterceptor()` 机制将上述两个拦截器组合并挂载到系统请求链路中，使用 `.order()` 严格锁定执行优先级。

**4. [修改] 用户控制器入口 (UserController)**
- **文件**: `src/main/java/com/hmdp/controller/UserController.java`
- **说明**: 重构登录与获取当前用户的 API。将 `login` 逻辑下发到 Service 层，并在 `me()` 接口剥离原有 mock 逻辑，接入真实的 `UserHolder.getUser()` 提取真实用户。

**5. [修复] 修复 Nginx 静态文件前端 Bug**
- **文件**: 外置 Nginx 系统的 `login.html`
- **说明**: 修复了长期遗留的前端登录协议勾选框被遮挡/无法生效的 Bug。原因为隐藏的 radio 标签缺失 `id="readed"`，导致 UI 视图层面的 Label 点击事件无法关联实际的表单控件，现已通过脚本注入修复。

### 💡 架构亮点 (Architecture Highlights)
- **避免单点雪崩**：通过“拦截器拆分”分离了**有效性延期**与**登录拦截**的职能，保证了游客在浏览无需登录的界面时也能维持 Token 的活跃度。
- **并发安全保证**：全面弃用 Session，转向 `ThreadLocal` 结合并发线程池的安全回收机制 `removeUser` 管理每一次请求隔离。
