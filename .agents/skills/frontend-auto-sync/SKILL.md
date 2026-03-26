---
name: frontend-auto-sync
description: Generates Vue2 frontend pages and interaction logic when a new or modified backend API is detected in the hm-dianping project but the corresponding frontend UI, Axios call, or page route is missing. Use this skill when the user asks to sync the frontend with a new backend feature, add a UI for a new API endpoint, or when a Controller method has been added or changed without a matching frontend page.
---

# Frontend Auto-Sync Skill for 黑马点评 (hm-dianping)

This skill automates frontend code generation for the hm-dianping project when a backend API change has no corresponding frontend implementation.

## Project Context

| Layer | Technology | Location |
|-------|-----------|----------|
| Backend | Spring Boot + MyBatis-Plus | `hm-dianping/src/main/java/com/hmdp/` |
| Frontend | Vue 2 (CDN) + Axios | Nginx deployment folder, typically `<nginx-root>/html/hmdp/`. Ask the user for the exact path if unknown, as it varies per machine (e.g. Windows: `D:\nginx-1.18.0\html\hmdp\`, Mac/Linux: `/usr/local/nginx/html/hmdp/`). |
| Proxy | Nginx reverse proxy | All `/api/` requests → `127.0.0.1:8081` |

**Critical constraint**: The frontend uses **Vue 2 Options API** (`new Vue({ data, methods, mounted })`), NOT Vue 3. Never generate `createApp`, `setup()`, or Composition API syntax.

---

## When to Use This Skill

- User says "帮我生成前端代码" / "前端没有对应页面" / "前端联动" / "sync frontend"
- A new `@GetMapping`, `@PostMapping`, `@PutMapping`, or `@DeleteMapping` was added to a Controller but no matching HTML/JS exists in the Nginx html folder
- The user explicitly asks to generate frontend interaction for a specific backend feature

---

## How to Use It

### Step 1 — Analyze the Backend Change (后端特征提取)

Read the relevant Controller and Service files. Extract the following information:

```
API Path       : e.g. DELETE /blog/{id}
HTTP Method    : GET / POST / PUT / DELETE
URL Parameters : @PathVariable, @RequestParam
Request Body   : @RequestBody DTO class (read its fields)
Response Type  : Result.ok(data) / Result.fail(msg)
Auth Required  : Check if path is excluded in MvcConfig interceptor
```

**Files to inspect:**
- `controller/` — for route mapping and parameter types
- `dto/` — for request body field definitions
- `utils/MvcConfig.java` — to confirm if the endpoint requires login token

---

### Step 2 — Audit the Frontend (前端缺失确认)

Look inside `D:\computer2\nginx-1.18.0\html\hmdp\` for:

1. Does an HTML page exist that logically should contain this feature? (e.g. `blog-detail.html` for blog operations)
2. Inside that HTML, does a Vue `methods` block already contain a corresponding function?
3. Is there a missing button, link, or form that the user would interact with?

If gaps are found, document them before generating code.

---

### Step 3 — Generate Frontend Code (生成前端代码)

Follow these mandatory conventions for the hm-dianping Vue 2 frontend:

#### 3a. Axios Request Pattern
```javascript
// Always use relative path. Nginx proxies /api/ → :8081
// Token is attached globally; do not manually add Authorization header here.
axios.METHOD('/api/ENDPOINT' + parameter)
  .then((res) => {
    const data = res && res.data;
    if (data && data.success === false) {
      this.$message.error(data.errorMsg || '操作失败');
      return;
    }
    // success handling
    this.$message.success('操作成功！');
  })
  .catch((err) => {
    let msg = '请求失败';
    if (err && err.response && err.response.data) {
      msg = err.response.data.errorMsg || msg;
    }
    this.$message.error(msg);
  });
```

#### 3b. Authorization Guard (权限校验)
For operations that should only be visible to the resource owner, use `v-if` on the button:
```html
<!-- Only show to the author -->
<div v-if="user && user.id === blog.userId" class="action-btn">
  <span @click="deleteMethod">删除</span>
  <span @click="editMethod">编辑</span>
</div>
```

#### 3c. POST/PUT with Request Body
```javascript
axios.put('/api/blog', {
  id: this.blog.id,
  title: this.form.title,
  content: this.form.content,
  images: this.form.images
})
```

#### 3d. Navigation After Action
```javascript
// After destructive actions (delete), redirect with a delay
setTimeout(() => { location.href = '/info.html'; }, 1000);

// After non-destructive saves, go back
history.back();
```

#### 3e. New Page Template (if a whole new HTML page is needed)
Use the existing pages as a reference. Every page:
- Links `vue.js`, `axios.min.js`, and the shared `common.js` from the same relative path
- Contains a single `<div id="app">` root element
- Initializes with `new Vue({ el: '#app', data() { return {...} }, methods: {...}, mounted() {...} })`

---

### Step 4 — Output a Feature Explanation (必须输出功能说明书)

After generating all code, you **MUST** produce a structured explanation in Chinese containing:

```
## 新增前端页面/功能说明书

### 1. 功能概述
[该页面/功能在整个黑马点评项目中扮演的角色]

### 2. 用户交互流程
[用编号的步骤描述：用户做了什么 → 前端发生什么 → Nginx转发 → 后端处理 → 前端展示结果]

### 3. 后端接口对接详情
| 属性 | 值 |
|------|-----|
| 接口路径 | |
| 请求方法 | |
| 传递参数 | |
| 成功返回 | |
| 失败情况 | |

### 4. 权限安全说明
[说明前端的 v-if 权限校验与后端 UserHolder 权限校验如何构成双层防护]

### 5. 修改的文件清单
[列出新增或修改了哪些前端 .html 文件的哪些部分]
```

---

## Decision Tree

```
New backend feature detected
        │
        ▼
Does a relevant HTML page already exist?
  ├─ YES → Modify existing page (add button/method only)
  └─ NO  → Create a new .html page from template
        │
        ▼
Does the endpoint mutate data (POST/PUT/DELETE)?
  ├─ YES → Add confirm dialog + success redirect
  └─ NO  → Add result display (list/detail render)
        │
        ▼
Is the operation owner-restricted?
  ├─ YES → Wrap button in v-if="user.id === resource.userId"
  └─ NO  → Show button to all logged-in users
        │
        ▼
Always output the 功能说明书 (Step 4)
```
