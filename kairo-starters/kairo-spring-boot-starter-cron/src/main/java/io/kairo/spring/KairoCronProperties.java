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

/** {@code kairo.cron.*} configuration for the cron auto-configuration. */
@ConfigurationProperties(prefix = "kairo.cron")
public class KairoCronProperties {

    /** Enable the cron scheduler auto-configuration. Defaults to true. */
    private boolean enabled = true;

    /**
     * Path to the durable tasks JSON file. When blank, defaults to {@code
     * ~/.kairo/cron/tasks.json}.
     */
    private String storePath = "";

    /** IANA zone id for cron evaluation; defaults to the system zone. */
    private String zone = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStorePath() {
        return storePath;
    }

    public void setStorePath(String storePath) {
        this.storePath = storePath;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public Path resolvedStorePath() {
        if (storePath == null || storePath.isBlank()) {
            return Paths.get(System.getProperty("user.home"), ".kairo", "cron", "tasks.json");
        }
        return Paths.get(storePath);
    }
}
