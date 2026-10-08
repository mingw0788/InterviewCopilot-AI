# 本地原生运行说明

本项目在当前 Windows 电脑上使用已有隔离 MariaDB + Python + Java + Node.js，不依赖 Docker、Podman 或 Rancher。

## 启动与停止

最简单的方式：项目根目录双击 `启动项目.cmd`，看到“启动完成”后打开 <http://127.0.0.1:5173/>。停止时双击 `停止项目.cmd`，数据库和面试数据默认保留。

也可以在项目根目录执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File infra/native.ps1 -Action Start
powershell.exe -NoProfile -ExecutionPolicy Bypass -File infra/native.ps1 -Action Status
powershell.exe -NoProfile -ExecutionPolicy Bypass -File infra/native.ps1 -Action Restart
powershell.exe -NoProfile -ExecutionPolicy Bypass -File infra/native.ps1 -Action Stop
```

`Status` 只说明端口是否监听，不代表业务完全就绪。实际健康状态使用下方健康端点或 `verify-native.mjs` 验证。

需要同时停止脚本识别的隔离数据库时，显式增加 `-StopDatabase`；这不会删除数据。平时无需使用此选项。

脚本记录进程 ID、创建时间和命令标记，避免 PID 复用时误停其他程序；识别到其他程序占用端口时会报错，不会强制清理端口。Python 虚拟环境启动后会跟踪实际监听进程，避免启停只处理启动器。

## 配置

项目根目录的 `.env.native` 为本机私有配置，Git 忽略。模板为 `infra/native.env.example`。

| 配置 | 当前默认值 / 用途 |
| --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` | `127.0.0.1` / `13306` |
| `MYSQL_DATABASE` / `MYSQL_USER` | `interviewcopilot` / `interviewcopilot` |
| `MYSQL_PASSWORD` | 已有本地账号的密码 |
| `INTERNAL_AI_SERVICE_TOKEN` | Java → Python 的共享内部令牌 |
| `JWT_SIGNING_SECRET` | 至少 32 字符的签名密钥 |
| `AI_PROVIDER` | `mock`，当前仅验收模拟能力 |
| `AI_SERVICE_PORT` | `8001` |
| `BUSINESS_SERVICE_PORT` | `8080` |
| `FRONTEND_PORT` | `5173` |
| `MARIADB_CONFIG` | 已有隔离数据库的 `my.ini` 完整路径 |
| `MARIADB_EXECUTABLE` | 可留空，自动查找本机既有隔离 MariaDB 程序 |

配置采用逐行 `NAME=value`，值不加引号；脚本不会把配置内容作为命令执行。启动前需安装各模块依赖，步骤见根目录 README。

脚本新启动的服务绑定 `127.0.0.1`；已在运行的数据库沿用既有配置，不能仅凭“本地使用”推断其未对其他网卡监听，使用者仍需核对数据库绑定和防火墙。本机已有 MySQL80 与本项目的隔离实例相互独立。

## 启动保障与数据库兼容

- 按数据库 → AI → Java → 前端的依赖关系启动，并等待 HTTP 就绪。
- 自动对比后端源码、资源和 `pom.xml` 的修改时间；JAR 过期时先重新打包再启动。已有旧 JAR 不可直接当作最新业务实现。
- Java 使用 `local` profile，仍启用 Hibernate `validate`，不以关闭结构校验绕过数据库兼容问题。
- 原生 MariaDB 使用专用方言处理 JSON 在 JDBC 元数据中表现为 LONGTEXT 的别名差异；CI / Docker MySQL 不切换此方言。
- UUID 维持原有未交换的 `BINARY(16)` 布局，使用可移植 SQL 和 Java 字节转换，不依赖手工创建的 `UUID_TO_BIN` / `BIN_TO_UUID` 函数。
- 报告保存校验领域版本，防止旧版本覆盖新版本；一次报告生成多次保存使用返回的新版本。AI 调用失败时保存 `FAILED_RETRYABLE` 并释放幂等记录。

## 地址与日志

| 服务 | 地址 |
| --- | --- |
| 中文界面 | <http://127.0.0.1:5173/> |
| Java 就绪检查 | <http://127.0.0.1:8080/actuator/health/readiness> |
| Java 存活检查 | <http://127.0.0.1:8080/actuator/health/liveness> |
| AI 就绪检查 | <http://127.0.0.1:8001/health/ready> |
| 数据库 | `127.0.0.1:13306`，无网页 |

调整端口后以上地址也需要随之调整。前端开发代理由脚本传入 `BUSINESS_API_PROXY_TARGET`，不直接请求 Python。

日志位于 `.tmp/native/`：

- `business.stdout.log` / `business.stderr.log`：Java 启动、数据库和业务异常。
- `ai.stdout.log` / `ai.stderr.log`：Python 服务。
- `frontend.stdout.log` / `frontend.stderr.log`：Vite。
- `database.stdout.log` / `database.stderr.log`：由脚本新启动的数据库。
- `processes.json`：启动脚本管理的进程信息。

重复启动时，日志可能被下一次启动覆盖。不要将可能含请求数据的完整日志公开上传。

## 完整验收

项目启动后，在根目录执行：

```powershell
node infra/verify-native.mjs
node infra/verify-browser.mjs
```

接口验收包含 40 项检查：健康、鉴权、创建、启动、三题答题、报告、分页、取消、权限隔离、错误码和幂等重放。浏览器验收包含中文交互、中文技能分隔、历史列表渲染、草稿刷新恢复、完整报告流程、手机布局和运行时错误检查。

验收只允许访问本机地址；通过 `FRONTEND_BASE_URL`、`BUSINESS_BASE_URL`、`AI_BASE_URL` 指定非默认端口。浏览器验收需要 Node.js 22.12+ 及已安装的 Chrome / Edge；其他安装位置用 `CHROME_PATH` 指定。脚本使用独立测试账号，不会删除已有记录。

## 隔离数据库回归

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File infra/verify-database.ps1
```

脚本复用本机 `%LOCALAPPDATA%/InterviewCopilot/mariadb-runtime` 中的数据库程序，随机端口和密码，新建 `.tmp/native-db-test-<随机值>` 临时数据目录，再运行完整 Java 测试。测试创建和删除自身独立 schema；结束时停止临时实例，保留目录及 `maven.log` 供诊断，现有业务数据库不修改。

该命令启用数据库集成测试；默认跳过唯一需要运行中 Python 的实时契约测试。若项目已启动，可以在同一个 PowerShell 中仅载入内部令牌并启用该项：

```powershell
$tokenLine = Get-Content .env.native -Encoding UTF8 |
    Where-Object { $_.StartsWith('INTERNAL_AI_SERVICE_TOKEN=') } |
    Select-Object -First 1
$env:INTERNAL_AI_SERVICE_TOKEN = $tokenLine.Substring('INTERNAL_AI_SERVICE_TOKEN='.Length)
$env:AI_INTEGRATION_BASE_URL = 'http://127.0.0.1:8001'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File infra/verify-database.ps1
```

2026-10-08 本机按以上方式完成 Java 125 项测试，失败、错误和跳过均为 0。该实时测试调用本地 Python mock，不需要外部大模型密钥，也不代表真实模型已经接入。

## Docker 与上线边界

原有 `compose.yaml`、`infra/verify-local.mjs` 和 CI MySQL Testcontainers 继续保留，作为可选容器/CI 方案；本次本机验收没有运行 Docker 环境。

本地启动成功不等于可直接公网部署。真实模型、HTTPS、密钥轮换、限流、数据备份恢复、数据库网络收敛和监控仍需后续单独配置与验收。
