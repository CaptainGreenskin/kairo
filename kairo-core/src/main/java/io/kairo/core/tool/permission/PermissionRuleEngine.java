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
package io.kairo.core.tool.permission;

import io.kairo.api.tool.ToolPermission;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluates an ordered list of {@link PermissionRule}s against a tool invocation.
 *
 * <p>Rules are evaluated in order; the first matching rule's permission is returned. If no rule
 * matches, {@link Optional#empty()} is returned so the caller can fall through to mode-based
 * defaults.
 */
public final class PermissionRuleEngine {

    private final List<PermissionRule> rules;

    public PermissionRuleEngine(List<PermissionRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * Resolve permission for a tool invocation by evaluating rules in order.
     *
     * @param toolName the tool name
     * @param args the tool arguments
     * @return the first matching rule's permission, or empty if no rule matches
     */
    public Optional<ToolPermission> resolve(String toolName, Map<String, Object> args) {
        for (PermissionRule rule : rules) {
            if (rule.matches(toolName, args)) {
                return Optional.of(rule.permission());
            }
        }
        return Optional.empty();
    }

    /** Returns the number of rules in this engine. */
    public int ruleCount() {
        return rules.size();
    }
}
