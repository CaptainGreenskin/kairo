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
 * Result of a user approval request.
 *
 * @param approved whether the request was approved
 * @param reason the reason for denial, or null if approved
 */
@Stable(value = "User approval result record; shape frozen since v0.4", since = "1.0.0")
public record ApprovalResult(boolean approved, String reason) {

    /** Create an approved result. */
    public static ApprovalResult allow() {
        return new ApprovalResult(true, null);
    }

    /** Create a denied result with the given reason. */
    public static ApprovalResult denied(String reason) {
        return new ApprovalResult(false, reason);
    }
}
