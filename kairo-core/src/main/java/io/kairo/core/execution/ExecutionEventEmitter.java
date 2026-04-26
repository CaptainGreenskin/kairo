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
package io.kairo.core.execution;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.execution.DurableExecutionStore;
import io.kairo.api.execution.ExecutionEvent;
import io.kairo.api.execution.ExecutionEventType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Emits {@link ExecutionEvent}s into a {@link DurableExecutionStore} with SHA-256 hash chain
 * integrity.
 *
 * <p>Each emitted event carries a hash computed as {@code SHA256(previousHash + payloadJson)},
 * forming a tamper-evident chain starting from the {@code "GENESIS"} seed. See ADR-011 for the hash
 * chain formula.
 *
 * <p>This class is thread-safe: {@code previousHash} and {@code eventCounter} are updated
 * atomically.
 *
 * @since v0.8
 */
public class ExecutionEventEmitter {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEventEmitter.class);

    /** The genesis seed for the first event's hash computation. */
    static final String GENESIS = HashChainUtils.GENESIS;

    private static final int SCHEMA_VERSION = 1;

    private final DurableExecutionStore store;
    private final String executionId;
    private final AtomicReference<String> previousHash;
    private final AtomicInteger eventCounter;
    @Nullable private final KairoEventBus eventBus;

    /**
     * Create a new emitter for the given execution (no event bus bridging).
     *
     * @param store the durable execution store to append events to
     * @param executionId the execution ID that events belong to
     */
    public ExecutionEventEmitter(DurableExecutionStore store, String executionId) {
        this(store, executionId, null);
    }

    /**
     * Create a new emitter for the given execution, bridging every emission to the {@link
     * KairoEventBus} for observability.
     *
     * @param store the durable execution store to append events to
     * @param executionId the execution ID that events belong to
     * @param eventBus optional bus facade; {@code null} disables bridging
     */
    public ExecutionEventEmitter(
            DurableExecutionStore store, String executionId, @Nullable KairoEventBus eventBus) {
        this.store = store;
        this.executionId = executionId;
        this.previousHash = new AtomicReference<>(GENESIS);
        this.eventCounter = new AtomicInteger(0);
        this.eventBus = eventBus;
    }

    /**
     * Emit an event into the store with hash-chain integrity.
     *
     * <p>Best-effort: callers should use {@code .onErrorResume()} to ensure emission failures do
     * not break the main execution flow.
     *
     * @param type the event type
     * @param payloadJson canonical JSON payload
     * @return completes when the event is appended to the store
     */
    public synchronized Mono<Void> emit(ExecutionEventType type, String payloadJson) {
        String prevHash = previousHash.get();
        String hash = HashChainUtils.computeHash(prevHash, payloadJson);

        ExecutionEvent event =
                new ExecutionEvent(
                        UUID.randomUUID().toString(),
                        type,
                        Instant.now(),
                        payloadJson,
                        hash,
                        SCHEMA_VERSION);

        // Atomically update the hash chain
        previousHash.set(hash);
        eventCounter.incrementAndGet();

        publishToBus(event);
        return store.appendEvent(executionId, event);
    }

    private void publishToBus(ExecutionEvent event) {
        if (eventBus == null) {
            return;
        }
        try {
            eventBus.publish(
                    new KairoEvent(
                            event.eventId(),
                            event.timestamp(),
                            KairoEvent.DOMAIN_EXECUTION,
                            event.eventType().name(),
                            event,
                            Map.of("executionId", executionId)));
        } catch (RuntimeException ex) {
            log.debug("KairoEventBus publish failed for execution event: {}", ex.toString());
        }
    }

    /**
     * Return the current hash chain head.
     *
     * @return the hash of the most recently emitted event, or {@code "GENESIS"} if none emitted
     */
    public String currentHash() {
        return previousHash.get();
    }

    /**
     * Return the number of events emitted so far.
     *
     * @return event count
     */
    public int eventCount() {
        return eventCounter.get();
    }

    /**
     * Compute the SHA-256 hash for the chain.
     *
     * @param prevHash the previous hash in the chain
     * @param payload the event payload
     * @return hex-encoded SHA-256 hash
     * @see HashChainUtils#computeHash(String, String)
     */
    static String computeHash(String prevHash, String payload) {
        return HashChainUtils.computeHash(prevHash, payload);
    }
}
