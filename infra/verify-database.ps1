$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeBase = Join-Path $env:LOCALAPPDATA 'InterviewCopilot/mariadb-runtime'
$serverFile = Get-ChildItem -LiteralPath $runtimeBase -Filter mariadbd.exe -Recurse | Select-Object -First 1
if (-not $serverFile) { throw '未找到已有 MariaDB 程序。此脚本复用本机安装，不会下载数据库。' }
$binDirectory = $serverFile.DirectoryName
$testDirectory = Join-Path $projectRoot ('.tmp/native-db-test-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDirectory -Force | Out-Null
$portListener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$portListener.Start()
$testPort = $portListener.LocalEndpoint.Port
$portListener.Stop()
$testPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
$testServer = $null
$testExitCode = 1
$variableNames = @('MIGRATION_TEST_RUN', 'MIGRATION_TEST_JDBC_BASE_URL', 'MIGRATION_TEST_DB_USER', 'MIGRATION_TEST_DB_PASSWORD', 'SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT', 'MYSQL_PWD')
function Invoke-DatabaseAdmin([string]$Command) {
    $admin = Start-Process -FilePath (Join-Path $binDirectory 'mariadb-admin.exe') `
        -ArgumentList @('--no-defaults', '--host=127.0.0.1', "--port=$testPort", '--user=root', $Command) `
        -WindowStyle Hidden -Wait -PassThru `
        -RedirectStandardOutput (Join-Path $testDirectory 'admin.stdout.log') `
        -RedirectStandardError (Join-Path $testDirectory 'admin.stderr.log')
    return $admin.ExitCode
}
$previousEnvironment = @{}
foreach ($name in $variableNames) { $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    & (Join-Path $binDirectory 'mariadb-install-db.exe') "--datadir=$testDirectory" "--port=$testPort" "--password=$testPassword" --silent
    if ($LASTEXITCODE -ne 0) { throw '隔离测试数据库初始化失败。' }
    $testServer = Start-Process -FilePath $serverFile.FullName -ArgumentList @("--defaults-file=`"$(Join-Path $testDirectory 'my.ini')`"", '--bind-address=127.0.0.1', '--console') `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testDirectory 'server.stdout.log') -RedirectStandardError (Join-Path $testDirectory 'server.stderr.log')
    $env:MYSQL_PWD = $testPassword
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ((Invoke-DatabaseAdmin 'ping') -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw '隔离测试数据库启动超时。' }
    $env:MIGRATION_TEST_RUN = 'true'
    $env:MIGRATION_TEST_JDBC_BASE_URL = "jdbc:mysql://127.0.0.1:$testPort/"
    $env:MIGRATION_TEST_DB_USER = 'root'
    $env:MIGRATION_TEST_DB_PASSWORD = $testPassword
    $env:SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT = 'com.interviewcopilot.business.persistence.NativeMariaDbDialect'
    Push-Location (Join-Path $projectRoot 'business-service')
    try {
        $maven = (Get-Command mvn -ErrorAction Stop).Source
        try {
            # Windows PowerShell 5.1 treats native stderr as error records, even for warnings.
            $ErrorActionPreference = 'Continue'
            & $maven -q test *> (Join-Path $testDirectory 'maven.log')
            $testExitCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = 'Stop' }
        if ($testExitCode -ne 0) { Get-Content -LiteralPath (Join-Path $testDirectory 'maven.log') -Tail 35 }
        else { Write-Host '数据库迁移、鉴权、聚合持久化、乐观锁、幂等和报告失败恢复验证通过。' }
    } finally { Pop-Location }
} catch {
    Write-Host "数据库验证失败：$($_.Exception.Message)" -ForegroundColor Red
} finally {
    if ($testServer -and -not $testServer.HasExited) {
        Invoke-DatabaseAdmin 'shutdown' | Out-Null
        if (-not $testServer.WaitForExit(5000)) { Stop-Process -Id $testServer.Id }
    }
    foreach ($name in $variableNames) { [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process') }
    Write-Host "隔离数据库已停止；诊断日志保留于 $testDirectory。现有数据库未修改。"
}
exit $testExitCode
