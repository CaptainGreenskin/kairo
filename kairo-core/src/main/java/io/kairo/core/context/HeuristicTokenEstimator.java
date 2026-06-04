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
package io.kairo.core.context;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import java.util.List;

/**
 * Content-type-aware token estimator with per-type coefficients.
 *
 * <p>Different content types have different token densities:
 *
 * <ul>
 *   <li>TextContent / ThinkingContent → chars/3.5 (dense prose)
 *   <li>ToolUseContent input (JSON) → chars/5.0 (sparse structure)
 *   <li>ToolResultContent → chars/4.5 (semi-structured output)
 * </ul>
 */
public class HeuristicTokenEstimator implements TokenEstimator {

    @Override
    public int estimate(List<Msg> messages) {
        int total = 0;
        for (Msg msg : messages) {
            for (Content c : msg.contents()) {
                total += estimateContent(c);
            }
        }
        return total;
    }

    private static int estimateContent(Content c) {
        if (c instanceof Content.TextContent t) {
            return Math.max(1, t.text().length() * 2 / 7);
        } else if (c instanceof Content.ThinkingContent t) {
            return Math.max(1, t.thinking().length() * 2 / 7);
        } else if (c instanceof Content.ToolUseContent t) {
            int len = t.input() != null ? t.input().toString().length() : 0;
            return Math.max(1, len / 5);
        } else if (c instanceof Content.ToolResultContent t) {
            return Math.max(1, t.content().length() * 2 / 9);
        }
        return 0;
    }
}
