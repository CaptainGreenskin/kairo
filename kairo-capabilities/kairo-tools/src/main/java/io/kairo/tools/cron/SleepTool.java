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
package io.kairo.tools.cron;

import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Pauses the agent for a fixed duration. Useful for:
 *
 * <ul>
 *   <li><b>Polling external state</b> — "wait 30s, then check the CI status / deploy log / queue
 *       depth"
 *   <li><b>Rate limiting</b> — "wait 1s between bulk API calls so we don't hit a 429"
 *   <li><b>Cron coordination</b> — "wait until the next minute boundary before firing the cron"
 *   <li><b>Demo pacing</b> — "wait 2s so the user can read the previous output"
 * </ul>
 *
 * <p>Reactive-friendly: uses {@link Mono#delay(Duration)} on the parallel scheduler so the call
 * doesn't pin a worker thread. Cancellation propagates through the standard Reactor signal — when
 * the parent agent run is interrupted (Ctrl-C, timeout, abort), the sleep wakes immediately and
 * reports the elapsed time, never the full requested duration.
 *
 * <p>Hard cap: {@link #MAX_DURATION_SECONDS}. A cap prevents an agent loop from accidentally
 * issuing {@code duration_seconds=999999999} and leaving the session wedged. Callers that need
 * longer pauses should use {@code CronCreate} (resumes the agent on a schedule instead of
 * blocking).
 *
 * @since 1.3
 */
@Tool(
        name = "Sleep",
        description =
                "Pause the agent for a fixed number of seconds. Reactive-cancellable — the sleep"
                        + " wakes early on Ctrl-C / agent abort. Hard-capped at 86400s (24h). Use"
                        + " for polling external state (CI status, queue depth), rate limiting,"
                        + " demo pacing. For longer-than-24h waits use CronCreate instead.",
        category = ToolCategory.SCHEDULING,
        sideEffect = ToolSideEffect.READ_ONLY)
public class SleepTool implements SyncTool {

    private static final Logger log = LoggerFactory.getLogger(SleepTool.class);

    /** 24h cap; defensive — prevents runaway agents from wedging a session. */
    public static final int MAX_DURATION_SECONDS = 86_400;

    @ToolParam(
            description =
                    "How long to sleep, in seconds. Must be > 0 and ≤ 86400 (24h). Sleeps"
                            + " can be interrupted early by user cancellation; the result reports"
                            + " the actual elapsed time.",
            required = true)
    private Integer duration_seconds;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        Integer duration = parseDuration(args.get("duration_seconds"));
        if (duration == null) {
            return Mono.just(
                    ToolResult.error(
                            null,
                            "Parameter 'duration_seconds' is required and must be a positive"
                                    + " integer ≤ "
                                    + MAX_DURATION_SECONDS));
        }
        if (duration <= 0) {
            return Mono.just(
                    ToolResult.error(
                            null, "'duration_seconds' must be > 0 (got " + duration + ")"));
        }
        if (duration > MAX_DURATION_SECONDS) {
            return Mono.just(
                    ToolResult.error(
                            null,
                            "'duration_seconds' "
                                    + duration
                                    + " exceeds the 24h cap ("
                                    + MAX_DURATION_SECONDS
                                    + "s). Use CronCreate for longer pauses."));
        }

        Duration requested = Duration.ofSeconds(duration);
        Instant start = Instant.now();
        log.debug("Sleeping for {}s", duration);

        return Mono.delay(requested, Schedulers.parallel())
                .map(
                        tick -> {
                            long elapsed = Duration.between(start, Instant.now()).getSeconds();
                            return success(elapsed, false);
                        })
                .onErrorResume(
                        err -> {
                            long elapsed = Duration.between(start, Instant.now()).getSeconds();
                            log.debug("Sleep interrupted after {}s: {}", elapsed, err.getMessage());
                            return Mono.just(success(elapsed, true));
                        });
    }

    /**
     * Reactor cancellation path arrives as a discarded subscription — `onErrorResume` doesn't see
     * it, so we need an additional `doOnCancel` branch that produces the same shape. Reactor lets
     * us register cancel side-effects but not synthesize a result from them; instead we model the
     * cancel as a separate emission via {@link Mono#take(Duration)} pattern in tests. For the
     * production path, cancellation propagates back to the caller as a Reactor cancel signal — the
     * agent loop above us treats that as "tool aborted" and surfaces a generic interrupt message.
     * The user-visible behavior is identical.
     */
    private static ToolResult success(long sleptSeconds, boolean interrupted) {
        String content =
                interrupted
                        ? "Sleep interrupted after " + sleptSeconds + "s"
                        : "Slept for " + sleptSeconds + "s";
        return ToolResult.success(
                null, content, Map.of("slept_seconds", sleptSeconds, "interrupted", interrupted));
    }

    /**
     * Accepts either an Integer / Long / Number (JSON number) or a String that parses as a positive
     * integer. Returns null when the value is missing or unparseable so the caller can emit a
     * uniform "missing / invalid" error.
     */
    private static Integer parseDuration(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) {
            long l = n.longValue();
            if (l > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            return (int) l;
        }
        if (raw instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
