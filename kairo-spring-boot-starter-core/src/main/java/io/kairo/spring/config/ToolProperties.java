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
package io.kairo.spring.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tool registration configuration ({@code kairo.tool.*}).
 *
 * <p>Controls which tool categories are enabled and defines additional dangerous command patterns
 * for the permission guard. Tools are grouped into categories that can be independently toggled.
 */
@ConfigurationProperties(prefix = "kairo.tool")
public class ToolProperties {

    /**
     * Enable file operation tools (Read, Write, Edit, Glob, Grep). These tools allow the agent to
     * interact with the local filesystem.
     *
     * <p>Default: {@code true}
     */
    private boolean enableFileTools = true;

    /**
     * Enable execution tools (Bash, Monitor). These tools allow the agent to execute shell
     * commands. Use with caution in production; combine with {@link #dangerousPatterns} for safety.
     *
     * <p>Default: {@code true}
     */
    private boolean enableExecTools = true;

    /**
     * Enable information tools (AskUser). These tools allow the agent to prompt the user for input
     * during execution.
     *
     * <p>Default: {@code true}
     */
    private boolean enableInfoTools = true;

    /**
     * Enable agent collaboration tools (Spawn, SendMessage, Task*, Team*). These tools enable
     * multi-agent orchestration scenarios. Requires the {@code kairo-multi-agent} module on the
     * classpath.
     *
     * <p>Default: {@code false}
     */
    private boolean enableAgentTools = false;

    /**
     * Additional regex patterns for the permission guard to flag as dangerous. These are appended
     * to the built-in dangerous command patterns (e.g. {@code rm -rf}, {@code sudo}).
     *
     * <p>Example: {@code ["docker\\s+rm", "kubectl\\s+delete"]}
     *
     * <p>Default: empty list
     */
    private List<String> dangerousPatterns = new ArrayList<>();

    public boolean isEnableFileTools() {
        return enableFileTools;
    }

    public void setEnableFileTools(boolean enableFileTools) {
        this.enableFileTools = enableFileTools;
    }

    public boolean isEnableExecTools() {
        return enableExecTools;
    }

    public void setEnableExecTools(boolean enableExecTools) {
        this.enableExecTools = enableExecTools;
    }

    public boolean isEnableInfoTools() {
        return enableInfoTools;
    }

    public void setEnableInfoTools(boolean enableInfoTools) {
        this.enableInfoTools = enableInfoTools;
    }

    public boolean isEnableAgentTools() {
        return enableAgentTools;
    }

    public void setEnableAgentTools(boolean enableAgentTools) {
        this.enableAgentTools = enableAgentTools;
    }

    public List<String> getDangerousPatterns() {
        return dangerousPatterns;
    }

    public void setDangerousPatterns(List<String> dangerousPatterns) {
        this.dangerousPatterns = dangerousPatterns;
    }
}
