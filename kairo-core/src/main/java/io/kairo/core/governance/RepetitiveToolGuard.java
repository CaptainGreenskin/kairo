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
package io.kairo.core.governance;

import io.kairo.api.Experimental;
import io.kairo.api.hook.HookHandler;
import io.kairo.api.hook.HookPhase;
import io.kairo.api.hook.HookResult;
import io.kairo.api.hook.PostReasoningEvent;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelResponse;
import java.util.HashSet;
import java.util.Set;

/**
 * Detects when the agent calls the same tool in N consecutive turns and injects a hint to try a
 * different approach.
 *
 * <p>Fires at most once per tool per session to avoid spamming.
 *
 * <p>Phase: {@link HookPhase#POST_REASONING}.
 */
@Experimental("Governance guard; v0.12")
public final class RepetitiveToolGuard {

    private final int threshold;
    private final boolean interactive;
    private final Set<String> firedFor = new HashSet<>();
    private String lastTool;
    private int consecutiveCount;

    public RepetitiveToolGuard() {
        this(4, false);
    }

    public RepetitiveToolGuard(int threshold) {
        this(threshold, false);
    }

    public RepetitiveToolGuard(int threshold, boolean interactive) {
        this.threshold = threshold;
        this.interactive = interactive;
    }

    @HookHandler(HookPhase.POST_REASONING)
    public HookResult<PostReasoningEvent> onPostReasoning(PostReasoningEvent event) {
        if (interactive) {
            return HookResult.proceed(event);
        }

        ModelResponse response = event.response();
        if (response == null || response.contents() == null) {
            return HookResult.proceed(event);
        }

        String toolName =
                response.contents().stream()
                        .filter(Content.ToolUseContent.class::isInstance)
                        .map(c -> ((Content.ToolUseContent) c).toolName())
                        .findFirst()
                        .orElse(null);

        if (toolName == null) {
            lastTool = null;
            consecutiveCount = 0;
            return HookResult.proceed(event);
        }

        if (toolName.equals(lastTool)) {
            consecutiveCount++;
        } else {
            lastTool = toolName;
            consecutiveCount = 1;
        }

        if (consecutiveCount >= threshold && !firedFor.contains(toolName)) {
            firedFor.add(toolName);
            String message =
                    "You've called '"
                            + toolName
                            + "' "
                            + consecutiveCount
                            + " times in a row. Consider a different approach.";
            return HookResult.inject(event, Msg.of(MsgRole.USER, message), "RepetitiveToolGuard");
        }

        return HookResult.proceed(event);
    }

    public void reset() {
        lastTool = null;
        consecutiveCount = 0;
        firedFor.clear();
    }
}
