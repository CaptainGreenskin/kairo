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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolPropertiesTest {

    @Test
    void defaultValues() {
        ToolProperties props = new ToolProperties();

        assertThat(props.isEnableFileTools()).isTrue();
        assertThat(props.isEnableExecTools()).isTrue();
        assertThat(props.isEnableInfoTools()).isTrue();
        assertThat(props.isEnableAgentTools()).isFalse();
        assertThat(props.getDangerousPatterns()).isEmpty();
    }

    @Test
    void disableFileTools() {
        ToolProperties props = new ToolProperties();
        props.setEnableFileTools(false);
        assertThat(props.isEnableFileTools()).isFalse();
    }

    @Test
    void disableExecTools() {
        ToolProperties props = new ToolProperties();
        props.setEnableExecTools(false);
        assertThat(props.isEnableExecTools()).isFalse();
    }

    @Test
    void disableInfoTools() {
        ToolProperties props = new ToolProperties();
        props.setEnableInfoTools(false);
        assertThat(props.isEnableInfoTools()).isFalse();
    }

    @Test
    void enableAgentTools() {
        ToolProperties props = new ToolProperties();
        props.setEnableAgentTools(true);
        assertThat(props.isEnableAgentTools()).isTrue();
    }

    @Test
    void addDangerousPatterns() {
        ToolProperties props = new ToolProperties();
        props.setDangerousPatterns(List.of("docker\\s+rm", "kubectl\\s+delete"));

        assertThat(props.getDangerousPatterns()).hasSize(2);
        assertThat(props.getDangerousPatterns()).contains("docker\\s+rm", "kubectl\\s+delete");
    }

    @Test
    void dangerousPatternsMutable() {
        ToolProperties props = new ToolProperties();
        props.getDangerousPatterns().add("rm\\s+-rf");

        assertThat(props.getDangerousPatterns()).containsExactly("rm\\s+-rf");
    }

    @Test
    void allToolsCategoryToggle() {
        ToolProperties props = new ToolProperties();
        props.setEnableFileTools(false);
        props.setEnableExecTools(false);
        props.setEnableInfoTools(false);

        assertThat(props.isEnableFileTools()).isFalse();
        assertThat(props.isEnableExecTools()).isFalse();
        assertThat(props.isEnableInfoTools()).isFalse();
        assertThat(props.isEnableAgentTools()).isFalse();
    }
}
