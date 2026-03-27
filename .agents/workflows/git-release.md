---
description: 更新 CHANGELOG.md 并将所有改动推送到 GitHub
---

# Git Release Workflow — 黑马点评项目发版

本工作流用于在每次功能开发完毕后，规范地更新版本日志并推送到远程仓库。

---

## 使用前确认（人工完成）

在启动本工作流之前，请确认以下两件事已经完成：
- [ ] 本次新增或修改的后端/前端功能代码已全部保存
- [ ] 你已确定本次要发布的版本号（格式：`v主版本.次版本.补丁号`，如 `v2.5.0`）

---

## 工作流步骤

### 第 1 步：收集本次变更信息
询问用户以下信息（如果用户没有主动提供）：
- 本次发版的**版本号**（例如 `v2.5.0`）
- 本次发版的**主题描述**（一句话，例如"评论模块全链路实现"）
- 本次新增、修改、修复了哪些功能（逐条列举）

### 第 2 步：更新 CHANGELOG.md
在 `CHANGELOG.md` 文件顶部插入新版本条目，严格遵守以下格式规则：

```markdown
## [vX.Y.Z] - YYYY-MM-DD | 版本主题

### ✨ 新增功能

#### 1. 功能名称 (`相关文件`)
- **新增** 具体说明...
- **设计细节**：...
- **修改文件**：`FileA.java`、`FileB.java`

---
```

**格式强制要求（必须遵守）：**
- 日期格式必须为 `YYYY-MM-DD`（今日日期）
- 每个功能点必须标注 **修改文件** 列表
- 新版本条目与上一个版本条目之间必须有 `---` 水平分隔线
- `v2.3.0` 的 `- 当前` 标记无需修改（它是历史记录的一部分）

### 第 3 步：暂存所有变更
// turbo
运行以下命令查看本次变更文件列表：
```powershell
git add -A; git status
```

### 第 4 步：提交 Commit
运行以下命令提交，**commit message 必须遵守 Conventional Commits 规范**：
```powershell
git commit -m "feat(scope): 简短描述

- feat(module): 具体改动 1
- feat(module): 具体改动 2
- docs: update CHANGELOG.md with vX.Y.Z release notes"
```

**Conventional Commits 前缀速查表：**
| 前缀 | 含义 |
|---|---|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `refactor` | 重构（无新功能无 Bug 修复） |
| `docs` | 文档更新 |
| `chore` | 构建/配置类修改 |

### 第 5 步：推送到远程仓库
```powershell
git push origin master
```

### 第 6 步：验证推送结果
推送完成后，输出以下验证信息给用户：
- 推送的 commit hash（从 git 输出中提取）
- 本次共修改了多少文件、新增了多少行代码
- 提示用户去 GitHub 对应仓库地址确认最新提交

---

## 注意事项

- 如果 `git push` 返回 `rejected`（被拒绝），优先尝试 `git pull --rebase` 后再重推，**不要强制推送（`--force`）**
- 如果存在未追踪的敏感文件（如含密码的 `application.yaml`），请提醒用户检查 `.gitignore` 规则，**不要提交任何密码或密钥**
