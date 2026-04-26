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
package io.kairo.core.context.compaction;

import io.kairo.api.context.CompactionConfig;
import io.kairo.api.context.CompactionResult;
import io.kairo.api.context.CompactionStrategy;
import io.kairo.api.context.ContextState;
import io.kairo.api.hook.HookChain;
import io.kairo.api.hook.PostCompactEvent;
import io.kairo.api.hook.PreCompactEvent;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelProvider;
import io.kairo.core.context.CompactionThresholds;
import io.kairo.core.model.ModelRegistry;
import io.kairo.core.resilience.CircuitBreakerPrimitive;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Orchestrates progressive compaction through multiple stages.
 *
 * <p>Key design: compaction is <strong>progressive</strong> — lighter strategies are attempted
 * first, and heavier ones only kick in when pressure remains high. A circuit breaker prevents
 * runaway compaction attempts.
 *
 * <p>Default pipeline order (by priority):
 *
 * <ol>
 *   <li>{@link TimeBasedMicrocompact} (idle) — compress old messages after 60min idle
 *   <li>{@link SnipCompaction} (80%) — snip old tool results
 *   <li>{@link MicroCompaction} (85%) — clear tool result content
 *   <li>{@link CollapseCompaction} (90%) — fold message groups
 *   <li>{@link AutoCompaction} (95%) — LLM-generated summary
 *   <li>{@link PartialCompaction} (98%) — selective last-resort compression
 * </ol>
 */
public class CompactionPipeline {

    private static final Logger log = LoggerFactory.getLogger(CompactionPipeline.class);

    private final List<CompactionStrategy> stages;
    private final CircuitBreakerPrimitive circuitBreaker;
    private final String modelId;
    private final HookChain hookChain;

    /**
     * Create a pipeline with default stages.
     *
     * @param modelProvider the model provider for AutoCompaction (may be null)
     */
    public CompactionPipeline(ModelProvider modelProvider) {
        this(modelProvider, null, null, CompactionThresholds.DEFAULTS);
    }

    /**
     * Create a pipeline with default stages, model ID, and hook chain.
     *
     * @param modelProvider the model provider for AutoCompaction (may be null)
     * @param modelId the model ID for resolving context window (may be null)
     * @param hookChain the hook chain for pre/post compact events (may be null)
     */
    public CompactionPipeline(ModelProvider modelProvider, String modelId, HookChain hookChain) {
        this(modelProvider, modelId, hookChain, CompactionThresholds.DEFAULTS);
    }

    /**
     * Create a pipeline with default stages, model ID, hook chain, and custom thresholds.
     *
     * @param modelProvider the model provider for AutoCompaction (may be null)
     * @param modelId the model ID for resolving context window (may be null)
     * @param hookChain the hook chain for pre/post compact events (may be null)
     * @param thresholds compaction thresholds (uses sensible defaults if null)
     */
    public CompactionPipeline(
            ModelProvider modelProvider,
            String modelId,
            HookChain hookChain,
            CompactionThresholds thresholds) {
        this(
                buildDefaultStages(
                        modelProvider,
                        thresholds != null ? thresholds : CompactionThresholds.DEFAULTS),
                modelId,
                hookChain,
                thresholds != null ? thresholds : CompactionThresholds.DEFAULTS);
    }

    /**
     * Create a pipeline with custom stages.
     *
     * @param stages the compaction strategies to use, in priority order
     */
    public CompactionPipeline(List<CompactionStrategy> stages) {
        this(stages, null, null, CompactionThresholds.DEFAULTS);
    }

    /**
     * Create a pipeline with custom stages, model ID, and hook chain.
     *
     * @param stages the compaction strategies to use, in priority order
     * @param modelId the model ID for resolving context window (may be null)
     * @param hookChain the hook chain for pre/post compact events (may be null)
     */
    public CompactionPipeline(
            List<CompactionStrategy> stages, String modelId, HookChain hookChain) {
        this(stages, modelId, hookChain, CompactionThresholds.DEFAULTS);
    }

    /**
     * Create a pipeline with custom stages, model ID, hook chain, and custom thresholds.
     *
     * @param stages the compaction strategies to use, in priority order
     * @param modelId the model ID for resolving context window (may be null)
     * @param hookChain the hook chain for pre/post compact events (may be null)
     * @param thresholds compaction thresholds for circuit breaker configuration
     */
    public CompactionPipeline(
            List<CompactionStrategy> stages,
            String modelId,
            HookChain hookChain,
            CompactionThresholds thresholds) {
        CompactionThresholds t = thresholds != null ? thresholds : CompactionThresholds.DEFAULTS;
        this.stages =
                stages.stream()
                        .sorted(Comparator.comparingInt(CompactionStrategy::priority))
                        .toList();
        this.circuitBreaker =
                new CircuitBreakerPrimitive(
                        t.cbFailureLimit(), Duration.ofSeconds(t.cbCooldownSeconds()));
        this.modelId = modelId;
        this.hookChain = hookChain;
    }

    private static List<CompactionStrategy> buildDefaultStages(
            ModelProvider modelProvider, CompactionThresholds t) {
        return List.of(
                new TimeBasedMicrocompact(),
                new SnipCompaction(t.snipPressure()),
                new MicroCompaction(t.microPressure()),
                new CollapseCompaction(t.collapsePressure()),
                new AutoCompaction(modelProvider, t.autoPressure()),
                new PartialCompaction(t.partialPressure()));
    }

    /**
     * Execute the compaction pipeline on the given messages.
     *
     * <p>Filters out verbatim messages before passing to strategies. Each stage is executed
     * sequentially, and later stages only run if the current pressure still exceeds their trigger
     * threshold.
     *
     * @param messages the full message list
     * @param verbatimIds IDs of messages that must not be compressed
     * @param currentPressure the current token pressure
     * @param config the compaction configuration
     * @return a Mono emitting the merged compaction result, or empty if nothing was done
     */
    public Mono<CompactionResult> execute(
            List<Msg> messages,
            Set<String> verbatimIds,
            float currentPressure,
            CompactionConfig config) {
        if (!circuitBreaker.allowCall()) {
            log.warn("CompactionPipeline circuit breaker is OPEN — skipping compaction");
            return Mono.empty();
        }

        int contextWindow = modelId != null ? ModelRegistry.getContextWindow(modelId) : 0;
        ContextState state =
                new ContextState(0, 0, currentPressure, messages.size(), contextWindow);

        // Filter compressible messages (exclude verbatim)
        List<Msg> compressible =
                messages.stream().filter(m -> !verbatimIds.contains(m.id())).toList();

        // Fire PreCompact hook if available
        Mono<io.kairo.api.hook.HookResult<PreCompactEvent>> preHookMono;
        if (hookChain != null) {
            PreCompactEvent preEvent = new PreCompactEvent(compressible, currentPressure);
            preHookMono = hookChain.firePreCompactWithResult(preEvent);
        } else {
            preHookMono =
                    Mono.just(
                            io.kairo.api.hook.HookResult.proceed(
                                    new PreCompactEvent(compressible, currentPressure)));
        }

        return preHookMono.flatMap(
                hookResult -> {
                    // Handle ABORT decision
                    if (!hookResult.shouldProceed()) {
                        log.info("Compaction aborted by PreCompact hook: {}", hookResult.reason());
                        return Mono.empty();
                    }

                    // Handle SKIP decision — skip compaction gracefully
                    if (hookResult.shouldSkip()) {
                        log.info("Compaction skipped by hook: {}", hookResult.reason());
                        return Mono.empty();
                    }

                    PreCompactEvent preEvent = hookResult.event();
                    if (preEvent.cancelled()) {
                        log.info("Compaction cancelled by PreCompact hook");
                        return Mono.empty();
                    }

                    // Progressive fold: each stage operates on the output of the previous
                    // stage so lighter compactions compose with heavier ones instead of each
                    // stage seeing the raw input and the final merge silently discarding
                    // upstream work.
                    Mono<CompactionResult> acc =
                            Mono.just(new CompactionResult(compressible, 0, null));
                    for (CompactionStrategy stage : stages) {
                        if (!stage.shouldTrigger(state)) {
                            continue;
                        }
                        final CompactionStrategy current = stage;
                        final Mono<CompactionResult> prev = acc;
                        acc =
                                prev.flatMap(
                                        pr -> {
                                            log.info(
                                                    "Executing compaction stage: {} (priority={})",
                                                    current.name(),
                                                    current.priority());
                                            return current.compact(pr.compactedMessages(), config)
                                                    .map(
                                                            sr ->
                                                                    new CompactionResult(
                                                                            sr.compactedMessages(),
                                                                            pr.tokensSaved()
                                                                                    + sr
                                                                                            .tokensSaved(),
                                                                            sr.marker() != null
                                                                                    ? sr.marker()
                                                                                    : pr.marker()));
                                        });
                    }

                    return acc.flatMap(
                                    r ->
                                            r.marker() == null
                                                    ? Mono.<CompactionResult>empty()
                                                    : Mono.just(r))
                            .flatMap(
                                    result -> {
                                        // Fire PostCompact hook if available
                                        if (hookChain != null) {
                                            PostCompactEvent postEvent =
                                                    new PostCompactEvent(
                                                            result.compactedMessages(),
                                                            result.tokensSaved(),
                                                            result.marker().strategyName(),
                                                            List.of(result.marker()));
                                            return hookChain
                                                    .<PostCompactEvent>firePostCompactWithResult(
                                                            postEvent)
                                                    .map(
                                                            postResult -> {
                                                                // Handle ABORT — discard compaction
                                                                if (!postResult.shouldProceed()) {
                                                                    log.info(
                                                                            "Compaction discarded"
                                                                                    + " by PostCompact"
                                                                                    + " hook: {}",
                                                                            postResult.reason());
                                                                    return result;
                                                                }
                                                                PostCompactEvent evt =
                                                                        postResult.event();
                                                                // Merge recovery messages
                                                                List<Msg> recoveryMsgs =
                                                                        evt.getRecoveryMessages();
                                                                if (!recoveryMsgs.isEmpty()) {
                                                                    List<Msg> merged =
                                                                            new ArrayList<>(
                                                                                    result
                                                                                            .compactedMessages());
                                                                    merged.addAll(recoveryMsgs);
                                                                    return new CompactionResult(
                                                                            merged,
                                                                            result.tokensSaved(),
                                                                            result.marker());
                                                                }
                                                                return result;
                                                            });
                                        }
                                        return Mono.just(result);
                                    })
                            .doOnSuccess(
                                    result -> {
                                        if (result != null) {
                                            circuitBreaker.recordSuccess();
                                            log.info(
                                                    "Compaction complete: saved {} tokens",
                                                    result.tokensSaved());
                                        }
                                    })
                            .doOnError(
                                    e -> {
                                        circuitBreaker.recordFailure();
                                        log.error("Compaction failed: {}", e.getMessage());
                                    });
                });
    }

    /**
     * Check if the circuit breaker is open (too many consecutive failures).
     *
     * @return true if the pipeline is currently disabled
     */
    public boolean isCircuitBreakerOpen() {
        return circuitBreaker.isOpen();
    }

    /** Reset the circuit breaker. */
    public void resetCircuitBreaker() {
        circuitBreaker.reset();
    }
}
