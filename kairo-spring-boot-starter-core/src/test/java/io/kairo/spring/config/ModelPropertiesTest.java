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

import io.kairo.api.model.ModelConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ModelPropertiesTest {

    @Test
    void defaultValues() {
        ModelProperties props = new ModelProperties();

        assertThat(props.getProvider()).isEqualTo("anthropic");
        assertThat(props.getApiKey()).isNull();
        assertThat(props.getBaseUrl()).isNull();
        assertThat(props.getModelName()).isEqualTo(ModelConfig.DEFAULT_MODEL);
        assertThat(props.getMaxTokens()).isEqualTo(ModelConfig.DEFAULT_MAX_TOKENS);
        assertThat(props.getTemperature()).isEqualTo(0.7);
        assertThat(props.isThinkingEnabled()).isFalse();
        assertThat(props.getThinkingBudget()).isEqualTo(10000);
    }

    @Test
    void nestedCircuitBreakerDefaults() {
        ModelProperties props = new ModelProperties();
        ModelProperties.CircuitBreaker cb = props.getCircuitBreaker();

        assertThat(cb).isNotNull();
        assertThat(cb.isEnabled()).isTrue();
        assertThat(cb.getFailureThreshold()).isEqualTo(5);
        assertThat(cb.getResetTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void setProvider() {
        ModelProperties props = new ModelProperties();
        props.setProvider("openai");
        assertThat(props.getProvider()).isEqualTo("openai");
    }

    @Test
    void setApiKey() {
        ModelProperties props = new ModelProperties();
        props.setApiKey("sk-test-key");
        assertThat(props.getApiKey()).isEqualTo("sk-test-key");
    }

    @Test
    void setBaseUrl() {
        ModelProperties props = new ModelProperties();
        props.setBaseUrl("https://open.bigmodel.cn/api/paas/v4");
        assertThat(props.getBaseUrl()).isEqualTo("https://open.bigmodel.cn/api/paas/v4");
    }

    @Test
    void setModelName() {
        ModelProperties props = new ModelProperties();
        props.setModelName("glm-4-plus");
        assertThat(props.getModelName()).isEqualTo("glm-4-plus");
    }

    @Test
    void setMaxTokens() {
        ModelProperties props = new ModelProperties();
        props.setMaxTokens(16384);
        assertThat(props.getMaxTokens()).isEqualTo(16384);
    }

    @Test
    void setTemperature() {
        ModelProperties props = new ModelProperties();
        props.setTemperature(0.0);
        assertThat(props.getTemperature()).isEqualTo(0.0);

        props.setTemperature(1.0);
        assertThat(props.getTemperature()).isEqualTo(1.0);
    }

    @Test
    void enableThinking() {
        ModelProperties props = new ModelProperties();
        props.setThinkingEnabled(true);
        props.setThinkingBudget(20000);

        assertThat(props.isThinkingEnabled()).isTrue();
        assertThat(props.getThinkingBudget()).isEqualTo(20000);
    }

    @Test
    void circuitBreakerCanBeCustomised() {
        ModelProperties props = new ModelProperties();
        ModelProperties.CircuitBreaker cb = props.getCircuitBreaker();

        cb.setEnabled(false);
        cb.setFailureThreshold(10);
        cb.setResetTimeout(Duration.ofSeconds(120));

        assertThat(props.getCircuitBreaker().isEnabled()).isFalse();
        assertThat(props.getCircuitBreaker().getFailureThreshold()).isEqualTo(10);
        assertThat(props.getCircuitBreaker().getResetTimeout()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void setCircuitBreakerReplacesNested() {
        ModelProperties props = new ModelProperties();
        ModelProperties.CircuitBreaker newCb = new ModelProperties.CircuitBreaker();
        newCb.setEnabled(false);
        newCb.setFailureThreshold(3);

        props.setCircuitBreaker(newCb);

        assertThat(props.getCircuitBreaker()).isSameAs(newCb);
        assertThat(props.getCircuitBreaker().getFailureThreshold()).isEqualTo(3);
    }
}
