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
package io.kairo.api.message;

import io.kairo.api.Stable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Unified message type for the agent runtime conversation.
 *
 * <p>A message carries one or more {@link Content} blocks, supports metadata, and tracks token
 * usage. Messages marked as {@code verbatimPreserved} follow the "Facts First" principle and will
 * not be compressed during context compaction.
 *
 * <p>Use the {@link Builder} to construct instances:
 *
 * <pre>{@code
 * Msg msg = Msg.builder()
 *     .role(MsgRole.USER)
 *     .addContent(new Content.TextContent("Hello"))
 *     .build();
 * }</pre>
 */
@Stable(value = "Core message type; shape frozen since v0.1", since = "1.0.0")
public class Msg {

    private final String id;
    private final MsgRole role;
    private final List<Content> contents;
    private final Map<String, Object> metadata;
    private final Instant timestamp;
    private final int tokenCount;
    private final boolean verbatimPreserved;
    private final String sourceAgentId;

    private Msg(Builder builder) {
        this.id = builder.id;
        this.role = Objects.requireNonNull(builder.role, "role must not be null");
        this.contents = List.copyOf(builder.contents);
        this.metadata = Map.copyOf(builder.metadata);
        this.timestamp = builder.timestamp;
        this.tokenCount = builder.tokenCount;
        this.verbatimPreserved = builder.verbatimPreserved;
        this.sourceAgentId = builder.sourceAgentId;
    }

    /** Unique message identifier. */
    public String id() {
        return id;
    }

    /** The role of the message sender. */
    public MsgRole role() {
        return role;
    }

    /** Content blocks in this message. */
    public List<Content> contents() {
        return contents;
    }

    /** Arbitrary metadata attached to this message. */
    public Map<String, Object> metadata() {
        return metadata;
    }

    /** When this message was created. */
    public Instant timestamp() {
        return timestamp;
    }

    /** Estimated token count for this message. */
    public int tokenCount() {
        return tokenCount;
    }

    /** Whether this message is marked as verbatim (not compressible). */
    public boolean verbatimPreserved() {
        return verbatimPreserved;
    }

    /** The ID of the agent that produced this message (may be null). */
    public String sourceAgentId() {
        return sourceAgentId;
    }

    /** Convenience: extract the first {@link Content.TextContent} text, or empty string. */
    public String text() {
        return contents.stream()
                .filter(Content.TextContent.class::isInstance)
                .map(Content.TextContent.class::cast)
                .map(Content.TextContent::text)
                .findFirst()
                .orElse("");
    }

    /** Create a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Shortcut to build a simple text message.
     *
     * @param role the message role
     * @param text the text content
     * @return a new Msg
     */
    public static Msg of(MsgRole role, String text) {
        return builder().role(role).addContent(new Content.TextContent(text)).build();
    }

    /** Builder for {@link Msg}. */
    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private MsgRole role;
        private final List<Content> contents = new ArrayList<>();
        private final Map<String, Object> metadata = new HashMap<>();
        private Instant timestamp = Instant.now();
        private int tokenCount;
        private boolean verbatimPreserved;
        private String sourceAgentId;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder role(MsgRole role) {
            this.role = role;
            return this;
        }

        public Builder addContent(Content content) {
            this.contents.add(content);
            return this;
        }

        public Builder contents(List<Content> contents) {
            this.contents.clear();
            this.contents.addAll(contents);
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder tokenCount(int tokenCount) {
            this.tokenCount = tokenCount;
            return this;
        }

        public Builder verbatimPreserved(boolean verbatimPreserved) {
            this.verbatimPreserved = verbatimPreserved;
            return this;
        }

        public Builder sourceAgentId(String sourceAgentId) {
            this.sourceAgentId = sourceAgentId;
            return this;
        }

        public Msg build() {
            return new Msg(this);
        }
    }

    @Override
    public String toString() {
        return "Msg{id='" + id + "', role=" + role + ", contents=" + contents.size() + "}";
    }
}
