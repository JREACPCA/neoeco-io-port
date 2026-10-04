# 构建说明

本工程目标是 **Minecraft 1.21.1 + NeoForge + JDK 21**。

---

## 1. 依赖版本（已核实）

| 组件 | 版本 | 来源 |
|---|---|---|
| Minecraft | `1.21.1` | — |
| NeoForge | `21.1.252` | NeoForge 官方 MDK `MDK-1.21.1-ModDevGradle` 的 `gradle.properties` |
| ModDevGradle | `2.0.148` | 同上 |
| Parchment | `1.21.1` / `2024.11.17` | 同上 |
| Gradle | `8.10.2` | 工程内 `gradle-8.10.2/` |
| JDK | `21`（Temurin 21.0.12.1） | MC 1.21.1 官方要求 Java 21；工程内 `jdk21/` |
| AE2 | `19.2.18` | Maven Central：`org.appliedenergistics:appliedenergistics2` |

> AE2 在 Maven Central 上是公开的，**不需要**任何第三方仓库。
> 注意旧资料里常见的 `appeng:appliedenergistics2-neoforge` 是**已停更的旧坐标**
> （停在 `19.0.4-alpha`，2024-07），1.21.1 不要用它。

AE2 的依赖声明：

```groovy
compileOnly "org.appliedenergistics:appliedenergistics2:${ae2_version}"
runtimeOnly "org.appliedenergistics:appliedenergistics2:${ae2_version}"
```

> ⚠️ **必须用完整 jar，不要用 `:api` 分类器。**
> AE2 的 `-api.jar` 只含 `appeng.api.*` 等公开接口，**不含**
> `IOPortBlockEntity`、`AEBaseBlock`、`AEBaseEntityBlock`、`UpgradeableMenu`、
> `MenuTypeBuilder` 等实现类。本模组继承了 `IOPortBlockEntity`，
> 所以必须依赖完整构件。（已用 `javap` 对官方 jar 逐类核实。）

---

## 2. 快速开始

### 双击 `build.bat` 即可

工程**已自带完整的构建工具链**，你不需要在系统里装 JDK 或 Gradle：

| 目录 | 内容 | 大小 |
|---|---|---|
| `jdk21/` | Adoptium Temurin JDK 21 便携版 | ~328 MB |
| `gradle-8.10.2/` | Gradle 8.10.2 发行版 | ~130 MB |

`build.bat` 会自动：

1. 优先使用工程内的 `jdk21\`，找不到才回退到系统 JDK 21
2. 优先使用工程内的 `gradle-8.10.2\bin\gradle.bat`
3. 把 `GRADLE_USER_HOME` 指向工程内的 `.gradle-home\`（避免写用户主目录失败）
4. 把完整日志**同时**打印到屏幕并写入 `build-log.txt`
5. **无论成功失败都会停在窗口里等你按键**，不会一闪而过

> 首次构建会下载 NeoForge 并反编译 Minecraft，**通常需要 10～30 分钟**。
> 期间可能长时间没有输出，属于正常现象，不要关窗口。

### 关于 Gradle wrapper

工程内**没有** `gradlew`。原因是本工程的绝对路径含中文
（`F:\DEEPSEEK工作区\...`），而 `gradle-wrapper` 会把
`distributionUrl` 当 URI 解析，遇到非 ASCII 路径会直接抛
`URISyntaxException: Illegal character in path`——这是 Gradle wrapper 的已知限制。
所以这里改为**直接调用内置的 Gradle**，绕开该问题。

如果你把工程移到纯 ASCII 路径下，想用标准的 wrapper，可以：

```bat
gradle-8.10.2\bin\gradle.bat wrapper --gradle-version 8.10.2
```

### 手动构建（等价写法）

```bat
set JAVA_HOME=%~dp0jdk21
set GRADLE_USER_HOME=%~dp0.gradle-home
gradle-8.10.2\bin\gradle.bat build
```

产物：`build\libs\neoeco_io-1.0.0.jar`

其它常用任务：

```bat
gradle.bat runClient      :: 启动开发客户端
gradle.bat runServer      :: 启动开发服务端
gradle.bat runData        :: 运行数据生成
gradle.bat clean
```

---

## 3. 网络要求与代理

构建需要联网下载两部分：

| 内容 | 来源 | 是否可镜像 |
|---|---|---|
| AE2、Parchment、Gradle 插件 | Maven Central / plugins.gradle.org | ✅ |
| **NeoForge + NeoForm 工具链** | `maven.neoforged.net` | ⚠️ 部分可镜像，见下 |

### 代理

在工程根目录新建一个 `proxy.txt`，里面**只写一行端口号**：

```
7890
```

`build.bat` 会自动读取并加上 `-Dhttps.proxyHost=127.0.0.1` 等参数。

### 镜像（可选，有局限）

[`init-mirrors.gradle`](init-mirrors.gradle) 会把 NeoForge 仓库重定向到
BMCLAPI 镜像 `https://bmclapi2.bangbang93.com/maven`：

```bat
gradle-8.10.2\bin\gradle.bat --init-script init-mirrors.gradle build
```

> ⚠️ **这个镜像对本工程只解决一半问题。** 实测 BMCLAPI 上有
> `neoforge-21.1.252-userdev.jar`、`neoform`、`mergetool`，
> 但**没有** `neoform-runtime` 和 `minecraft-dependencies`——
> 而这两个恰恰是必需的。如果你的网络能直连 `maven.neoforged.net`，
> **不要**用镜像脚本，直接用官方源最可靠。

---

## 4. 已知构建阻塞：`neoform-runtime`

> **这一节很重要。** 本工程在**无法访问 `maven.neoforged.net` 的网络**里
> 无法完成编译。原因不是代码问题，而是缺两个构件。

```
Could not resolve net.neoforged:neoform-runtime:2.0.31.
> Could not GET 'https://maven.neoforged.net/releases/net/neoforged/neoform-runtime/2.0.31/...pom'
   > Connect to maven.neoforged.net:443 failed: Connection timed out
```

NeoFormRuntime 负责「反编译 Minecraft → 打 NeoForge 补丁 → 重映射到官方命名」，
是生成编译用 Minecraft 类的必要工具。实测可获取性：

| 构件 | Maven Central | BMCLAPI 镜像 | 官方源 |
|---|---|---|---|
| `net.neoforged:neoform-runtime:2.0.31` | ❌ 无 | ❌ 404 | ⛔ 连不上 |
| `net.neoforged:minecraft-dependencies:1.21.1` | ❌ 无 | ❌ 404 | ⛔ 连不上 |
| `net.neoforged:neoforge:21.1.252` | ❌ 无 | ✅ 有 | ⛔ 连不上 |
| `net.neoforged:neoform` / `mergetool` | ❌ 无 | ✅ 有 | ⛔ 连不上 |

> Maven Central 的 `net/neoforged/` 组下只有 `AutoRenamingTool`、`srtutils`、
> `installertools` 等，**没有** `neoform-runtime`。
> 阿里云 / 腾讯云 / 华为云 / izzel / firstdark / modmaven / BlameJared /
> shedaniel / jitpack 也全部 404。GitHub Packages 返回 401（需 token）。

**解决办法：只要构建机能访问 `maven.neoforged.net`，`build.bat` 就能直接跑通。**

---

## 5. 已知环境问题：NeoForm `recompile` 失败

> **这是本工程目前唯一的实际阻塞点，而且与本模组代码无关。**

### 现象

```
***  Started working on recompile
     110 items on the compile classpath
     0 items on the sourcepath
     Compiling 5364 source files
ERROR Line: 1, ..net.minecraft.world.ticks.package-info...: null in /net/minecraft/world/ticks/package-info.java
Node action for recompile failed
Caused by: java.io.IOException: Compilation failed
     at net.neoforged.neoform.runtime.actions.RecompileSourcesActionWithJDK.run(RecompileSourcesActionWithJDK.java:77)
```

`recompile` 是 NeoForm 把打完补丁的 Minecraft 源码编成 class 的阶段。
`null` 表示 NeoFormRuntime **没有把 javac 的真实诊断回传**（`problems.json` 是空的 `[]`）。

### 已确认的事实

**这是一个环境问题，不是本工程的问题。** 判据是隔离实验：
一个从 NeoForge 官方生成器出来的、**只有一个 `@Mod` 类、零额外依赖**的最小工程
（见 `../stock-mdk-test`），在**完全相同的配置**下，以**完全相同的方式**失败于
同一阶段、同一文件、同一行。

### 已排除的原因

| 排除项 | 依据 |
|---|---|
| 本模组代码 | 最小 `@Mod` 工程同样失败 |
| 中文 / 非 ASCII 路径 | 实测 `javac` 编译中文目录下的源文件退出码为 0 |
| 磁盘空间 | C: 剩 44GB，F: 剩 278GB |
| 堆内存 | `-Xmx4G`，且失败发生在 `Compiling` 之后 |
| Maven 依赖缺失 | `Loaded 145 artifacts`，之前各阶段全部 `DONE` |
| **NeoForm 缓存损坏** | 清除 `caches/neoformruntime` 后流水线**完整重跑**（`decompile` 32s 等均为实跑），**仍然同样失败** |
| `package-info.java` 本身 | 实测空文件、仅包声明两种情形 `javac` 均返回 0 |
| 网络 | 代理 `127.0.0.1:7890` 可用，`maven.neoforged.net` 返回 200 |

### 嫌疑最大的原因：JDK 版本

NeoFormRuntime **2.0.31** 发布于 2024 年，当时面向 JDK 21.0.0 ~ 21.0.4；
本工程内置的是 **Temurin 21.0.12.1**（2026 年）。新版 JDK 在源文件读取或诊断
行为上可能有差异，导致 `package-info.java` 解析失败、且诊断消息丢失。

**验证方法**：换一个 JDK 21（不同厂商或较旧小版本）后重跑。

```bat
REM 下载一个替代 JDK（Microsoft Build of OpenJDK 21）
jdk21\bin\java GetAltJdk.java

REM 用它重跑
build-clean.bat "路径\到\jdk21-alt\<解压出的目录>"
```

**如果换 JDK 无效**，用调试模式抓取真实报错：

```bat
cd ..\stock-mdk-test
run-debug.bat
```

`--debug` 级别通常会打印被 NeoForm 吞掉的 javac 诊断，脚本会自动筛出
`error:`、`package-info`、`-encoding` 等相关行。

---

## 6. 受限环境下的额外坑

如果你在**不允许写入用户主目录**的环境里构建，Gradle 会因为无法把
`native-platform.dll` 解压到 `~/.gradle` 而报：

```
Could not initialize native services.
> Failed to load native library 'native-platform.dll' for Windows 11 amd64.
```

解决办法是把 Gradle 用户目录指到可写位置（`build.bat` 已经自动这么做了）：

```powershell
$env:GRADLE_USER_HOME = 'D:\gradle-home'
gradle-8.10.2\bin\gradle.bat build
```

另外已关闭文件系统监视，避免原生监视器报权限错误：

```properties
# gradle.properties
org.gradle.vfs.watch=false
```

---

## 7. 重新生成资源

方块贴图、物品贴图、界面贴图与 logo：

```bash
python tools/gen_textures.py .
```

方块状态 JSON（48 个变体，含 AE2 的 `spin` 与 `ae2:z` 属性）：

```bash
python tools/gen_blockstate.py .
```

需要 `Pillow`。
