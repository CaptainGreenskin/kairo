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
import java.util.List;

/** Content-type-aware token estimation for {@link Msg} objects. */
public final class MsgTokens {

    private MsgTokens() {}

    /** Estimate token count for a single message. Returns at least 1. */
    public static int estimate(Msg msg) {
        int total = 0;
        for (Content content : msg.contents()) {
            if (content instanceof Content.TextContent tc) {
                total += Math.max(1, tc.text().length() * 2 / 7);
            } else if (content instanceof Content.ThinkingContent tc) {
                total += Math.max(1, tc.thinking().length() * 2 / 7);
            } else if (content instanceof Content.ToolUseContent tu) {
                int len = tu.input() != null ? tu.input().toString().length() : 0;
                total += Math.max(1, len / 5);
            } else if (content instanceof Content.ToolResultContent tr) {
                total += Math.max(1, tr.content().length() * 2 / 9);
            }
        }
        return Math.max(1, total);
    }

    /** Estimate total token count across a list of messages. */
    public static int estimate(List<Msg> messages) {
        return messages.stream().mapToInt(MsgTokens::estimate).sum();
    }
}
