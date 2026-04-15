/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.core.tool;

import io.kairo.api.tool.PermissionGuard;
import io.kairo.api.tool.ToolCategory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link PermissionGuard} that blocks dangerous operations.
 *
 * <p>Provides multi-layer protection inspired by claude-code-best's permission model:
 *
 * <ul>
 *   <li>Shell command pattern matching (rm -rf, sudo, etc.)
 *   <li>Sensitive file path protection for write/edit tools
 * </ul>
 */
public class DefaultPermissionGuard implements PermissionGuard {

    private static final Logger log = LoggerFactory.getLogger(DefaultPermissionGuard.class);

    private final List<Pattern> dangerousPatterns = new CopyOnWriteArrayList<>();
    private final List<Pattern> sensitivePathPatterns = new CopyOnWriteArrayList<>();

    /** Creates a new guard with default dangerous patterns and sensitive paths. */
    public DefaultPermissionGuard() {
        // Default dangerous shell patterns
        addDangerousPattern("\\brm\\s+-[^\\s]*r[^\\s]*f"); // rm -rf
        addDangerousPattern("\\brm\\s+-[^\\s]*f[^\\s]*r"); // rm -fr
        addDangerousPattern("\\bsudo\\b"); // sudo commands
        addDangerousPattern("\\bchmod\\s+777\\b"); // chmod 777
        addDangerousPattern("\\bmkfs\\b"); // mkfs (format disk)
        addDangerousPattern("\\bdd\\s+if="); // dd (disk destroyer)
        addDangerousPattern("\\b:\\(\\)\\{\\s*:\\|:\\s*&\\s*\\};:"); // fork bomb
        addDangerousPattern(">\\s*/dev/sd[a-z]"); // writing directly to disk
        addDangerousPattern("\\bshutdown\\b"); // shutdown
        addDangerousPattern("\\breboot\\b"); // reboot
        addDangerousPattern("\\bkill\\s+-9\\s+1\\b"); // kill init

        // Default sensitive file path patterns
        addSensitivePathPattern("/etc/passwd");
        addSensitivePathPattern("/etc/shadow");
        addSensitivePathPattern("/etc/sudoers");
        addSensitivePathPattern("/etc/crontab");
        addSensitivePathPattern("/etc/hosts");
        addSensitivePathPattern("\\.ssh/");
        addSensitivePathPattern("\\.gnupg/");
        addSensitivePathPattern("\\.aws/credentials");
        addSensitivePathPattern("\\.aws/config");
        addSensitivePathPattern("\\.kube/config");
        addSensitivePathPattern("\\.docker/config\\.json");
        addSensitivePathPattern("\\.env$");
        addSensitivePathPattern("\\.env\\.local$");
        addSensitivePathPattern("\\.env\\.production$");
        addSensitivePathPattern("\\.bashrc$");
        addSensitivePathPattern("\\.bash_profile$");
        addSensitivePathPattern("\\.profile$");
        addSensitivePathPattern("\\.zshrc$");
        addSensitivePathPattern("/root/");
    }

    @Override
    public Mono<Boolean> checkPermission(String toolName, Map<String, Object> input) {
        // Check shell commands for bash/exec/monitor tools
        if ("bash".equals(toolName) || "monitor".equals(toolName)) {
            return checkShellCommand(input);
        }

        // Check file paths for write/edit tools
        if ("write".equals(toolName) || "edit".equals(toolName)) {
            return checkFilePath(input);
        }

        return Mono.just(true);
    }

    @Override
    public Mono<Boolean> checkPermission(
            String toolName, ToolCategory category, Map<String, Object> input) {
        // Category-based checks
        if (category == ToolCategory.EXECUTION) {
            return checkShellCommand(input);
        }
        if (category == ToolCategory.FILE_AND_CODE) {
            // Only check writes, not reads
            if (toolName.toLowerCase().contains("write")
                    || toolName.toLowerCase().contains("edit")) {
                return checkFilePath(input);
            }
        }
        return checkPermission(toolName, input);
    }

    private Mono<Boolean> checkShellCommand(Map<String, Object> input) {
        Object commandObj = input.get("command");
        if (commandObj == null) {
            return Mono.just(true);
        }
        String command = commandObj.toString();
        for (Pattern pattern : dangerousPatterns) {
            if (pattern.matcher(command).find()) {
                log.warn(
                        "Blocked dangerous command matching pattern [{}]: {}",
                        pattern.pattern(),
                        command);
                return Mono.just(false);
            }
        }
        return Mono.just(true);
    }

    private Mono<Boolean> checkFilePath(Map<String, Object> input) {
        // Check common path parameter names
        String path = extractPath(input);
        if (path == null) {
            return Mono.just(true);
        }

        // Normalize the path to defeat traversal attacks (e.g. "../../etc/passwd")
        String normalizedPath;
        try {
            normalizedPath = Path.of(path).normalize().toString();
        } catch (Exception e) {
            log.warn("Invalid file path: {}", path);
            return Mono.just(false);
        }

        for (Pattern pattern : sensitivePathPatterns) {
            if (pattern.matcher(normalizedPath).find()) {
                log.warn(
                        "Blocked write to sensitive path matching [{}]: {} (normalized: {})",
                        pattern.pattern(),
                        path,
                        normalizedPath);
                return Mono.just(false);
            }
        }
        return Mono.just(true);
    }

    private static String extractPath(Map<String, Object> input) {
        for (String key : List.of("file_path", "filePath", "path", "file")) {
            Object val = input.get(key);
            if (val != null) return val.toString();
        }
        return null;
    }

    @Override
    public void addDangerousPattern(String pattern) {
        dangerousPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
    }

    @Override
    public void addSensitivePathPattern(String pathPattern) {
        sensitivePathPatterns.add(Pattern.compile(pathPattern, Pattern.CASE_INSENSITIVE));
    }
}
