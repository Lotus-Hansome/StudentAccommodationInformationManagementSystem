param(
    [switch]$SmokeTest,
    [switch]$Console,
    [switch]$Desktop,
    [switch]$Restart
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$SourceDir = Join-Path $ProjectRoot "src\main\java\com\dormitory"
$OutDir = Join-Path $ProjectRoot "out"
$LibDir = Join-Path $ProjectRoot "lib"
$MysqlDriver = Join-Path $LibDir "mysql-connector-j-8.4.0.jar"

function Test-HttpApp($Port) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:$Port/" -UseBasicParsing -TimeoutSec 2
        return $response.StatusCode -eq 200 -and $response.Content.Contains("Dormitory Console")
    } catch {
        return $false
    }
}

function Get-FreePort($StartPort) {
    for ($port = $StartPort; $port -lt ($StartPort + 30); $port++) {
        $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if (-not $listener) {
            return $port
        }
    }
    throw "No available port found near 8080. Please close the process using that port and retry."
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Sources = Get-ChildItem -Path $SourceDir -Filter "*.java" | ForEach-Object { $_.FullName }

javac -encoding UTF-8 -d $OutDir $Sources

if ($SmokeTest) {
    java -cp $OutDir com.dormitory.DormitoryManagementSystem --smoke-test
} else {
    New-Item -ItemType Directory -Force -Path $LibDir | Out-Null
    if (-not (Test-Path $MysqlDriver)) {
        Write-Host "Downloading MySQL Connector/J..."
        Invoke-WebRequest `
            -Uri "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar" `
            -OutFile $MysqlDriver
    }
    if ($Console) {
        java -cp "$OutDir;$MysqlDriver" com.dormitory.DormitoryManagementSystem --console
    } elseif ($Desktop) {
        java -cp "$OutDir;$MysqlDriver" com.dormitory.DormitoryManagementSystem --desktop
    } else {
        $requestedPort = 8080
        if ($env:APP_PORT) {
            $requestedPort = [int]$env:APP_PORT
        }
        $listener = Get-NetTCPConnection -LocalPort $requestedPort -State Listen -ErrorAction SilentlyContinue
        if ($listener) {
            if (Test-HttpApp $requestedPort) {
                if ($Restart) {
                    Write-Host "Restarting application on port $requestedPort..."
                    Stop-Process -Id $listener.OwningProcess -Force
                    Start-Sleep -Seconds 1
                } else {
                Write-Host "Application is already running: http://localhost:$requestedPort"
                Start-Process "http://localhost:$requestedPort"
                return
                }
            } else {
                $freePort = Get-FreePort ($requestedPort + 1)
                $env:APP_PORT = "$freePort"
                Write-Host "Port $requestedPort is in use. Starting with: http://localhost:$freePort"
            }
        }
        java -cp "$OutDir;$MysqlDriver" com.dormitory.DormitoryManagementSystem
    }
}
