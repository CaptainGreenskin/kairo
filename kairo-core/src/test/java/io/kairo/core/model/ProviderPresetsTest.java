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
package io.kairo.core.model;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/** Tests for {@link ProviderPresets}. */
class ProviderPresetsTest {

    private static final String DUMMY_KEY = "test-api-key-12345";

    // ── anthropic ──────────────────────────────────────────────

    @Test
    void anthropic_returnsNonNull() {
        AnthropicProvider provider = ProviderPresets.anthropic(DUMMY_KEY);
        assertNotNull(provider);
    }

    @Test
    void anthropic_hasCorrectName() {
        assertEquals("anthropic", ProviderPresets.anthropic(DUMMY_KEY).name());
    }

    @Test
    void anthropic_returnsAnthropicProviderType() {
        assertInstanceOf(AnthropicProvider.class, ProviderPresets.anthropic(DUMMY_KEY));
    }

    // ── openai ─────────────────────────────────────────────────

    @Test
    void openai_returnsNonNull() {
        OpenAIProvider provider = ProviderPresets.openai(DUMMY_KEY);
        assertNotNull(provider);
    }

    @Test
    void openai_hasCorrectName() {
        assertEquals("openai", ProviderPresets.openai(DUMMY_KEY).name());
    }

    @Test
    void openai_usesDefaultBaseUrl() throws Exception {
        OpenAIProvider provider = ProviderPresets.openai(DUMMY_KEY);
        assertEquals("https://api.openai.com", getField(provider, "baseUrl"));
    }

    // ── qwen ───────────────────────────────────────────────────

    @Test
    void qwen_returnsNonNull() {
        OpenAIProvider provider = ProviderPresets.qwen(DUMMY_KEY);
        assertNotNull(provider);
    }

    @Test
    void qwen_hasCorrectName() {
        assertEquals("openai", ProviderPresets.qwen(DUMMY_KEY).name());
    }

    @Test
    void qwen_usesCorrectBaseUrl() throws Exception {
        OpenAIProvider provider = ProviderPresets.qwen(DUMMY_KEY);
        assertEquals(
                "https://dashscope.aliyuncs.com/compatible-mode", getField(provider, "baseUrl"));
    }

    @Test
    void qwen_usesCorrectPath() throws Exception {
        OpenAIProvider provider = ProviderPresets.qwen(DUMMY_KEY);
        assertEquals("/v1/chat/completions", getField(provider, "chatCompletionsPath"));
    }

    // ── glm ────────────────────────────────────────────────────

    @Test
    void glm_returnsNonNull() {
        OpenAIProvider provider = ProviderPresets.glm(DUMMY_KEY);
        assertNotNull(provider);
    }

    @Test
    void glm_hasCorrectName() {
        assertEquals("openai", ProviderPresets.glm(DUMMY_KEY).name());
    }

    @Test
    void glm_usesCorrectBaseUrl() throws Exception {
        OpenAIProvider provider = ProviderPresets.glm(DUMMY_KEY);
        assertEquals("https://open.bigmodel.cn/api/paas", getField(provider, "baseUrl"));
    }

    @Test
    void glm_usesCorrectPath() throws Exception {
        OpenAIProvider provider = ProviderPresets.glm(DUMMY_KEY);
        assertEquals("/v4/chat/completions", getField(provider, "chatCompletionsPath"));
    }

    // ── deepseek ───────────────────────────────────────────────

    @Test
    void deepseek_returnsNonNull() {
        OpenAIProvider provider = ProviderPresets.deepseek(DUMMY_KEY);
        assertNotNull(provider);
    }

    @Test
    void deepseek_hasCorrectName() {
        assertEquals("openai", ProviderPresets.deepseek(DUMMY_KEY).name());
    }

    @Test
    void deepseek_usesCorrectBaseUrl() throws Exception {
        OpenAIProvider provider = ProviderPresets.deepseek(DUMMY_KEY);
        assertEquals("https://api.deepseek.com", getField(provider, "baseUrl"));
    }

    @Test
    void deepseek_usesCorrectPath() throws Exception {
        OpenAIProvider provider = ProviderPresets.deepseek(DUMMY_KEY);
        assertEquals("/v1/chat/completions", getField(provider, "chatCompletionsPath"));
    }

    // ── all presets create without throwing ─────────────────────

    @Test
    void allPresets_createWithoutThrowing() {
        assertDoesNotThrow(() -> ProviderPresets.anthropic(DUMMY_KEY));
        assertDoesNotThrow(() -> ProviderPresets.openai(DUMMY_KEY));
        assertDoesNotThrow(() -> ProviderPresets.qwen(DUMMY_KEY));
        assertDoesNotThrow(() -> ProviderPresets.glm(DUMMY_KEY));
        assertDoesNotThrow(() -> ProviderPresets.deepseek(DUMMY_KEY));
    }

    // ── utility class cannot be instantiated ────────────────────

    @Test
    void constructor_isPrivate() throws Exception {
        var ctor = ProviderPresets.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()));
    }

    // ── helper ──────────────────────────────────────────────────

    private static Object getField(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }
}
