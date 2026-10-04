# ===========================================================================
#  Neo ECO IO Port —— 构建脚本
#  由 build.bat 调用；也可以直接右键「使用 PowerShell 运行」。
#  用法: powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1 [gradle 任务...]
# ===========================================================================

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Tasks = @('build')
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$logFile = Join-Path $root 'build-log.txt'

# ---------------------------------------------------------------------------
# 把临时目录指到可写位置，并尽量使用「短英文路径」。
#
# 背景一（必须）：NeoFormRuntime 的 transformSources 阶段会 fork 出独立的外部
# 工具进程（net.neoforged.jst:jst-cli-bundle），它调用 Files.createTempDirectory()，
# 默认落在 %TEMP%。在受限环境（沙箱 / 只读用户目录）下会失败：
#
#   java.nio.file.AccessDeniedException:
#     C:\Users\<用户>\AppData\Local\Temp\jst<随机数>
#
# 背景二（锦上添花）：如果工程位于含中文的目录（例如 F:\DEEPSEEK工作区\...），
# NeoForm 重编译 Minecraft 时对包含非 ASCII 的路径较敏感。把临时目录放到纯英文
# 路径可以显著减少这类风险，代价比重整工程小得多。
#
# TEMP / TMP 会被 JVM 用来初始化 java.io.tmpdir，也会被所有子进程继承，
# 所以设置这两个变量即可同时覆盖「读环境变量」和「读系统属性」两套实现。
#
# 刻意不使用 JAVA_TOOL_OPTIONS：它会让每一个 java 进程都往 stderr 打印
#   Picked up JAVA_TOOL_OPTIONS: -Djava.io.tmpdir=...
# 容易被误认为报错。实测 TEMP/TMP 单独就足以让 java.io.tmpdir 生效。
# ---------------------------------------------------------------------------
$javaTmp = $null

# 优先：驱动器根目录下的短英文路径（纯 ASCII）
$driveRoot = [System.IO.Path]::GetPathRoot($root)          # 例如 F:\
foreach ($candidate in @('neoeco-build-tmp', 'tmp')) {
    $short = Join-Path $driveRoot $candidate
    try {
        if (-not (Test-Path $short)) {
            New-Item -ItemType Directory -Force -Path $short -ErrorAction Stop | Out-Null
        }
        # 实际写一个文件确认可写
        $probe = Join-Path $short 'write-probe.tmp'
        [System.IO.File]::WriteAllText($probe, 'ok')
        Remove-Item $probe -Force -ErrorAction SilentlyContinue
        $javaTmp = $short
        break
    } catch {
        # 该位置不可写，换下一个候选
    }
}

# 兜底：工程内的 build\tmp\javatmp
if (-not $javaTmp) {
    $javaTmp = Join-Path $root 'build\tmp\javatmp'
    if (-not (Test-Path $javaTmp)) {
        New-Item -ItemType Directory -Force -Path $javaTmp | Out-Null
    }
}

$env:TEMP = $javaTmp
$env:TMP = $javaTmp

function Write-Head($text) {
    Write-Host ''
    Write-Host '============================================================' -ForegroundColor Cyan
    Write-Host "  $text" -ForegroundColor Cyan
    Write-Host '============================================================' -ForegroundColor Cyan
}

function Write-Step($text) {
    Write-Host ''
    Write-Host "[$text]" -ForegroundColor Yellow
}

function Fail($message) {
    Write-Host ''
    Write-Host '============================================================' -ForegroundColor Red
    Write-Host "  [失败] $message" -ForegroundColor Red
    Write-Host '============================================================' -ForegroundColor Red
    Write-Host ''
    Read-Host '按回车键关闭窗口'
    exit 1
}

Write-Head 'Neo ECO IO Port  |  构建'

# ---------------------------------------------------------------------------
# 1. JDK 21
# ---------------------------------------------------------------------------
Write-Step '1/4 探测 Java 环境'

$javaHome = $null

# 优先用工程内自带的便携版 JDK 21
$bundled = Join-Path $root 'jdk21'
if (Test-Path (Join-Path $bundled 'bin\javac.exe')) {
    $javaHome = $bundled
    Write-Host '      使用工程自带 JDK 21' -ForegroundColor Green
}

# 其次用 JAVA_HOME
if (-not $javaHome -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\javac.exe'
    if (Test-Path $candidate) {
        try {
            $verOut = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1 | Out-String
            if ($verOut -match 'version "21') {
                $javaHome = $env:JAVA_HOME
                Write-Host "      使用系统 JAVA_HOME: $javaHome" -ForegroundColor Green
            }
        } catch { }
    }
}

# 最后扫一遍常见安装位置
if (-not $javaHome) {
    $searchRoots = @(
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Java',
        'C:\Program Files\Microsoft',
        'C:\Program Files\BellSoft',
        'C:\Program Files\Zulu',
        'C:\Program Files\Amazon Corretto',
        'D:\Java',
        'F:\Java'
    )
    foreach ($r in $searchRoots) {
        if (-not (Test-Path $r)) { continue }
        $dirs = Get-ChildItem -Path $r -Directory -ErrorAction SilentlyContinue
        foreach ($d in $dirs) {
            if ($d.Name -like '*21*' -and (Test-Path (Join-Path $d.FullName 'bin\javac.exe'))) {
                $javaHome = $d.FullName
                Write-Host "      自动发现 JDK 21: $javaHome" -ForegroundColor Green
                break
            }
        }
        if ($javaHome) { break }
    }
}

if (-not $javaHome) {
    Write-Host '      [x] 没有找到 JDK 21' -ForegroundColor Red
    Fail @'
Minecraft 1.21.1 必须用 JDK 21 构建。

工程内本应自带 jdk21\ 目录（约 328MB）。若已被删除，请到
  https://adoptium.net/temurin/releases/?version=21
下载 "Windows x64 .zip"，解压后重命名为 jdk21 放回工程根目录。
'@
}

$env:JAVA_HOME = $javaHome
& (Join-Path $javaHome 'bin\java.exe') -version 2>&1 | ForEach-Object { Write-Host "      $_" }

# ---------------------------------------------------------------------------
# 2. Gradle
# ---------------------------------------------------------------------------
Write-Step '2/4 探测 Gradle'

$gradleCmd = $null
$bundledGradle = Join-Path $root 'gradle-8.10.2\bin\gradle.bat'
if (Test-Path $bundledGradle) {
    $gradleCmd = $bundledGradle
    Write-Host '      使用工程自带 Gradle 8.10.2' -ForegroundColor Green
} elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
    $gradleCmd = 'gradle'
    Write-Host '      使用系统 gradle' -ForegroundColor Green
}

if (-not $gradleCmd) {
    Write-Host '      [x] 没找到 Gradle' -ForegroundColor Red
    Fail @'
工程内本应自带 gradle-8.10.2\ 目录。若已被删除，请下载
  https://services.gradle.org/distributions/gradle-8.10.2-bin.zip
解压后重命名为 gradle-8.10.2 放回工程根目录。
'@
}

# Gradle 用户目录。
#
# 反编译结果与 NeoForm 中间产物都缓存在这里，所以这个目录的路径也最好保持纯 ASCII。
#
# 如果你不想搬动整个工程（482MB，含 jdk21 与 gradle），可以用一个更省事的办法：
# 在工程根目录新建 gradle-home.txt，里面只写一行英文路径，例如
#     F:\neoeco-gradle-home
# 脚本会用它作为 GRADLE_USER_HOME。这样除了工程源码本身，其余路径全部是 ASCII。
# 代价：第一次会因为缓存不在原位置而重新跑一遍 NeoForm（约 10 分钟）。
if (-not $env:GRADLE_USER_HOME) {
    $ghOverride = Join-Path $root 'gradle-home.txt'
    if (Test-Path $ghOverride) {
        $custom = (Get-Content $ghOverride -First 1).Trim()
        if ($custom) {
            if (-not (Test-Path $custom)) {
                New-Item -ItemType Directory -Force -Path $custom -ErrorAction SilentlyContinue | Out-Null
            }
            if (Test-Path $custom) {
                $env:GRADLE_USER_HOME = $custom
                Write-Host "      已读取 gradle-home.txt，使用自定义 Gradle 用户目录" -ForegroundColor Green
            } else {
                Write-Host "      [警告] gradle-home.txt 指定的目录无法创建，改用默认位置" -ForegroundColor Yellow
            }
        }
    }
    if (-not $env:GRADLE_USER_HOME) {
        $env:GRADLE_USER_HOME = Join-Path $root '.gradle-home'
    }
}
Write-Host "      GRADLE_USER_HOME = $env:GRADLE_USER_HOME"
Write-Host "      临时目录 TEMP/TMP/java.io.tmpdir = $javaTmp"

# ---------------------------------------------------------------------------
# 路径检查：强烈建议把工程放在纯 ASCII 路径下
#
# Minecraft 1.21.1 + NeoForge 21.1.252 是标准组合，正常一定能编译。
# 但 NeoFormRuntime 在 recompile 阶段要把 5364 个源文件路径交给 javac，
# 工程路径含中文（例如 F:\DEEPSEEK工作区\...）时会失败，典型现象是：
#
#   ERROR Line: 1, ..net.minecraft.world.ticks.package-info...: null
#   Node action for recompile failed
#
# 报错信息里的 null 就是因为 javac 的详细诊断没能被正确回传。
# 这类路径问题在 Windows 的 Java 工具链上非常常见，所以这里直接给出告警。
# ---------------------------------------------------------------------------
$nonAscii = [regex]::Matches($root, '[^\x00-\x7F]')
if ($nonAscii.Count -gt 0) {
    Write-Host ''
    Write-Host '      [警告] 工程路径包含非 ASCII 字符（例如中文）：' -ForegroundColor Yellow
    Write-Host "             $root" -ForegroundColor Yellow
    Write-Host '             NeoForm 在重编译 Minecraft 时可能因此失败。' -ForegroundColor Yellow
    Write-Host '             如果构建在 recompile 阶段报错，请把整个 NeoEcoIOPort 目录' -ForegroundColor Yellow
    Write-Host '             移动到纯英文路径（例如 F:\NeoEcoIOPort）后重试。' -ForegroundColor Yellow
    Write-Host ''
}

# 可选代理：工程根目录放 proxy.txt，内容只有一行端口号，例如 7890
$proxyArgs = @()
$proxyFile = Join-Path $root 'proxy.txt'
if (Test-Path $proxyFile) {
    $port = (Get-Content $proxyFile -First 1).Trim()
    if ($port) {
        $proxyArgs = @(
            "-Dhttps.proxyHost=127.0.0.1", "-Dhttps.proxyPort=$port",
            "-Dhttp.proxyHost=127.0.0.1", "-Dhttp.proxyPort=$port"
        )
        Write-Host "      已读取 proxy.txt，使用代理端口 $port" -ForegroundColor Green
    }
}

# ---------------------------------------------------------------------------
# 3. 构建
# ---------------------------------------------------------------------------
Write-Step '3/4 开始构建'

Write-Host ''
Write-Host '      首次构建会下载 NeoForge 并反编译 Minecraft，通常需要 10-30 分钟。' -ForegroundColor Gray
Write-Host '      期间可能长时间没有输出，属于正常现象，请勿关闭窗口。' -ForegroundColor Gray
Write-Host "      完整日志: $logFile" -ForegroundColor Gray
Write-Host ''

$gradleArgs = @('--no-daemon', '--console=plain') + $proxyArgs + $Tasks
Write-Host "      执行: $gradleCmd $($gradleArgs -join ' ')" -ForegroundColor Gray
Write-Host ''

# ---------------------------------------------------------------------------
# 构建（带自动重试）
#
# 为什么要重试：maven.neoforged.net 是 NeoForm 工具链（neoform-runtime /
# minecraft-dependencies）的唯一来源，且没有可用的镜像。实测该主机是
# **间歇性可达**的 —— 有时能下载，有时直接连接超时。而 Gradle 一旦解析失败
# 就会立刻放弃，白白浪费一次完整尝试。
#
# 另外，mc 反编译产物一旦生成就会缓存在 GRADLE_USER_HOME 的
# caches\neoformruntime 下，之后不再需要联网。所以只要成功连线**一次**，
# 后续构建就稳定了。
# ---------------------------------------------------------------------------
$maxAttempts = 3
if ($env:NEOECO_MAX_ATTEMPTS) {
    $parsed = 0
    if ([int]::TryParse($env:NEOECO_MAX_ATTEMPTS, [ref]$parsed) -and $parsed -gt 0) {
        $maxAttempts = $parsed
    }
}

$exitCode = 1
$logOutput = @()

for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
    if ($attempt -gt 1) {
        Write-Host ''
        Write-Host "      === 第 $attempt / $maxAttempts 次尝试（网络可能恢复）===" -ForegroundColor Yellow
        Start-Sleep -Seconds 10
    }

    # 日志同时输出到屏幕和变量。
    # 注意：PowerShell 5.1 的 Tee-Object 默认以 UTF-16LE 写文件，很多编辑器/工具
    # 会把它当成二进制，也不方便直接复制粘贴。所以先收集输出，
    # 再用无 BOM 的 UTF-8 落盘。
    $logOutput = & $gradleCmd @gradleArgs 2>&1 | Tee-Object -Variable attemptOutput
    $exitCode = $LASTEXITCODE

    try {
        $logText = ($logOutput | Out-String)
        [System.IO.File]::WriteAllText($logFile, $logText, (New-Object System.Text.UTF8Encoding($false)))
    } catch {
        Write-Host "      [警告] 写入日志文件失败: $($_.Exception.Message)" -ForegroundColor Yellow
    }

    if ($exitCode -eq 0) {
        break
    }

    # 只有「网络解析失败」才值得重试；编译错误重试没有意义
    $text = ''
    try { $text = ($logOutput | Out-String) } catch { }
    $isNetworkIssue = $text -match 'maven\.neoforged\.net|Connection timed out|SocketTimeoutException|Could not resolve all|Could not GET'

    if (-not $isNetworkIssue) {
        break
    }

    if ($attempt -lt $maxAttempts) {
        Write-Host ''
        Write-Host '      [i] 本次失败是网络问题，将自动重试。' -ForegroundColor Yellow
        Write-Host '          如果反复失败，请在工程根目录建 proxy.txt（只写一行端口号，如 7890）。' -ForegroundColor Yellow
    }
}

# ---------------------------------------------------------------------------
# 4. 结果
# ---------------------------------------------------------------------------
if ($exitCode -ne 0) {
    Write-Host ''
    Write-Host '============================================================' -ForegroundColor Red
    Write-Host "  [失败] 构建未通过（退出码 $exitCode）" -ForegroundColor Red
    Write-Host '============================================================' -ForegroundColor Red

    $logText = ''
    if (Test-Path $logFile) { $logText = Get-Content $logFile -Raw }

    if ($logText -match 'maven\.neoforged\.net|Connection timed out|SocketTimeoutException|Could not resolve') {
        Write-Host ''
        Write-Host '  看起来是网络问题（多数情况是 maven.neoforged.net 连不上）。' -ForegroundColor Yellow
        Write-Host ''
        Write-Host '  如果你有本地代理：' -ForegroundColor Yellow
        Write-Host '    在工程根目录新建 proxy.txt，里面只写一行端口号（例如 7890），' -ForegroundColor Yellow
        Write-Host '    然后重新运行 build.bat。' -ForegroundColor Yellow
        Write-Host ''
        Write-Host '  注意：init-mirrors.gradle 的国内镜像对本工程只能解决一半问题，' -ForegroundColor DarkGray
        Write-Host '        镜像上没有 neoform-runtime 与 minecraft-dependencies。' -ForegroundColor DarkGray
    }

    Write-Host ''
    Write-Host "  请把 $logFile 的最后 40 行贴给开发者。" -ForegroundColor Yellow
    Write-Host ''
    Read-Host '按回车键关闭窗口'
    exit 1
}

Write-Host ''
Write-Host '============================================================' -ForegroundColor Green
Write-Host '  [成功] 构建完成' -ForegroundColor Green
Write-Host '============================================================' -ForegroundColor Green

$libs = Join-Path $root 'build\libs'
if (Test-Path $libs) {
    $jars = Get-ChildItem -Path $libs -Filter '*.jar' -ErrorAction SilentlyContinue
    if ($jars) {
        Write-Host ''
        Write-Host '  产物:' -ForegroundColor Green
        foreach ($j in $jars) {
            Write-Host "    $($j.Name)  ($([math]::Round($j.Length / 1KB)) KB)" -ForegroundColor Green
            Write-Host "    $($j.FullName)" -ForegroundColor DarkGray
        }
    }
}

Write-Host ''
Write-Host '  安装方法:' -ForegroundColor Cyan
Write-Host '    1. 把上面的 jar 放进 .minecraft\mods\' -ForegroundColor Cyan
Write-Host '    2. 同时安装 AE2 19.2.17 或更高版本' -ForegroundColor Cyan
Write-Host '    3. 可选：再放入 Neo ECO AE Extension' -ForegroundColor Cyan
Write-Host ''
Read-Host '按回车键关闭窗口'
exit 0
