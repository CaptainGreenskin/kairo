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
package io.kairo.api.memory;

import java.time.Instant;
import java.util.Set;

/**
 * Structured query for searching memory entries.
 *
 * <p>All filter fields are nullable — null means "no constraint" for that dimension. Use the {@link
 * Builder} for convenient construction with defaults.
 *
 * @param agentId nullable — filter by agent; null means all agents
 * @param keyword nullable — text search term
 * @param queryVector nullable — vector for similarity search
 * @param from nullable — start of time range (inclusive)
 * @param to nullable — end of time range (inclusive)
 * @param tags nullable/empty — filter by tags (AND semantics)
 * @param minImportance minimum importance threshold (default 0.0)
 * @param limit maximum number of results (default 20)
 */
public record MemoryQuery(
        String agentId,
        String keyword,
        float[] queryVector,
        Instant from,
        Instant to,
        Set<String> tags,
        double minImportance,
        int limit,
        String namespace,
        String embeddingModelId,
        RankingMode rankingMode,
        FusionStrategy fusionStrategy,
        int lexicalTopK,
        int vectorTopK) {

    /** Ranking mode hint for hybrid memory retrieval. */
    public enum RankingMode {
        LEXICAL_ONLY,
        VECTOR_ONLY,
        HYBRID
    }

    /** Fusion strategy hint for combining lexical/vector candidates. */
    public enum FusionStrategy {
        RRF,
        SCORE_SUM
    }

    /** Compact constructor — applies defaults for tags. */
    public MemoryQuery {
        queryVector = queryVector == null ? null : queryVector.clone();
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        if (limit <= 0) {
            limit = 20;
        }
        if (minImportance < 0.0) {
            minImportance = 0.0;
        }
        namespace = normalizeBlankToNull(namespace);
        embeddingModelId = normalizeBlankToNull(embeddingModelId);
        rankingMode =
                rankingMode != null
                        ? rankingMode
                        : (queryVector == null ? RankingMode.LEXICAL_ONLY : RankingMode.HYBRID);
        fusionStrategy = fusionStrategy == null ? FusionStrategy.RRF : fusionStrategy;
        lexicalTopK = lexicalTopK > 0 ? lexicalTopK : limit;
        vectorTopK = vectorTopK > 0 ? vectorTopK : limit;
    }

    @Override
    public float[] queryVector() {
        return queryVector == null ? null : queryVector.clone();
    }

    /**
     * Create a new {@link Builder} for constructing a {@link MemoryQuery}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link MemoryQuery}. */
    public static class Builder {
        private String agentId;
        private String keyword;
        private float[] queryVector;
        private Instant from;
        private Instant to;
        private Set<String> tags;
        private double minImportance = 0.0;
        private int limit = 20;
        private String namespace;
        private String embeddingModelId;
        private RankingMode rankingMode;
        private FusionStrategy fusionStrategy;
        private int lexicalTopK;
        private int vectorTopK;

        private Builder() {}

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public Builder queryVector(float[] queryVector) {
            this.queryVector = queryVector;
            return this;
        }

        public Builder from(Instant from) {
            this.from = from;
            return this;
        }

        public Builder to(Instant to) {
            this.to = to;
            return this;
        }

        public Builder tags(Set<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder minImportance(double minImportance) {
            this.minImportance = minImportance;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder embeddingModelId(String embeddingModelId) {
            this.embeddingModelId = embeddingModelId;
            return this;
        }

        public Builder rankingMode(RankingMode rankingMode) {
            this.rankingMode = rankingMode;
            return this;
        }

        public Builder fusionStrategy(FusionStrategy fusionStrategy) {
            this.fusionStrategy = fusionStrategy;
            return this;
        }

        public Builder lexicalTopK(int lexicalTopK) {
            this.lexicalTopK = lexicalTopK;
            return this;
        }

        public Builder vectorTopK(int vectorTopK) {
            this.vectorTopK = vectorTopK;
            return this;
        }

        public MemoryQuery build() {
            return new MemoryQuery(
                    agentId,
                    keyword,
                    queryVector,
                    from,
                    to,
                    tags,
                    minImportance,
                    limit,
                    namespace,
                    embeddingModelId,
                    rankingMode,
                    fusionStrategy,
                    lexicalTopK,
                    vectorTopK);
        }
    }

    private static String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
