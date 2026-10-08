param(
    [ValidateSet('Start', 'Stop', 'Restart', 'Status')]
    [string]$Action = 'Start',
    [switch]$StopDatabase
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeDirectory = Join-Path $projectRoot '.tmp/native'
$stateFile = Join-Path $runtimeDirectory 'processes.json'
$configFile = Join-Path $projectRoot '.env.native'
$tracked = @{}
if (Test-Path -LiteralPath $stateFile) {
    $savedState = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    foreach ($property in $savedState.PSObject.Properties) { $tracked[$property.Name] = $property.Value }
}

function Save-State {
    New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
    $tracked | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $stateFile -Encoding UTF8
}

function Import-Configuration {
    if (-not (Test-Path -LiteralPath $configFile)) {
        throw '缺少 .env.native。请复制 infra/native.env.example 到项目根目录 .env.native，并填写数据库密码和服务密钥。'
    }
    foreach ($line in Get-Content -LiteralPath $configFile -Encoding UTF8) {
        if ($line.Trim() -eq '' -or $line.TrimStart().StartsWith('#')) { continue }
        if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw '配置文件格式错误，请使用 NAME=value。' }
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
    }
    foreach ($name in @('MYSQL_PASSWORD', 'INTERNAL_AI_SERVICE_TOKEN', 'JWT_SIGNING_SECRET')) {
        if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) { throw "请先在 .env.native 填写 $name。" }
    }
    if ($env:JWT_SIGNING_SECRET.Length -lt 32) { throw 'JWT_SIGNING_SECRET 至少需要 32 个可见 ASCII 字符。' }
    foreach ($name in @('MYSQL_PORT', 'AI_SERVICE_PORT', 'BUSINESS_SERVICE_PORT', 'FRONTEND_PORT')) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($value -notmatch '^\d+$' -or [int]$value -lt 1 -or [int]$value -gt 65535) { throw "$name 必须为 1–65535 之间的端口号。" }
    }
    if ($env:MYSQL_HOST -notin @('127.0.0.1', 'localhost')) { throw '原生启动脚本仅用于本机数据库。' }
    $env:SPRING_PROFILES_ACTIVE = 'local'
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:AI_SERVICE_BASE_URL = "http://127.0.0.1:$($env:AI_SERVICE_PORT)"
    $env:BUSINESS_API_PROXY_TARGET = "http://127.0.0.1:$($env:BUSINESS_SERVICE_PORT)"
    $env:SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT = 'com.interviewcopilot.business.persistence.NativeMariaDbDialect'
    $env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
}

function Listener([int]$Port) {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if ($listeners.Count) { return Get-CimInstance Win32_Process -Filter "ProcessId = $($listeners[0].OwningProcess)" }
    return $null
}

function Is-ExpectedProcess($ProcessInfo, [string]$Marker) {
    return $ProcessInfo -and $ProcessInfo.CommandLine -and
        $ProcessInfo.CommandLine.Replace('/', '\').IndexOf($Marker.Replace('/', '\'), [StringComparison]::OrdinalIgnoreCase) -ge 0
}

function Track-Process([string]$Name, $ProcessInfo, [string]$Marker) {
    $tracked[$Name] = @{ pid = [int]$ProcessInfo.ProcessId; marker = $Marker; created = $ProcessInfo.CreationDate.ToUniversalTime().ToString('o') }
    Save-State
}

function Stop-Tracked([string]$Name) {
    if (-not $tracked.ContainsKey($Name)) { return }
    $entry = $tracked[$Name]
    $info = Get-CimInstance Win32_Process -Filter "ProcessId = $($entry.pid)" -ErrorAction SilentlyContinue
    if (Is-ExpectedProcess $info $entry.marker) {
        if ($info.CreationDate.ToUniversalTime().ToString('o') -eq $entry.created) {
            $process = Get-Process -Id $entry.pid -ErrorAction Stop
            Stop-Process -Id $entry.pid -ErrorAction Stop
            if (-not $process.WaitForExit(10000)) { throw "$Name 停止超时，请查看进程状态后重试。" }
            Write-Host "$Name 已停止。"
        }
    }
    $tracked.Remove($Name)
    Save-State
}

function Start-ServiceProcess([string]$Name, [string]$Executable, [string[]]$Arguments, [string]$Directory, [int]$Port, [string]$Marker) {
    $existing = Listener $Port
    if ($existing) {
        if (-not (Is-ExpectedProcess $existing $Marker)) { throw "端口 $Port 已被其他程序占用，未对其进行操作。" }
        Track-Process $Name $existing $Marker
        Write-Host "$Name 已在运行。"
        return
    }
    New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
    $started = Start-Process -FilePath $Executable -ArgumentList $Arguments -WorkingDirectory $Directory -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runtimeDirectory "$Name.stdout.log") -RedirectStandardError (Join-Path $runtimeDirectory "$Name.stderr.log")
    $info = Get-CimInstance Win32_Process -Filter "ProcessId = $($started.Id)"
    if (-not $info) { throw "$Name 启动失败，请查看 .tmp/native 中的日志。" }
    Track-Process $Name $info $Marker
    Write-Host "$Name 已启动。"
}

function Wait-Healthy([string]$Url, [string]$Name, [int]$Port, [string]$Marker, [int]$Seconds = 60) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                $listener = Listener $Port
                if (-not (Is-ExpectedProcess $listener $Marker)) { throw "$Name 监听进程与项目不匹配。" }
                Track-Process $Name $listener $Marker
                Write-Host "$Name 就绪。"
                return
            }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "$Name 未能就绪，请查看 .tmp/native/$Name.stderr.log 和 $Name.stdout.log。"
}

try {
    if ($Action -in @('Stop', 'Restart')) {
        foreach ($name in @('frontend', 'business', 'ai')) { Stop-Tracked $name }
        if ($StopDatabase) { Stop-Tracked 'database' }
        if ($Action -eq 'Stop') { Write-Host '应用已停止，数据库数据已保留。'; exit 0 }
    }
    Import-Configuration
    if ($Action -eq 'Status') {
        foreach ($item in @(@('database', $env:MYSQL_PORT), @('ai', $env:AI_SERVICE_PORT), @('business', $env:BUSINESS_SERVICE_PORT), @('frontend', $env:FRONTEND_PORT))) {
            $port = [int]$item[1]
            $state = if (Listener $port) { '端口已监听' } else { '未运行' }
            Write-Host "$($item[0]) : $port - $state"
        }
        exit 0
    }

    $java = (Get-Command java -ErrorAction Stop).Source
    $node = (Get-Command node -ErrorAction Stop).Source
    $maven = (Get-Command mvn -ErrorAction Stop).Source
    $python = Join-Path $projectRoot 'ai-service/.venv/Scripts/python.exe'
    $vite = Join-Path $projectRoot 'frontend/node_modules/vite/bin/vite.js'
    if (-not (Test-Path -LiteralPath $python)) { throw '缺少 Python 虚拟环境，请按 README.md 安装 AI 服务依赖。' }
    if (-not (Test-Path -LiteralPath $vite)) { throw '缺少前端依赖，请先在 frontend 执行 npm ci。' }

    $databaseProcess = Listener ([int]$env:MYSQL_PORT)
    if ($databaseProcess) {
        if (-not $env:MARIADB_CONFIG -or -not (Is-ExpectedProcess $databaseProcess $env:MARIADB_CONFIG)) { throw '数据库端口被其他实例占用，请检查 MYSQL_PORT 和 MARIADB_CONFIG。' }
        Track-Process 'database' $databaseProcess $env:MARIADB_CONFIG
    } else {
        if (-not $env:MARIADB_EXECUTABLE) {
            $runtime = Join-Path $env:LOCALAPPDATA 'InterviewCopilot/mariadb-runtime'
            $candidate = Get-ChildItem -LiteralPath $runtime -Filter mariadbd.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($candidate) { $env:MARIADB_EXECUTABLE = $candidate.FullName }
        }
        if (-not $env:MARIADB_EXECUTABLE -or -not (Test-Path -LiteralPath $env:MARIADB_CONFIG)) { throw '请在 .env.native 配置已有 MariaDB 的程序和配置文件路径。' }
        Start-ServiceProcess 'database' $env:MARIADB_EXECUTABLE @("--defaults-file=`"$($env:MARIADB_CONFIG)`"", '--bind-address=127.0.0.1', '--console') $projectRoot ([int]$env:MYSQL_PORT) $env:MARIADB_CONFIG
        $databaseDeadline = [DateTime]::UtcNow.AddSeconds(30)
        while (-not (Listener ([int]$env:MYSQL_PORT))) {
            if ([DateTime]::UtcNow -gt $databaseDeadline) { throw '数据库启动超时，请检查 database 日志。' }
            Start-Sleep -Milliseconds 500
        }
    }

    $jar = Join-Path $projectRoot 'business-service/target/interviewcopilot-business-service-0.1.0-SNAPSHOT.jar'
    $latestSource = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'business-service/src/main') -Recurse -File | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $needsBuild = -not (Test-Path -LiteralPath $jar)
    if (-not $needsBuild) {
        $jarTime = (Get-Item -LiteralPath $jar).LastWriteTimeUtc
        $needsBuild = $latestSource.LastWriteTimeUtc -gt $jarTime -or (Get-Item (Join-Path $projectRoot 'business-service/pom.xml')).LastWriteTimeUtc -gt $jarTime
    }
    if ($needsBuild) {
        $existing = Listener ([int]$env:BUSINESS_SERVICE_PORT)
        if ($existing) {
            if (-not (Is-ExpectedProcess $existing $jar)) { throw '业务端口被其他程序占用，无法更新业务服务。' }
            Track-Process 'business' $existing $jar
            Stop-Tracked 'business'
        }
        Write-Host '正在构建最新业务服务，避免旧 JAR 导致接口 404…'
        Push-Location (Join-Path $projectRoot 'business-service')
        try { & $maven -q '-DskipTests' package; if ($LASTEXITCODE -ne 0) { throw '业务服务构建失败。' } } finally { Pop-Location }
    }
    Start-ServiceProcess 'ai' $python @('-m', 'uvicorn', 'interviewcopilot_ai.main:app', '--host', '127.0.0.1', '--port', $env:AI_SERVICE_PORT) (Join-Path $projectRoot 'ai-service') ([int]$env:AI_SERVICE_PORT) 'interviewcopilot_ai.main:app'
    Wait-Healthy "$($env:AI_SERVICE_BASE_URL)/health/ready" 'ai' ([int]$env:AI_SERVICE_PORT) 'interviewcopilot_ai.main:app'
    Start-ServiceProcess 'business' $java @('-jar', "`"$jar`"") (Join-Path $projectRoot 'business-service') ([int]$env:BUSINESS_SERVICE_PORT) $jar
    Wait-Healthy "http://127.0.0.1:$($env:BUSINESS_SERVICE_PORT)/actuator/health/readiness" 'business' ([int]$env:BUSINESS_SERVICE_PORT) $jar
    Start-ServiceProcess 'frontend' $node @("`"$vite`"", '--host', '127.0.0.1', '--port', $env:FRONTEND_PORT, '--strictPort') (Join-Path $projectRoot 'frontend') ([int]$env:FRONTEND_PORT) (Join-Path $projectRoot 'frontend')
    Wait-Healthy "http://127.0.0.1:$($env:FRONTEND_PORT)/" 'frontend' ([int]$env:FRONTEND_PORT) (Join-Path $projectRoot 'frontend')
    Write-Host "启动完成，请打开 http://127.0.0.1:$($env:FRONTEND_PORT)/"
} catch {
    Write-Host "操作失败：$($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
