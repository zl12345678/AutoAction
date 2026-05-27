package com.auto.input.interception;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves interception.dll and records why Interception is unavailable.
 */
public final class InterceptionBootstrap {
    private static final AtomicReference<String> lastDiagnostic = new AtomicReference<>("未探测");
    private static volatile Path configuredHome;

    private InterceptionBootstrap() {
    }

    public static void configureHome(String interceptionHome) {
        if (interceptionHome == null || interceptionHome.isBlank()) {
            configuredHome = null;
            return;
        }
        configuredHome = Paths.get(interceptionHome.trim());
    }

    public static String lastDiagnostic() {
        return lastDiagnostic.get();
    }

    static List<Path> candidateDllPaths() {
        List<Path> candidates = new ArrayList<>();
        if (configuredHome != null) {
            candidates.add(configuredHome.resolve(Paths.get("library", "x64", "interception.dll")));
            candidates.add(configuredHome.resolve("interception.dll"));
        }
        String envHome = System.getenv("INTERCEPTION_HOME");
        if (envHome != null && !envHome.isBlank()) {
            Path home = Paths.get(envHome.trim());
            candidates.add(home.resolve(Paths.get("library", "x64", "interception.dll")));
            candidates.add(home.resolve("interception.dll"));
        }
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        candidates.add(userDir.resolve(Paths.get("tools", "interception", "interception.dll")));
        candidates.add(Paths.get("tools", "interception", "interception.dll").toAbsolutePath().normalize());
        return candidates;
    }

    public static Path resolveDll() {
        for (Path candidate : candidateDllPaths()) {
            if (Files.isRegularFile(candidate)) {
                lastDiagnostic.set("已找到 DLL: " + candidate.toAbsolutePath());
                return candidate.toAbsolutePath();
            }
        }
        lastDiagnostic.set(
                "未找到 interception.dll。已检查: "
                        + String.join("; ", candidateDllPaths().stream().map(Path::toString).toList())
        );
        return null;
    }

    static void markDriverMissing() {
        lastDiagnostic.set(
                "DLL 已加载，但 interception_create_context 返回 null（内核驱动未加载，无法打开 \\\\.\\interception00）。"
                        + " 请在【普通】PowerShell 执行: cd \"D:\\Desktop\\Interception\\command line installer\"; .\\install-interception.exe /install"
                        + " → UAC 点是 → 【重启电脑】→ 运行 tools/interception/install-check.ps1 自检。"
        );
    }

    static void markLoadFailure(Throwable error) {
        lastDiagnostic.set("DLL 加载失败: " + error.getClass().getSimpleName() + " — " + error.getMessage());
    }
}
