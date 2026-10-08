# InterviewCopilot AI

中文模拟面试练习项目。已实现注册登录、创建与恢复面试、逐题作答与评估、历史记录、取消面试以及报告生成与查询。

当前使用本地原生运行方案，不需要 Docker Desktop 或其他容器软件。AI 默认为确定性 mock：可以体验完整流程，但题目、评分和建议不是来自真实大模型，不能用来判断真实求职能力。

## GitHub 公开仓库

仓库地址：[mingw0788/InterviewCopilot-AI](https://github.com/mingw0788/InterviewCopilot-AI)，当前为公开仓库，无需仓库授权即可查看源码。

发布采用当前版本快照，不包含含有本机演示令牌的早期提交；完整旧开发历史仍保存在本地 `main`。本机发布分支为 `codex/github-private-upload`，对应远程 `main`。私有配置、数据库数据、依赖和构建产物均不上传。

这是代码仓库，不是在线网站地址。换电脑仍需按下文安装依赖和配置本地服务。

## 在这台电脑上使用

1. 双击项目根目录的 `启动项目.cmd`，等待出现“启动完成”。
2. 浏览器打开 <http://127.0.0.1:5173/>。
3. 注册并登录，填写职位、技能、难度和题数，点击“创建并开始面试”。
4. 提交每题回答，完成后点击“生成报告”；历史记录中可以继续面试或回看报告。
5. 使用完双击 `停止项目.cmd`。默认只停止应用，保留数据库及已有面试数据。

启动脚本会检查服务、识别旧版后端 JAR 并在源代码更新后重新构建，避免使用旧包导致新接口返回 404。不要在启动窗口尚未显示“启动完成”时操作页面。

当前端口：前端 `5173`、Java `8080`、AI `8001`、隔离 MariaDB `13306`。不占用或修改已有 MySQL80 实例。

## 配置与依赖

这台电脑的依赖与本地配置已经准备好。换电脑时需要 Java 21、Maven 3.6.3+、Python 3.11+、Node.js 22.12+ / npm 10+，以及可用的本地 MariaDB。

复制 `infra/native.env.example` 到项目根目录 `.env.native`，填写数据库账号密码、内部服务令牌、JWT 密钥及已有 MariaDB 配置路径。JWT 密钥至少 32 个字符。启动脚本不会安装或初始化数据库，也不会自动安装依赖。

`.env.native` 已被 Git 忽略；不要上传、截图分享或将真实密钥填写到模板中。数据库密码必须与现有实例一致，Java 与 Python 必须使用同一个内部服务令牌。

首次安装项目依赖：

```powershell
cd frontend
npm ci
cd ../docs/openapi
npm ci
cd ../../ai-service
py -3.11 -m venv .venv
.venv/Scripts/python -m pip install -e ".[test]"
```

完成配置后回到根目录，双击启动脚本即可。详细说明、状态检查和日志位置见 [原生运行说明](infra/README.md)。

## 验证

在项目根目录运行以下原生验收，不需要 Docker：

```powershell
node infra/verify-native.mjs
node infra/verify-browser.mjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File infra/verify-database.ps1
```

前两项需要项目已启动；浏览器验收复用已安装的 Chrome / Edge，使用独立无界面浏览器，不影响你打开的标签页。数据库验收复用本机 MariaDB 程序，创建并停止独立临时实例，不修改现有数据库。验收数据保留在独立测试账号下。

模块验证：

```powershell
cd frontend
npm test
npm run build
cd ../ai-service
.venv/Scripts/python -m pytest
cd ../docs/openapi
npm test
cd ../../business-service
mvn test
```

默认 `mvn test` 会跳过需要数据库或运行中的 Python 服务的集成测试；完整原生数据库测试及 Java → Python 契约测试方法见 [原生运行说明](infra/README.md)。

## 项目结构与边界

- `frontend`：Vue 3 / TypeScript 中文界面，只访问 Java 业务 API。
- `business-service`：Java 21 / Spring Boot，负责用户、面试状态、数据持久化、幂等和确定性最终评分。
- `ai-service`：Python / FastAPI，提供题目、维度评估和报告摘要；不访问业务数据库。
- `docs`：冻结 OpenAPI 契约、架构和开发计划。
- `infra`：原生启动、接口验收、浏览器验收及隔离数据库回归脚本。

开发进展、验证结果和未完成项见 [PROGRESS.md](PROGRESS.md)。现有 Docker 配置保留作可选开发/CI 方案，本机使用无需打开 Docker Desktop。

## 当前限制

- 真实大模型接入、提示词质量与成本评估尚未完成。
- 回答草稿仅保存在当前浏览器标签页的 sessionStorage；关闭标签页或禁用存储时不保证保留。
- 网络失败后的写操作重试会在当前页面复用幂等键；刷新页面后不保留这些待重试键。遇到不确定是否提交成功的情况，先刷新历史记录确认状态。
- 当前为本地开发运行方式；HTTPS、备份恢复、限流与审计等上线治理尚未验收。
