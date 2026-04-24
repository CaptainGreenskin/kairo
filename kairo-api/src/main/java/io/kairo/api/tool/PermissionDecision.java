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
package io.kairo.api.tool;

import io.kairo.api.Stable;

/**
 * Structured permission decision with reason and policy context. Allows agents to understand WHY a
 * tool was denied and adapt accordingly.
 */
@Stable(value = "Permission decision record; shape frozen since v0.7", since = "1.0.0")
public record PermissionDecision(
        boolean allowed,
        String reason, // nullable when allowed=true
        String policyId // nullable, identifies which policy made the decision
        ) {
    public static PermissionDecision allow() {
        return new PermissionDecision(true, null, null);
    }

    public static PermissionDecision deny(String reason, String policyId) {
        return new PermissionDecision(false, reason, policyId);
    }
}
