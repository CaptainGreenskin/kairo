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

import org.junit.jupiter.api.Test;

class AgentPropertiesTest {

    @Test
    void defaultValues() {
        AgentProperties props = new AgentProperties();

        assertThat(props.getName()).isEqualTo("agent");
        assertThat(props.getSystemPrompt()).isEqualTo("You are a helpful coding assistant.");
        assertThat(props.getMaxIterations()).isEqualTo(50);
        assertThat(props.getTimeoutSeconds()).isEqualTo(1800);
        assertThat(props.getTokenBudget()).isEqualTo(200000);
    }

    @Test
    void setName() {
        AgentProperties props = new AgentProperties();
        props.setName("my-agent");
        assertThat(props.getName()).isEqualTo("my-agent");
    }

    @Test
    void setSystemPrompt() {
        AgentProperties props = new AgentProperties();
        props.setSystemPrompt("You are a senior software engineer.");
        assertThat(props.getSystemPrompt()).isEqualTo("You are a senior software engineer.");
    }

    @Test
    void setMaxIterations() {
        AgentProperties props = new AgentProperties();
        props.setMaxIterations(100);
        assertThat(props.getMaxIterations()).isEqualTo(100);
    }

    @Test
    void setTimeoutSeconds() {
        AgentProperties props = new AgentProperties();
        props.setTimeoutSeconds(3600);
        assertThat(props.getTimeoutSeconds()).isEqualTo(3600);
    }

    @Test
    void setTokenBudget() {
        AgentProperties props = new AgentProperties();
        props.setTokenBudget(500000);
        assertThat(props.getTokenBudget()).isEqualTo(500000);
    }

    @Test
    void maxIterationsMinimumBoundary() {
        AgentProperties props = new AgentProperties();
        props.setMaxIterations(1);
        assertThat(props.getMaxIterations()).isEqualTo(1);
    }

    @Test
    void timeoutSecondsOneDayBoundary() {
        AgentProperties props = new AgentProperties();
        props.setTimeoutSeconds(86400);
        assertThat(props.getTimeoutSeconds()).isEqualTo(86400);
    }
}
