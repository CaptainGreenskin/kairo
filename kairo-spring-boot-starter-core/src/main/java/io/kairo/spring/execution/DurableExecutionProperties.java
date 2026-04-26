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
package io.kairo.spring.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Kairo durable execution.
 *
 * <p>Prefix: {@code kairo.execution.durable}
 *
 * @since v0.8
 */
@ConfigurationProperties(prefix = "kairo.execution.durable")
public class DurableExecutionProperties {

    /** Supported durable execution store types. */
    public enum StoreType {
        MEMORY,
        JDBC
    }

    /** Whether durable execution is enabled. */
    private boolean enabled = false;

    /** Store type: MEMORY for testing or JDBC for production. */
    private StoreType storeType = StoreType.MEMORY;

    /** Whether to attempt recovery of pending executions on startup. */
    private boolean recoveryOnStartup = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public StoreType getStoreType() {
        return storeType;
    }

    public void setStoreType(StoreType storeType) {
        this.storeType = storeType;
    }

    public boolean isRecoveryOnStartup() {
        return recoveryOnStartup;
    }

    public void setRecoveryOnStartup(boolean recoveryOnStartup) {
        this.recoveryOnStartup = recoveryOnStartup;
    }
}
