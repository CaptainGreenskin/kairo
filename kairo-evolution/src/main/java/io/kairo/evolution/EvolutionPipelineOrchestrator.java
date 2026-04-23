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
package io.kairo.evolution;

import io.kairo.api.evolution.EvolutionContext;
import io.kairo.api.evolution.EvolutionCounters;
import io.kairo.api.evolution.EvolutionOutcome;
import io.kairo.api.evolution.EvolutionPolicy;
import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.evolution.SkillTrustLevel;
import io.kairo.evolution.event.EvolutionEventType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Orchestrates the evolution pipeline: review → quarantine → scan → activate/reject.
 *
 * <p>Manages the lifecycle state machine per agent and delegates to the configured {@link
 * EvolutionPolicy} for the actual review logic.
 *
 * @since v0.9 (Experimental)
 */
public class EvolutionPipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EvolutionPipelineOrchestrator.class);

    private static final int MAX_SKILL_INSTRUCTIONS_LENGTH = 50_000;
    private static final int MIN_SKILL_INSTRUCTIONS_LENGTH = 10;

    private final EvolutionPolicy policy;
    private final EvolvedSkillStore skillStore;
    private final EvolutionStateMachine stateMachine;
    private final InMemoryEvolutionRuntimeStateStore stateStore;

    /**
     * Create a new orchestrator.
     *
     * @param policy the evolution policy for reviewing contexts
     * @param skillStore the store for persisting evolved skills
     * @param stateMachine the state machine governing transitions
     * @param stateStore the runtime state store for per-agent state tracking
     */
    public EvolutionPipelineOrchestrator(
            EvolutionPolicy policy,
            EvolvedSkillStore skillStore,
            EvolutionStateMachine stateMachine,
            InMemoryEvolutionRuntimeStateStore stateStore) {
        this.policy = policy;
        this.skillStore = skillStore;
        this.stateMachine = stateMachine;
        this.stateStore = stateStore;
    }

    /**
     * Submit an evolution context for asynchronous processing.
     *
     * <p>If the agent is currently suspended, the submission is silently skipped.
     *
     * @param context the evolution context snapshot
     * @return a Mono that completes when processing finishes (fire-and-forget safe)
     */
    public Mono<Void> submit(EvolutionContext context) {
        return Mono.defer(
                        () -> {
                            String agentName = context.agentName();
                            EvolutionState current = stateStore.getState(agentName);

                            if (current == EvolutionState.SUSPENDED) {
                                log.info(
                                        "Evolution event: {} for agent '{}'",
                                        EvolutionEventType.EVOLUTION_SUSPENDED,
                                        agentName);
                                return Mono.empty();
                            }

                            stateStore.setState(
                                    agentName,
                                    stateMachine.transit(current, EvolutionSignal.START_REVIEW));

                            return policy.review(context)
                                    .flatMap(outcome -> processOutcome(agentName, outcome))
                                    .doOnError(e -> handleFailure(agentName, e))
                                    .onErrorResume(e -> Mono.empty())
                                    .then();
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> processOutcome(String agentName, EvolutionOutcome outcome) {
        if (!outcome.hasChanges()) {
            stateStore.setState(agentName, EvolutionState.IDLE);
            log.debug("No evolution changes for agent '{}'", agentName);
            return Mono.empty();
        }

        // Transition: REVIEWING -> QUARANTINED
        stateStore.setState(
                agentName,
                stateMachine.transit(EvolutionState.REVIEWING, EvolutionSignal.REVIEW_COMPLETE));

        if (outcome.skillToCreate().isPresent()) {
            log.info(
                    "Evolution event: {} — skill '{}' for agent '{}'",
                    EvolutionEventType.SKILL_CREATED,
                    outcome.skillToCreate().get().name(),
                    agentName);
            return quarantineAndScan(agentName, outcome.skillToCreate().get());
        }

        if (outcome.skillToPatch().isPresent()) {
            log.info(
                    "Evolution event: {} — skill '{}' for agent '{}'",
                    EvolutionEventType.SKILL_UPDATED,
                    outcome.skillToPatch().get().name(),
                    agentName);
            return quarantineAndScan(agentName, outcome.skillToPatch().get());
        }

        // Memory-only outcome: go straight to APPLIED
        stateStore.setState(
                agentName,
                stateMachine.transit(EvolutionState.QUARANTINED, EvolutionSignal.SCAN_PASS));
        log.info("Evolution applied memory-only outcome for agent '{}'", agentName);
        return Mono.empty();
    }

    private Mono<Void> quarantineAndScan(String agentName, EvolvedSkill candidate) {
        // Save with DRAFT trust level (quarantine)
        EvolvedSkill quarantined =
                new EvolvedSkill(
                        candidate.name(),
                        candidate.version(),
                        candidate.description(),
                        candidate.instructions(),
                        candidate.category(),
                        candidate.tags(),
                        SkillTrustLevel.DRAFT,
                        candidate.metadata(),
                        candidate.createdAt(),
                        Instant.now(),
                        candidate.usageCount());

        return skillStore
                .save(quarantined)
                .flatMap(
                        saved -> {
                            log.info(
                                    "Evolution event: {} — skill '{}' for agent '{}'",
                                    EvolutionEventType.SKILL_QUARANTINED,
                                    saved.name(),
                                    agentName);

                            if (scanContent(saved)) {
                                log.info(
                                        "Evolution event: {} — skill '{}' for agent '{}'",
                                        EvolutionEventType.SKILL_SCAN_PASSED,
                                        saved.name(),
                                        agentName);
                                return activate(agentName, saved);
                            } else {
                                return reject(agentName, saved);
                            }
                        });
    }

    private Mono<Void> activate(String agentName, EvolvedSkill skill) {
        stateStore.setState(
                agentName,
                stateMachine.transit(EvolutionState.QUARANTINED, EvolutionSignal.SCAN_PASS));

        EvolvedSkill activated =
                new EvolvedSkill(
                        skill.name(),
                        skill.version(),
                        skill.description(),
                        skill.instructions(),
                        skill.category(),
                        skill.tags(),
                        SkillTrustLevel.VALIDATED,
                        skill.metadata(),
                        skill.createdAt(),
                        Instant.now(),
                        skill.usageCount());

        return skillStore
                .save(activated)
                .doOnNext(
                        s ->
                                log.info(
                                        "Evolution event: {} — skill '{}' for agent '{}'",
                                        EvolutionEventType.SKILL_ACTIVATED,
                                        s.name(),
                                        agentName))
                .then();
    }

    private Mono<Void> reject(String agentName, EvolvedSkill skill) {
        stateStore.setState(
                agentName,
                stateMachine.transit(EvolutionState.QUARANTINED, EvolutionSignal.SCAN_REJECT));

        return skillStore
                .delete(skill.name())
                .doOnSuccess(
                        v ->
                                log.warn(
                                        "Evolution event: {} — skill '{}' for agent '{}'",
                                        EvolutionEventType.SKILL_SCAN_REJECTED,
                                        skill.name(),
                                        agentName));
    }

    /** Basic content validation: non-empty, reasonable length, no obvious injection patterns. */
    private boolean scanContent(EvolvedSkill skill) {
        String instructions = skill.instructions();
        if (instructions == null || instructions.isBlank()) {
            log.warn("Scan rejected '{}': empty instructions", skill.name());
            return false;
        }
        if (instructions.length() < MIN_SKILL_INSTRUCTIONS_LENGTH) {
            log.warn(
                    "Scan rejected '{}': instructions too short ({})",
                    skill.name(),
                    instructions.length());
            return false;
        }
        if (instructions.length() > MAX_SKILL_INSTRUCTIONS_LENGTH) {
            log.warn(
                    "Scan rejected '{}': instructions too long ({})",
                    skill.name(),
                    instructions.length());
            return false;
        }
        // Basic injection check: instructions should not contain system prompt override attempts
        String lower = instructions.toLowerCase();
        if (lower.contains("ignore previous instructions")
                || lower.contains("disregard all prior")
                || lower.contains("you are now")) {
            log.warn("Scan rejected '{}': potential prompt injection detected", skill.name());
            return false;
        }
        return true;
    }

    private void handleFailure(String agentName, Throwable e) {
        log.warn("Evolution pipeline failed for agent '{}': {}", agentName, e.getMessage());

        EvolutionCounters counters = stateStore.getCounters(agentName);
        int newFailures = counters.consecutiveFailures() + 1;
        stateStore.setCounters(
                agentName,
                new EvolutionCounters(
                        counters.turnSinceLastMemoryReview(),
                        counters.toolLoopIterationsSinceLastSkillReview(),
                        newFailures));

        if (stateMachine.shouldSuspend(newFailures)) {
            stateStore.setState(agentName, EvolutionState.SUSPENDED);
            log.warn(
                    "Evolution event: {} for agent '{}' after {} consecutive failures",
                    EvolutionEventType.EVOLUTION_SUSPENDED,
                    agentName,
                    newFailures);
        } else {
            stateStore.setState(agentName, EvolutionState.IDLE);
        }
    }

    /** Compute SHA-256 hash of content for provenance tracking. */
    static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
