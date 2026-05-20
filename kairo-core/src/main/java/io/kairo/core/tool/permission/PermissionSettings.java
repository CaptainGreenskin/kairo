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
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated permission configuration loaded from one or more config files.
 *
 * @param mode the permission mode, or null to inherit from a lower-priority layer
 * @param rules ordered permission rules (deny rules first, then allow rules)
 */
public record PermissionSettings(PermissionMode mode, List<PermissionRule> rules) {

    public PermissionSettings {
        rules = List.copyOf(rules);
    }

    /** Returns empty settings with no mode and no rules. */
    public static PermissionSettings defaults() {
        return new PermissionSettings(null, List.of());
    }

    /**
     * Merge with a higher-priority settings layer.
     *
     * <p>Mode: higher wins if non-null. Rules: all deny rules first (higher then this), then all
     * allow rules (higher then this). This ensures deny-before-allow semantics across layers.
     *
     * @param higher the higher-priority settings
     * @return merged settings
     */
    public PermissionSettings merge(PermissionSettings higher) {
        PermissionMode effectiveMode = higher.mode() != null ? higher.mode() : this.mode;

        List<PermissionRule> higherDeny = new ArrayList<>();
        List<PermissionRule> higherAllow = new ArrayList<>();
        for (PermissionRule r : higher.rules()) {
            if (r.permission() == ToolPermission.DENIED) {
                higherDeny.add(r);
            } else {
                higherAllow.add(r);
            }
        }

        List<PermissionRule> thisDeny = new ArrayList<>();
        List<PermissionRule> thisAllow = new ArrayList<>();
        for (PermissionRule r : this.rules()) {
            if (r.permission() == ToolPermission.DENIED) {
                thisDeny.add(r);
            } else {
                thisAllow.add(r);
            }
        }

        List<PermissionRule> merged = new ArrayList<>();
        merged.addAll(higherDeny);
        merged.addAll(thisDeny);
        merged.addAll(higherAllow);
        merged.addAll(thisAllow);

        return new PermissionSettings(effectiveMode, merged);
    }
}
