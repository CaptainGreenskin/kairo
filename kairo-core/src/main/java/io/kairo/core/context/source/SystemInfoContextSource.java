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
package io.kairo.core.context.source;

import io.kairo.api.context.ContextSource;

/**
 * Provides system and runtime information as context.
 *
 * <p>Collects OS details, Java version, and working directory. Output example:
 *
 * <pre>
 * System: macOS 15.2 aarch64
 * Java: OpenJDK 21.0.1
 * Working Directory: /Users/user/project
 * </pre>
 *
 * <p>Priority 10 — important for platform-aware reasoning, low collection cost.
 */
public class SystemInfoContextSource implements ContextSource {

    private volatile String cached;

    @Override
    public String getName() {
        return "system-info";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public String collect() {
        if (cached != null) {
            return cached;
        }
        String info = buildInfo();
        cached = info;
        return info;
    }

    private String buildInfo() {
        StringBuilder sb = new StringBuilder();

        // OS info
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "");
        String osArch = System.getProperty("os.arch", "");
        sb.append("System: ").append(osName);
        if (!osVersion.isEmpty()) {
            sb.append(" ").append(osVersion);
        }
        sb.append(" ").append(osArch);

        // Java version
        String javaVersion = System.getProperty("java.version", "unknown");
        String javaVendor = System.getProperty("java.vendor", "");
        sb.append("\nJava: ")
                .append(javaVendor.isEmpty() ? "" : javaVendor + " ")
                .append(javaVersion);

        // Working directory
        String userDir = System.getProperty("user.dir", "unknown");
        sb.append("\nWorking Directory: ").append(userDir);

        return sb.toString();
    }
}
