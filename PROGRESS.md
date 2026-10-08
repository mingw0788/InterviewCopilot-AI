# InterviewCopilot AI 开发进度

最后更新：2026-10-08

GitHub 发布分支：`codex/github-private-upload`（对应远程 `main`）

本地历史基线：`d95e198 docs: prevent stale native backend artifact`

本轮状态：项目可靠性与原生运行体验已完善并完成本机验证，按当前版本快照整理 GitHub 私有仓库交付。

目标仓库：[mingw0788/InterviewCopilot-AI](https://github.com/mingw0788/InterviewCopilot-AI)。仓库仅保存代码，不代表网站已在线部署。为避免上传旧文档中的本机演示令牌，早期开发历史仅保留在本地 `main`；下方历史 Commit 编号用于本地追溯，不属于远程快照历史。

## 当前状态

- P1-T01 至 P1-T12 的业务后端和前端主流程已实现。
- 本机统一使用原生 MariaDB + Python + Java + Vite；不要求启动 Docker Desktop 或安装其他容器平替。
- 界面固定文案已中文化；真实浏览器注册、登录、三题面试、报告生成与回看已验证。
- 本轮补齐历史列表 DTO、分页、中文错误提示、会话失效、草稿恢复和网络失败重试保护。
- 报告乐观锁及失败重试状态持久化问题已修复；原生新建数据库不再依赖手工 UUID 函数或关闭结构校验。
- 提供中文双击启动/停止入口、自动更新后端 JAR、服务就绪检查和原生验收脚本。
- AI 仍为确定性 mock。真实大模型接入和公网发布准备未完成，不应称为生产版。

## 已完成任务

### 项目基础阶段

| 交付项 | 状态 | Commit |
| --- | --- | --- |
| 项目工作区与服务骨架 | 已完成 | `d46ba3d chore(scaffold): initialize project workspace` |
| V1 OpenAPI 冻结契约 | 已完成 | `a854890 feat(contracts): define v1 OpenAPI contract baseline` |
| 本地开发环境 | 已完成 | `89138b5 feat(infra): add local development environment` |
| Java → Python 问题生成集成 | 已完成 | `857165a feat(integration): add Java-to-Python question generation` |
| CI 验证基线 | 已完成 | `326dee7 ci: establish project validation baseline` |

### P1 业务后端阶段

| Task ID | 交付内容 | 状态 | Commit |
| --- | --- | --- | --- |
| `P1-T01` | Flyway 核心数据库结构及幂等记录表 | 已完成 | `c203689 feat(db): add Flyway schema and idempotency records` |
| `P1-T02` | 用户注册、密码哈希及重复账号保护 | 已完成 | `06ccc59 feat(auth): implement user registration` |
| `P1-T03` | JWT 登录、鉴权过滤与当前用户访问 | 已完成 | `f5dace6 feat(auth): implement JWT login and current-user access` |
| `P1-T04` | Interview 聚合、状态机、业务约束及确定性评分 | 已完成 | `ea7a841 feat(interview): implement domain model and state machine` |
| `P1-T05` | Interview 聚合持久化、分页、乐观锁及查询优化 | 已完成 | `9a793ac feat(interview): implement aggregate persistence` |
| `P1-T06` | 所有权校验、统一错误模型及幂等基础能力 | 已完成 | `a7b1ce8 feat(business): add ownership and idempotency safeguards` |
| `P1-T07` | Interview 创建、当前用户分页列表、详情查询及创建幂等编排 | 已完成 | `e290bf8 feat(interview): add create list and detail application APIs` |
| `P1-T08` | Interview 启动、首题生成、AI 编排及启动幂等 | 已完成 | `2244f0a feat(interview): add start flow and first question generation` |
| `P1-T09` | 答案提交、AI 评估、下一题生成或 Interview 完成及提交幂等 | 已完成 | `ab81644` + `dab3519` |
| `P1-T10` | 当前题查询、取消 Interview 及取消幂等 | 已完成 | `2c4f5c7` |
| `P1-T11` | 报告生成、报告查询、AI 摘要重试及报告幂等 | 已完成 | `31f0130` |
| `P1-T12` | 前端面试完整流程、业务 API 客户端及端到端烟测 | 已完成 | `a764077` |

## 本轮完善内容

### 前端交互与错误恢复

- 列表项与面试详情采用不同 DTO，修复历史列表访问缺失的 `skills` 字段造成页面崩溃的问题；进入面试或报告前读取详情。
- 新增每页 10 条的分页、总数、空状态和刷新入口，操作中禁止重复提交及切换关键操作。
- 错误解析匹配冻结契约的平铺 `code/message`，对网络、超时、权限、状态和 AI 错误显示安全中文提示。
- 仅在明确 401 时清理受保护会话；网络故障不误删登录令牌。登录/注册后清除密码输入。
- 技能支持中英文逗号、顿号和分号；补齐基本输入检查、键盘焦点和移动端布局。
- 回答草稿按用户与题目保存到 sessionStorage，刷新后从历史“继续”恢复；提交成功后清除该题草稿。
- 同一页面中，不确定结果的写操作重试复用幂等键；请求内容改变或切换账号时不会复用旧键。
- 页面明确标识“本地模拟练习”，不将 mock 题目和评分包装为真实 AI 结果。

### 后端可靠性与数据库兼容

- 题目、评估和报告共用严格 AI HTTP 传输：超时、响应大小、JSON、结构校验及安全错误映射。
- 修复未开始/已取消面试查询和提交的状态错误，避免领域状态问题落入 500。
- 启动幂等重放在面试完成后仍返回原首题；旧答案重放返回对应题目的原答案，而非最后一题答案。
- 抢占幂等记录后的所有权失败释放新 claim；AI 报告失败保存 `FAILED_RETRYABLE` 并释放 claim，同键可再次重试。
- 报告保存显式核对领域版本，受管实体版本由 Hibernate 推进；一次生成中多次保存接收返回的新版本，防止旧数据覆盖。
- UUID 保持未交换的 `BINARY(16)`，改为可移植 SQL 和 Java 字节解析，新建 MariaDB 不要求自定义 UUID 函数。
- MariaDB 专用方言处理 JSON/LONGTEXT 元数据别名；保留 Hibernate `validate`，不放宽 MySQL 生产/CI 方言。
- 数据库 CHECK 约束测试识别 MySQL 与 MariaDB 的不同错误码，其他唯一性/外键约束仍精确校验。
- 新增 AI 错误路径、前端会话和重试、草稿、状态重放、报告并发与失败状态持久化回归。

### 原生启动与验收

- `启动项目.cmd` / `停止项目.cmd` 提供中文双击入口。
- `infra/native.ps1` 支持 Start / Stop / Restart / Status，跟踪进程创建时间和命令标记，拒绝误停端口上的无关程序。
- 检测 JAR 是否过期并自动构建，避免直接运行旧包使新增接口返回 404。
- 启动使用隐藏后台进程、项目内日志和就绪检查；跟踪 Python 实际监听进程，兼容 Windows PowerShell 5.1 中文编码及原生程序 stderr。
- `infra/verify-native.mjs` 验收实际前端代理到 Java/Python/数据库链路。
- `infra/verify-browser.mjs` 使用独立 Chrome/Edge 无界面实例验证真实页面操作。
- `infra/verify-database.ps1` 在独立临时 MariaDB 实例上运行数据库集成回归，结束后停止，不修改现有业务数据库。
- README 和 infra/README 改为中文原生运行说明，移除“面试流程尚未实现”“Docker 必需”等过时表述。

## 最新验证结果

验证日期：2026-10-08。以下为本机实测，不等同于远程 CI 或生产部署验收。

| 验证 | 结果 |
| --- | --- |
| Java 完整测试，含隔离数据库及运行中 Python mock 契约 | 125 项；失败 0、错误 0、跳过 0 |
| Python AI Service | 24 项通过 |
| 前端 Vitest | 3 个文件、12 项通过 |
| 前端 TypeScript 与 Vite 生产构建 | 通过 |
| OpenAPI lint | 两份契约校验通过 |
| OpenAPI 契约测试 | 15 项通过 |
| 原生真实 HTTP 流程 | 40 项检查通过 |
| 无界面浏览器完整交互 | 通过；未捕获运行时错误 |
| Windows PowerShell 5.1 原生启动/重启 | 验证通过，服务就绪 |

数据库回归覆盖新库迁移、结构校验、注册鉴权、聚合持久化、所有权、分页、Session/Report 乐观锁、幂等并发及报告失败可重试状态。实时 AI 契约测试调用本地 Python mock，不需要外部大模型凭据。

接口验收覆盖注册 → 登录 → 创建及幂等冲突 → 启动 → 三次回答与评估 → 完成 → 报告 PENDING/AVAILABLE → 重放 → 分页 → 取消 → 跨账号 403。浏览器验收另外覆盖中文技能输入、历史列表、草稿刷新恢复、手机宽度及退出登录。

临时数据库诊断目录、独立 `verify_` / `browser_` 测试账号保留供排查；没有删除用户原有账号或面试数据。

## 当前本地使用方法

1. 双击项目根目录 `启动项目.cmd`，等待“启动完成”。
2. 打开 <http://127.0.0.1:5173/>，注册登录后创建面试。
3. 完成回答后生成报告；历史列表支持继续和回看。
4. 使用完双击 `停止项目.cmd`，数据库数据保留。

默认数据库为隔离 MariaDB `13306`，AI `8001`，Java `8080`，前端 `5173`。`.env.native` 保存本机配置且已被 Git 忽略，不得提交真实密码或令牌。

详细的状态检查、日志、端口配置和验收命令见 [infra/README.md](infra/README.md)。原有 MySQL80 不属于本项目启动脚本的管理对象。

## 未完成项与建议下一步

1. **优先：接入真实 AI Provider。** 当前 Python 只有已验收的 mock 能力；需先由使用者确定提供商、模型、API 地址与成本预算，并通过私有配置提供凭据，再实施题目/评估/报告的真实模型适配和质量评测。未配置前保持 mock，不假装真实 AI。
2. **上线治理。** HTTPS、部署配置、数据库仅本机/内网访问、备份恢复演练、密钥轮换、登录及 AI 调用限流、监控审计、容量与成本控制。
3. **恢复边界。** 当前页面的待重试幂等键没有跨刷新持久化；跨页面恢复和异常退出的 IN_PROGRESS claim 治理需要单独设计，避免未经评审的自动重放或回收。
4. **产品增强。** 报告导出、账号找回、历史筛选、专项练习等尚不在已完成的核心流程内，按需求优先级另立任务。
5. **代码交付边界。** 私有仓库发布采用已扫描的当前版本快照，保留本地旧历史；不上传 `.env.native`、数据库、依赖缓存、构建产物或临时日志。GitHub Actions 的远程验证结果以仓库实际运行记录为准。

## 关键设计决策

- Java 是用户、面试、答案、评估、报告、状态和最终分数的唯一 System of Record。
- Python 只提供 AI 能力与结构化输出，不拥有业务数据库，不决定业务状态或最终评分。
- 前端只调用 Java；Java 通过受保护的内部契约调用 Python。
- 面试显式状态机和确定性评分位于 Java Domain；Session 与 Report 使用乐观锁。
- 面试列表先分页 ID，再批量加载聚合，避免集合抓取分页错误和 N+1。
- 所有权在 application boundary 强制执行，幂等范围为 user + operation + key；相同键不同请求返回冲突。
- API 错误响应不得暴露密码、令牌、堆栈或内部异常消息；本轮没有修改冻结端点或表结构。

## 已知限制与环境提示

- 草稿为标签页级 sessionStorage；关闭标签页、禁用存储或清理浏览器数据后不保证保留。
- JWT 仍使用浏览器 localStorage；生产版需结合 XSS 防护和会话策略评审。
- 若进程在独立幂等 claim 抢占后异常终止，可能留下 IN_PROGRESS；当前表无过期字段，未自动回收。
- 当前 MySQL JDBC 驱动读取 MariaDB 13 关键词元数据可能输出 `Unknown column 'RESERVED'` 警告；显式方言下就绪检查、结构校验和业务测试通过，数据库/驱动的生产组合仍需重新选择及验证。
- Mockito 在 Java 21 会提示动态 Agent 的未来兼容风险；Windows Node 工具链曾出现一次退出断言，单独重跑 lint/契约测试通过，升级运行时需再次回归。
- 本次本机未验收 Docker 链路；原有 Compose 与 CI 容器验证保留，不作为原生使用前置条件。
