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
package io.kairo.demo;

import io.kairo.api.hook.*;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;

/**
 * Hook handler that prints the ReAct reasoning loop to stdout with emoji-decorated markers so users
 * can follow each step in the terminal.
 */
public class LoggingHook {

    private int iteration = 0;

    @PreReasoning
    public PreReasoningEvent beforeReasoning(PreReasoningEvent event) {
        iteration++;
        System.out.println(
                "\n\uD83E\uDDE0 [Iteration "
                        + iteration
                        + " — Reasoning...] Analyzing next action");
        return event;
    }

    @PostReasoning
    public PostReasoningEvent afterReasoning(PostReasoningEvent event) {
        ModelResponse response = event.response();
        if (response == null) return event;

        for (Content content : response.contents()) {
            if (content instanceof Content.ThinkingContent tc) {
                System.out.println("\uD83D\uDCAD [Thinking] " + tc.thinking());
            } else if (content instanceof Content.ToolUseContent tu) {
                System.out.println(
                        "\uD83D\uDEE0\uFE0F  [Plan] Call tool: "
                                + tu.toolName()
                                + " with "
                                + summarizeInput(tu));
            } else if (content instanceof Content.TextContent tc) {
                System.out.println("\n\uD83D\uDCAC [Final Answer]\n" + tc.text());
            }
        }
        return event;
    }

    @PreActing
    public PreActingEvent beforeTool(PreActingEvent event) {
        System.out.println("\uD83D\uDD27 [Executing] " + event.toolName() + " ...");
        return event;
    }

    @PostActing
    public PostActingEvent afterTool(PostActingEvent event) {
        String content = event.result().content();
        boolean isError = event.result().isError();
        String icon = isError ? "\u274C" : "\u2705";
        System.out.println(icon + " [Result] " + truncate(content, 200));
        return event;
    }

    public int getIteration() {
        return iteration;
    }

    private String summarizeInput(Content.ToolUseContent tu) {
        var input = tu.input();
        if (input.containsKey("command")) {
            return "command=\"" + input.get("command") + "\"";
        }
        if (input.containsKey("path")) {
            return "path=\"" + input.get("path") + "\"";
        }
        return input.toString();
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        String trimmed = text.strip();
        if (trimmed.length() <= maxLen) return trimmed;
        return trimmed.substring(0, maxLen) + "...";
    }
}
