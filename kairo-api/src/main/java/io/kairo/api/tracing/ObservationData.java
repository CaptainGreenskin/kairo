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
package io.kairo.api.tracing;

import io.kairo.api.Stable;
import java.util.Map;
import java.util.Objects;

/**
 * Structured payload for {@link Tracer#recordObservation(Span, ObservationData)}.
 *
 * <p>Encapsulates the full set of attributes a model call / tool call / guardrail decision needs to
 * surface to Langfuse-style dashboards <em>and</em> standard OTel GenAI consumers in a single SPI
 * call. Without this record, every call site that wants Langfuse parity has to hand-roll 10+ {@code
 * span.setAttribute(...)} writes and stay in sync with the attribute-key convention; that drift is
 * exactly what this record exists to prevent.
 *
 * <p>Built via {@link #builder()}. The compact constructor normalises {@code null} level to {@link
 * Level#DEFAULT}, {@code null} metadata to {@link Map#of()}, and back-fills {@link #totalTokens}
 * from input+output when not provided.
 *
 * @since 1.4
 */
@Stable(value = "Observation SPI record; shape covers Langfuse + OTel GenAI", since = "1.4.0")
public record ObservationData(
        Type type,
        String model,
        String input,
        String output,
        Level level,
        String statusMessage,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        Double costUsd,
        Map<String, Object> metadata) {

    /** Langfuse observation type. Lowercased when written to {@code langfuse.observation.type}. */
    public enum Type {
        GENERATION,
        TOOL,
        SPAN,
        AGENT,
        EVENT,
        RETRIEVER,
        EMBEDDING
    }

    /** Langfuse observation level. Drives the dashboard error view. */
    public enum Level {
        DEBUG,
        DEFAULT,
        WARNING,
        ERROR
    }

    public ObservationData {
        Objects.requireNonNull(type, "type must not be null");
        if (level == null) level = Level.DEFAULT;
        if (metadata == null) metadata = Map.of();
        else metadata = Map.copyOf(metadata);
        if (totalTokens == 0 && (inputTokens > 0 || outputTokens > 0)) {
            totalTokens = inputTokens + outputTokens;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Type type;
        private String model;
        private String input;
        private String output;
        private Level level = Level.DEFAULT;
        private String statusMessage;
        private int inputTokens;
        private int outputTokens;
        private int totalTokens;
        private Double costUsd;
        private Map<String, Object> metadata;

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder input(String input) {
            this.input = input;
            return this;
        }

        public Builder output(String output) {
            this.output = output;
            return this;
        }

        public Builder level(Level level) {
            this.level = level;
            return this;
        }

        public Builder statusMessage(String statusMessage) {
            this.statusMessage = statusMessage;
            return this;
        }

        public Builder inputTokens(int inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(int outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder totalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder costUsd(Double costUsd) {
            this.costUsd = costUsd;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ObservationData build() {
            return new ObservationData(
                    type,
                    model,
                    input,
                    output,
                    level,
                    statusMessage,
                    inputTokens,
                    outputTokens,
                    totalTokens,
                    costUsd,
                    metadata);
        }
    }
}
