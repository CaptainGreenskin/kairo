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
package io.kairo.api.cron;

import io.kairo.api.Experimental;
import reactor.core.publisher.Mono;

/**
 * Delivers a cron task's result somewhere. Hosts implement this to route output to channels (chat /
 * Slack / DingTalk), files, or other agents. Multiple implementations can co-exist; routing is
 * selected by the {@link #target()} string of each delivery in the task's configuration.
 *
 * <p>Common target schemes:
 *
 * <ul>
 *   <li>{@code origin} — back to the chat that scheduled the task
 *   <li>{@code file:/path/to/output.txt} — append to a local file
 *   <li>{@code channel:<name>} — through a Kairo {@code Channel} implementation
 *   <li>{@code log} — slf4j INFO (default for hosts without UI)
 *   <li>{@code drop} — discard (useful for {@code wakeAgent}-style background jobs)
 * </ul>
 *
 * <p>The cron module only carries the abstraction; concrete deliveries live in the host
 * (kairo-assistant, kairo-spring-boot-starter-cron, etc).
 *
 * @since 1.2
 */
@Experimental("Cron delivery SPI — added in v1.2")
public interface CronDelivery {

    /** Best-effort URI / scheme this delivery handles (e.g. {@code "log"}, {@code "file"}). */
    String scheme();

    /**
     * Push the task's final output to its destination. The {@code target} string is the
     * task-specific routing key (e.g. {@code "/var/log/cron.log"} for a {@code file:} scheme).
     * Errors are surfaced via the returned {@link Mono} so the scheduler can record a failure
     * count.
     */
    Mono<Void> deliver(CronTask task, String target, String content);
}
