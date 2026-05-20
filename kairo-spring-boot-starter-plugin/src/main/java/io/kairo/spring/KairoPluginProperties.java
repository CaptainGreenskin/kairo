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
package io.kairo.spring;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties for the Kairo Plugin SPI auto-configuration. All properties live under {@code
 * kairo.plugin.*} in {@code application.yml} / {@code application.properties}.
 *
 * <p>Defaults assume {@code ~/.kairo/plugins/} as the data root so a Spring app behaves the same
 * way as the standalone {@code kairo-assistant} REPL.
 */
@ConfigurationProperties(prefix = "kairo.plugin")
public class KairoPluginProperties {

    /** Whether to enable the plugin auto-configuration. Defaults to true. */
    private boolean enabled = true;

    /**
     * Root directory where plugin cache + per-plugin data live. Resolves to {@code
     * ~/.kairo/plugins} when blank.
     */
    private String dataRoot = "";

    /**
     * Whether to install all 5 remote source fetchers (path / github / git-url / git-subdir / npm).
     * Set to false to bind only the local-path fetcher (useful in air-gapped Spring deployments).
     */
    private boolean enableRemoteFetchers = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDataRoot() {
        return dataRoot;
    }

    public void setDataRoot(String dataRoot) {
        this.dataRoot = dataRoot;
    }

    public boolean isEnableRemoteFetchers() {
        return enableRemoteFetchers;
    }

    public void setEnableRemoteFetchers(boolean enableRemoteFetchers) {
        this.enableRemoteFetchers = enableRemoteFetchers;
    }

    /** Resolve the data root to an absolute Path, defaulting to {@code ~/.kairo/plugins}. */
    public Path resolvedDataRoot() {
        if (dataRoot == null || dataRoot.isBlank()) {
            return Paths.get(System.getProperty("user.home"), ".kairo", "plugins");
        }
        return Paths.get(dataRoot);
    }
}
