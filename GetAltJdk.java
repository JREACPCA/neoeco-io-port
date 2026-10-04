import java.io.*;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

/**
 * 下载一个「替代的 JDK 21」并解压到工程内，用于判断 NeoForm 的
 * recompile 失败是否与本机 JDK 的具体构建/小版本有关。
 *
 * 背景：NeoFormRuntime 2.0.31 发布于 2024 年，当时面向 JDK 21.0.0 ~ 21.0.4。
 * 工程内置的是 Temurin 21.0.12.1（2026 年），可能在源文件读取或 javac 诊断
 * 行为上有差异。
 *
 * 用法:
 *   java GetAltJdk.java                下载默认（Oracle JDK 21.0.2 GA）
 *   java GetAltJdk.java --ms           下载 Microsoft Build of OpenJDK 21.0.8
 *   java GetAltJdk.java --url <URL>    指定其它 JDK 的 zip
 */
public class GetAltJdk {

    // 默认选 21.0.2：Oracle 官方 GA 构建，时间上最接近 NeoFormRuntime 2.0.31
    static final String JDK_2102 =
        "https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_windows-x64_bin.zip";
    static final String JDK_MS_2108 =
        "https://aka.ms/download-jdk/microsoft-jdk-21.0.8-windows-x64.zip";

    public static void main(String[] args) throws Exception {
        String url = JDK_2102;
        String tag = "jdk21-0-2";

        for (int i = 0; i < args.length; i++) {
            if ("--ms".equals(args[i])) {
                url = JDK_MS_2108;
                tag = "jdk21-ms";
            } else if ("--url".equals(args[i]) && i + 1 < args.length) {
                url = args[i + 1];
                tag = "jdk21-custom";
            }
        }

        Path base = Paths.get("").toAbsolutePath();
        Path zip = base.resolve(tag + ".zip");
        Path outDir = base.resolve(tag);

        System.out.println("== 下载替代 JDK ==");
        System.out.println("  url = " + url);
        System.out.println("  zip = " + zip);
        System.out.println("  out = " + outDir);
        System.out.println();

        // 支持代理：读取工程根目录的 proxy.txt（只写一行端口号）
        String proxyPort = null;
        Path proxyFile = base.resolve("proxy.txt");
        if (Files.exists(proxyFile)) {
            String line = new String(Files.readAllBytes(proxyFile)).trim();
            if (!line.isEmpty()) {
                proxyPort = line.split("\\s+")[0];
            }
        }

        HttpClient.Builder cb = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(25))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxyPort != null) {
            cb.proxy(ProxySelector.of(new java.net.InetSocketAddress("127.0.0.1",
                    Integer.parseInt(proxyPort))));
            System.out.println("  使用代理 127.0.0.1:" + proxyPort);
        }
        HttpClient c = cb.build();

        System.out.println("[1/3] 下载中（约 200MB，请耐心等待）...");
        HttpResponse<InputStream> r = c.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(60)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) {
            System.err.println("HTTP " + r.statusCode() + " - 下载失败");
            System.exit(1);
        }
        long expected = r.headers().firstValueAsLong("content-length").orElse(-1);
        long written = 0;
        long nextReport = 0;
        try (InputStream in = r.body();
             OutputStream os = Files.newOutputStream(zip, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                written += n;
                if (written >= nextReport) {
                    System.out.printf("      %d / %d bytes (%.0f%%)%n", written, expected,
                            expected > 0 ? written * 100.0 / expected : -1);
                    nextReport = written + (40L << 20);
                }
            }
        }
        System.out.println("      完成: " + written + " bytes");

        System.out.println("[2/3] 解压中...");
        if (Files.exists(outDir)) {
            deleteRecursively(outDir);
        }
        Files.createDirectories(outDir);
        unzip(zip, outDir);

        System.out.println("[3/3] 定位 javac.exe ...");
        Path found;
        try (var stream = Files.walk(outDir)) {
            found = stream.filter(p -> p.getFileName().toString().equals("javac.exe"))
                    .findFirst().orElse(null);
        }
        if (found == null) {
            System.err.println("解压后没找到 javac.exe");
            System.exit(1);
        }

        Path home = found.getParent().getParent();
        System.out.println();
        System.out.println("== 成功 ==");
        System.out.println("  JDK 目录: " + home);
        System.out.println();
        System.out.println("接下来运行（把路径换成上面这一行）：");
        System.out.println("  build-clean.bat \"" + home + "\"");
        System.out.println();
        System.out.println("（这个 zip 可以删掉以省空间: " + zip.getFileName() + "）");
    }

    static void unzip(Path zip, Path outDir) throws Exception {
        try (var zf = new java.util.zip.ZipFile(zip.toFile())) {
            var it = zf.entries();
            while (it.hasMoreElements()) {
                var e = it.nextElement();
                Path target = outDir.resolve(e.getName()).normalize();
                if (!target.startsWith(outDir)) {
                    continue;
                }
                if (e.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (var in = zf.getInputStream(e)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    static void deleteRecursively(Path p) throws Exception {
        try (var s = Files.walk(p)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(x -> {
                try {
                    Files.delete(x);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
