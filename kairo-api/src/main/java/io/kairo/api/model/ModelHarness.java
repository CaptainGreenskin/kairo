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
package io.kairo.api.model;

import io.kairo.api.message.Msg;
import java.util.List;

/**
 * Model harness for provider-specific optimizations.
 *
 * <p>A harness adapts generic messages and configuration to the quirks and best practices of a
 * particular model family (e.g. Claude, GPT, Gemini).
 */
public interface ModelHarness {

    /**
     * Optimize message list for the target model.
     *
     * <p>May reorder, merge, or transform messages to match model expectations.
     *
     * @param messages the original message list
     * @return the optimized message list
     */
    List<Msg> optimizeMessages(List<Msg> messages);

    /**
     * Optimize model configuration for the target model.
     *
     * <p>May adjust token limits, temperature, or tool formatting.
     *
     * @param config the original configuration
     * @return the optimized configuration
     */
    ModelConfig optimizeConfig(ModelConfig config);

    /**
     * The harness name, matching the model family (e.g. "claude", "openai").
     *
     * @return the harness name
     */
    String name();
}
