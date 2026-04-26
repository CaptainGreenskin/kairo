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
package io.kairo.security.pii;

import io.kairo.api.guardrail.GuardrailPhase;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Configuration for {@link PiiRedactionPolicy}.
 *
 * @param patterns compiled {@link Pattern} → replacement string map; evaluated in iteration order
 * @param phases the guardrail phases at which redaction is performed
 * @param order the {@link io.kairo.api.guardrail.GuardrailPolicy#order()} value to apply
 */
public record PiiRedactionConfig(
        Map<Pattern, String> patterns, Set<GuardrailPhase> phases, int order) {

    /** Default: all shipped {@link PiiPattern}s, POST_MODEL + POST_TOOL phases, order 100. */
    public static PiiRedactionConfig defaults() {
        var patterns = new java.util.LinkedHashMap<Pattern, String>();
        for (PiiPattern p : PiiPattern.values()) {
            patterns.put(p.pattern(), p.replacement());
        }
        return new PiiRedactionConfig(
                Map.copyOf(patterns),
                EnumSet.of(GuardrailPhase.POST_MODEL, GuardrailPhase.POST_TOOL),
                100);
    }

    /**
     * Build a config from a subset of shipped patterns.
     *
     * @param patterns the built-in patterns to enable
     */
    public static PiiRedactionConfig of(PiiPattern... patterns) {
        var map = new java.util.LinkedHashMap<Pattern, String>();
        for (PiiPattern p : patterns) {
            map.put(p.pattern(), p.replacement());
        }
        return new PiiRedactionConfig(
                Map.copyOf(map),
                EnumSet.of(GuardrailPhase.POST_MODEL, GuardrailPhase.POST_TOOL),
                100);
    }

    /** Replace the phases this config targets (returns a new record). */
    public PiiRedactionConfig withPhases(GuardrailPhase... phases) {
        return new PiiRedactionConfig(patterns, EnumSet.copyOf(List.of(phases)), order);
    }

    /** Replace the order (returns a new record). */
    public PiiRedactionConfig withOrder(int order) {
        return new PiiRedactionConfig(patterns, phases, order);
    }
}
