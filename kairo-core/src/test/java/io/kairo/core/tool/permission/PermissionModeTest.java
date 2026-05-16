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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.tool.ToolPermission;
import io.kairo.api.tool.ToolSideEffect;
import org.junit.jupiter.api.Test;

class PermissionModeTest {

    @Test
    void defaultMode_readOnly() {
        assertThat(PermissionMode.DEFAULT.defaultPermission(ToolSideEffect.READ_ONLY))
                .isEqualTo(ToolPermission.ALLOWED);
    }

    @Test
    void defaultMode_write() {
        assertThat(PermissionMode.DEFAULT.defaultPermission(ToolSideEffect.WRITE))
                .isEqualTo(ToolPermission.ALLOWED);
    }

    @Test
    void defaultMode_systemChange() {
        assertThat(PermissionMode.DEFAULT.defaultPermission(ToolSideEffect.SYSTEM_CHANGE))
                .isEqualTo(ToolPermission.ASK);
    }

    @Test
    void planMode_readOnly() {
        assertThat(PermissionMode.PLAN.defaultPermission(ToolSideEffect.READ_ONLY))
                .isEqualTo(ToolPermission.ALLOWED);
    }

    @Test
    void planMode_write() {
        assertThat(PermissionMode.PLAN.defaultPermission(ToolSideEffect.WRITE))
                .isEqualTo(ToolPermission.DENIED);
    }

    @Test
    void planMode_systemChange() {
        assertThat(PermissionMode.PLAN.defaultPermission(ToolSideEffect.SYSTEM_CHANGE))
                .isEqualTo(ToolPermission.DENIED);
    }

    @Test
    void strictMode_readOnly() {
        assertThat(PermissionMode.STRICT.defaultPermission(ToolSideEffect.READ_ONLY))
                .isEqualTo(ToolPermission.ALLOWED);
    }

    @Test
    void strictMode_write() {
        assertThat(PermissionMode.STRICT.defaultPermission(ToolSideEffect.WRITE))
                .isEqualTo(ToolPermission.ASK);
    }

    @Test
    void strictMode_systemChange() {
        assertThat(PermissionMode.STRICT.defaultPermission(ToolSideEffect.SYSTEM_CHANGE))
                .isEqualTo(ToolPermission.ASK);
    }

    @Test
    void bypassMode_readOnly() {
        assertThat(PermissionMode.BYPASS.defaultPermission(ToolSideEffect.READ_ONLY))
                .isEqualTo(ToolPermission.ALLOWED);
    }

    @Test
    void bypassMode_write() {
        assertThat(PermissionMode.BYPASS.defaultPermission(ToolSideEffect.WRITE))
                .isEqualTo(ToolPermission.ALLOWED);
    }

    @Test
    void bypassMode_systemChange() {
        assertThat(PermissionMode.BYPASS.defaultPermission(ToolSideEffect.SYSTEM_CHANGE))
                .isEqualTo(ToolPermission.ALLOWED);
    }
}
