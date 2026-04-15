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
package io.kairo.core.message;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.Map;

/**
 * Enhanced builder utilities for {@link Msg}, providing fluent chain-style construction and
 * convenient factory methods.
 *
 * <p>Usage examples:
 *
 * <pre>{@code
 * // Fluent chain style
 * Msg msg = MsgBuilder.create()
 *     .role(MsgRole.USER)
 *     .text("Hello")
 *     .image("https://example.com/img.png", "image/png")
 *     .build();
 *
 * // Convenient static factories
 * Msg user = MsgBuilder.user("Hello");
 * Msg system = MsgBuilder.system("You are a helpful assistant");
 * Msg assistant = MsgBuilder.assistant("Hi there!");
 * }</pre>
 */
public final class MsgBuilder {

    private final Msg.Builder delegate;

    private MsgBuilder() {
        this.delegate = Msg.builder();
    }

    /**
     * Create a new fluent message builder.
     *
     * @return a new MsgBuilder
     */
    public static MsgBuilder create() {
        return new MsgBuilder();
    }

    // ---- Convenience static factory methods ----

    /**
     * Create a simple user text message.
     *
     * @param text the user message text
     * @return a new Msg with role USER
     */
    public static Msg user(String text) {
        return create().role(MsgRole.USER).text(text).build();
    }

    /**
     * Create a simple system text message.
     *
     * @param text the system prompt text
     * @return a new Msg with role SYSTEM
     */
    public static Msg system(String text) {
        return create().role(MsgRole.SYSTEM).text(text).build();
    }

    /**
     * Create a simple assistant text message.
     *
     * @param text the assistant response text
     * @return a new Msg with role ASSISTANT
     */
    public static Msg assistant(String text) {
        return create().role(MsgRole.ASSISTANT).text(text).build();
    }

    /**
     * Create a tool result message.
     *
     * @param toolUseId the corresponding tool_use id
     * @param content the tool execution result
     * @param isError whether the tool execution errored
     * @return a new Msg with role TOOL
     */
    public static Msg toolResultMsg(String toolUseId, String content, boolean isError) {
        return create().role(MsgRole.TOOL).addToolResult(toolUseId, content, isError).build();
    }

    // ---- Fluent chain methods ----

    /** Set the message role. */
    public MsgBuilder role(MsgRole role) {
        delegate.role(role);
        return this;
    }

    /** Set a custom message ID. */
    public MsgBuilder id(String id) {
        delegate.id(id);
        return this;
    }

    /** Add a text content block. */
    public MsgBuilder text(String text) {
        delegate.addContent(new Content.TextContent(text));
        return this;
    }

    /** Add an image content block by URL. */
    public MsgBuilder image(String url, String mediaType) {
        delegate.addContent(new Content.ImageContent(url, mediaType, null));
        return this;
    }

    /** Add an image content block with raw bytes. */
    public MsgBuilder imageData(byte[] data, String mediaType) {
        delegate.addContent(new Content.ImageContent(null, mediaType, data));
        return this;
    }

    /** Add a tool use content block. */
    public MsgBuilder toolUse(String toolId, String toolName, Map<String, Object> input) {
        delegate.addContent(new Content.ToolUseContent(toolId, toolName, input));
        return this;
    }

    /** Add a tool result content block. */
    public MsgBuilder addToolResult(String toolUseId, String content, boolean isError) {
        delegate.addContent(new Content.ToolResultContent(toolUseId, content, isError));
        return this;
    }

    /** Add a thinking content block. */
    public MsgBuilder thinking(String thinking, int budgetTokens) {
        delegate.addContent(new Content.ThinkingContent(thinking, budgetTokens));
        return this;
    }

    /** Add arbitrary metadata. */
    public MsgBuilder metadata(String key, Object value) {
        delegate.metadata(key, value);
        return this;
    }

    /** Mark this message as verbatim-preserved (not compressible). */
    public MsgBuilder verbatimPreserved(boolean preserved) {
        delegate.verbatimPreserved(preserved);
        return this;
    }

    /** Set the source agent ID. */
    public MsgBuilder sourceAgentId(String agentId) {
        delegate.sourceAgentId(agentId);
        return this;
    }

    /**
     * Build the message, automatically estimating token count if not set.
     *
     * @return the constructed Msg
     */
    public Msg build() {
        // Auto-estimate token count based on text contents
        Msg msg = delegate.build();
        if (msg.tokenCount() == 0) {
            int estimated = estimateTokens(msg);
            Msg.Builder rebuilt =
                    Msg.builder()
                            .id(msg.id())
                            .role(msg.role())
                            .contents(msg.contents())
                            .timestamp(msg.timestamp())
                            .tokenCount(estimated)
                            .verbatimPreserved(msg.verbatimPreserved())
                            .sourceAgentId(msg.sourceAgentId());
            msg.metadata().forEach(rebuilt::metadata);
            return rebuilt.build();
        }
        return msg;
    }

    /**
     * Estimate token count for a message. Simple heuristic: ~4 characters per token for English
     * text.
     *
     * @param msg the message
     * @return estimated token count
     */
    public static int estimateTokens(Msg msg) {
        int charCount = 0;
        for (Content content : msg.contents()) {
            if (content instanceof Content.TextContent tc) {
                charCount += tc.text().length();
            } else if (content instanceof Content.ThinkingContent tc) {
                charCount += tc.thinking().length();
            } else if (content instanceof Content.ToolUseContent tu) {
                charCount += tu.toolName().length() + tu.input().toString().length();
            } else if (content instanceof Content.ToolResultContent tr) {
                charCount += tr.content().length();
            }
        }
        return Math.max(1, charCount / 4);
    }
}
