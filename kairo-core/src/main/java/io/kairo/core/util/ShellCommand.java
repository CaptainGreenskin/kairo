package io.kairo.core.util;

import java.util.Locale;

/**
 * Cross-platform shell command builder. Windows uses {@code cmd /c}, Unix/macOS uses {@code /bin/sh
 * -c}.
 */
public final class ShellCommand {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private ShellCommand() {}

    /** Returns {@code true} when running on a Windows host. */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * Build a shell-wrapped command array suitable for {@link ProcessBuilder}.
     *
     * <ul>
     *   <li>Windows: {@code ["cmd", "/c", command]}
     *   <li>Unix/macOS: {@code ["/bin/sh", "-c", command]}
     * </ul>
     */
    public static String[] buildCommand(String command) {
        if (IS_WINDOWS) {
            return new String[] {"cmd", "/c", command};
        }
        return new String[] {"/bin/sh", "-c", command};
    }

    /**
     * Returns the platform-specific null device path.
     *
     * <ul>
     *   <li>Windows: {@code NUL}
     *   <li>Unix/macOS: {@code /dev/null}
     * </ul>
     */
    public static String nullDevice() {
        return IS_WINDOWS ? "NUL" : "/dev/null";
    }

    /**
     * Returns the platform-specific shell executable for interactive use.
     *
     * <ul>
     *   <li>Windows: {@code cmd.exe}
     *   <li>Unix/macOS: {@code /bin/bash} (falls back to {@code /bin/sh})
     * </ul>
     */
    public static String interactiveShell() {
        if (IS_WINDOWS) {
            return "cmd.exe";
        }
        return java.nio.file.Files.exists(java.nio.file.Path.of("/bin/bash"))
                ? "/bin/bash"
                : "/bin/sh";
    }

    /** Returns the {@code which}/{@code where} command name for locating executables. */
    public static String whichCommand() {
        return IS_WINDOWS ? "where.exe" : "which";
    }
}
