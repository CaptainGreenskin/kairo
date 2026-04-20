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

import io.kairo.api.context.CompactionConfig;
import io.kairo.api.context.CompactionResult;
import io.kairo.api.context.ContextManager;
import io.kairo.api.context.TokenBudget;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelProvider;
import io.kairo.core.context.compaction.CompactionPipeline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link ContextManager} following the "Facts First" principle.
 *
 * <p>Core philosophy: preserve original context as much as possible. Compaction is a <strong>last
 * resort</strong>, only triggered when token pressure reaches critical thresholds.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Thread-safe message management via {@link AtomicReference} with immutable snapshots
 *   <li>Token budget tracking with pressure calculation
 *   <li>Verbatim protection — messages can be marked as non-compressible
 *   <li>Progressive compaction pipeline with circuit breaker
 *   <li>Boundary marker audit trail
 * </ul>
 */
public class DefaultContextManager implements ContextManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextManager.class);

    private final AtomicReference<List<Msg>> messages = new AtomicReference<>(List.of());
    private final TokenBudgetManager budgetManager;
    private final CompactionPipeline compactionPipeline;
    private final BoundaryMarkerManager boundaryMarkerManager;
    private final Set<String> verbatimMessageIds = ConcurrentHashMap.newKeySet();

    /** Create a DefaultContextManager with default Claude 200K budget and no model provider. */
    public DefaultContextManager() {
        this(TokenBudgetManager.forClaude200K(), new CompactionPipeline((ModelProvider) null));
    }

    /**
     * Create a DefaultContextManager with a specified budget and model provider.
     *
     * @param budgetManager the token budget manager
     * @param modelProvider the model provider for auto-compaction (may be null)
     */
    public DefaultContextManager(TokenBudgetManager budgetManager, ModelProvider modelProvider) {
        this(budgetManager, new CompactionPipeline(modelProvider));
    }

    /**
     * Create a DefaultContextManager with full control over components.
     *
     * @param budgetManager the token budget manager
     * @param compactionPipeline the compaction pipeline
     */
    public DefaultContextManager(
            TokenBudgetManager budgetManager, CompactionPipeline compactionPipeline) {
        this.budgetManager = budgetManager;
        this.compactionPipeline = compactionPipeline;
        this.boundaryMarkerManager = new BoundaryMarkerManager();
    }

    @Override
    public void addMessage(Msg message) {
        messages.updateAndGet(
                current -> {
                    List<Msg> updated = new ArrayList<>(current);
                    updated.add(message);
                    return Collections.unmodifiableList(updated);
                });
        budgetManager.recordUsage(message.tokenCount());

        // Verbatim messages are automatically protected
        if (message.verbatimPreserved()) {
            verbatimMessageIds.add(message.id());
        }

        log.debug(
                "Message added: id={}, role={}, tokens={}, pressure={}",
                message.id(),
                message.role(),
                message.tokenCount(),
                budgetManager.pressure());
    }

    @Override
    public List<Msg> getMessages() {
        return messages.get(); // already unmodifiable
    }

    @Override
    public int getTokenCount() {
        return budgetManager.used();
    }

    @Override
    public TokenBudget getTokenBudget() {
        return budgetManager.getBudget();
    }

    @Override
    public Mono<CompactionResult> compact() {
        float pressure = budgetManager.pressure();
        log.info("Compaction requested. Current pressure: {}", pressure);

        if (pressure < 0.80f) {
            log.info("Pressure below 80% — no compaction needed");
            return Mono.empty();
        }

        CompactionConfig config = new CompactionConfig(budgetManager.remaining(), true, null);

        return compactionPipeline
                .execute(messages.get(), verbatimMessageIds, pressure, config)
                .doOnSuccess(
                        result -> {
                            if (result != null) {
                                // Atomic swap — readers always see a complete list
                                messages.set(
                                        Collections.unmodifiableList(
                                                new ArrayList<>(result.compactedMessages())));

                                // Update budget
                                budgetManager.releaseUsage(result.tokensSaved());

                                // Record boundary marker
                                boundaryMarkerManager.record(result.marker());

                                log.info(
                                        "Compaction applied: saved {} tokens, pressure now {}",
                                        result.tokensSaved(),
                                        budgetManager.pressure());
                            }
                        });
    }

    @Override
    public void markVerbatim(String messageId) {
        verbatimMessageIds.add(messageId);
        log.debug("Message marked as verbatim: {}", messageId);
    }

    /**
     * Get the boundary marker manager for audit trail access.
     *
     * @return the boundary marker manager
     */
    public BoundaryMarkerManager getBoundaryMarkerManager() {
        return boundaryMarkerManager;
    }

    /**
     * Get the token budget manager.
     *
     * @return the budget manager
     */
    public TokenBudgetManager getTokenBudgetManager() {
        return budgetManager;
    }

    /**
     * Get the set of verbatim (non-compressible) message IDs.
     *
     * @return an unmodifiable view of verbatim message IDs
     */
    public Set<String> getVerbatimMessageIds() {
        return Collections.unmodifiableSet(verbatimMessageIds);
    }

    /**
     * Check if the given messages need compaction based on token pressure.
     *
     * <p>Returns true if pressure exceeds 80% (the first compaction stage threshold).
     *
     * @param msgs the messages to check
     * @return true if compaction should be triggered
     */
    public boolean needsCompaction(List<Msg> msgs) {
        double pressure = budgetManager.getPressure(msgs);
        return pressure > 0.80;
    }

    /**
     * Run compaction on the provided messages.
     *
     * <p>Delegates to the CompactionPipeline and returns the compacted message list. If no
     * compaction is performed (pressure is low or pipeline is unavailable), returns the original
     * list.
     *
     * @param msgs the messages to compact
     * @return a Mono emitting the (possibly compacted) message list
     */
    public Mono<List<Msg>> compactMessages(List<Msg> msgs) {
        float pressure = (float) budgetManager.getPressure(msgs);
        log.info("Compaction requested for {} messages, pressure: {}", msgs.size(), pressure);

        if (pressure < 0.80f) {
            log.info("Pressure below 80%% — no compaction needed");
            return Mono.just(msgs);
        }

        CompactionConfig config = new CompactionConfig(budgetManager.remaining(), true, null);

        return compactionPipeline
                .execute(msgs, verbatimMessageIds, pressure, config)
                .map(
                        result -> {
                            log.info(
                                    "Compaction of external messages complete: saved {} tokens",
                                    result.tokensSaved());
                            return result.compactedMessages();
                        })
                .defaultIfEmpty(msgs);
    }

    /**
     * Get the current pressure for a list of messages.
     *
     * @param msgs the messages to check
     * @return the pressure ratio (0.0 to 1.0+)
     */
    public double getPressure(List<Msg> msgs) {
        return budgetManager.getPressure(msgs);
    }
}
