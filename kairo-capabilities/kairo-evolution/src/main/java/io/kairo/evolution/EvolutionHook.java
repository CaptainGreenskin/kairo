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

import io.kairo.api.agent.AgentState;
import io.kairo.api.evolution.EvolutionContext;
import io.kairo.api.evolution.EvolutionCounters;
import io.kairo.api.evolution.EvolutionTrigger;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.hook.OnSessionEnd;
import io.kairo.api.hook.SessionEndEvent;
import io.kairo.api.message.Msg;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hook that triggers the evolution pipeline at the end of an agent session.
 *
 * <p>Guards ensure evolution only runs when the session completed successfully with enough
 * iterations to make review worthwhile. The actual review is submitted asynchronously to the {@link
 * EvolutionPipelineOrchestrator} in a fire-and-forget fashion.
 *
 * @since v0.9 (Experimental)
 */
public class EvolutionHook {

    private static final Logger log = LoggerFactory.getLogger(EvolutionHook.class);

    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicInteger failureCount = new AtomicInteger();
    private final AtomicInteger skipCount = new AtomicInteger();

    private final EvolutionTrigger trigger;
    private final EvolvedSkillStore skillStore;
    private final EvolutionPipelineOrchestrator orchestrator;

    /**
     * Create a new evolution hook.
     *
     * @param trigger the trigger for deciding review eligibility
     * @param skillStore the store for retrieving existing skills
     * @param orchestrator the pipeline orchestrator to submit reviews to
     */
    public EvolutionHook(
            EvolutionTrigger trigger,
            EvolvedSkillStore skillStore,
            EvolutionPipelineOrchestrator orchestrator) {
        this.trigger = trigger;
        this.skillStore = skillStore;
        this.orchestrator = orchestrator;
    }

    /**
     * Invoked when an agent session ends. Submits an evolution review if the session meets minimum
     * criteria (completed state, sufficient iterations, non-empty history).
     *
     * @param event the session end event
     */
    @OnSessionEnd(order = 100)
    public void onSessionEnd(SessionEndEvent event) {
        if (event.finalState() != AgentState.COMPLETED) {
            log.debug(
                    "Skipping evolution for agent '{}': session did not complete (state={})",
                    event.agentName(),
                    event.finalState());
            skipCount.incrementAndGet();
            return;
        }
        if (event.iterations() < 3) {
            log.debug(
                    "Skipping evolution for agent '{}': too few iterations ({})",
                    event.agentName(),
                    event.iterations());
            skipCount.incrementAndGet();
            return;
        }

        List<Msg> history =
                event.conversationHistorySupplier() != null
                        ? event.conversationHistorySupplier().get()
                        : List.of();
        if (history.isEmpty()) {
            log.debug(
                    "Skipping evolution for agent '{}': empty conversation history",
                    event.agentName());
            skipCount.incrementAndGet();
            return;
        }

        List<io.kairo.api.evolution.EvolvedSkill> existingSkills =
                skillStore.list().collectList().blockOptional().orElse(List.of());

        EvolutionContext context =
                new EvolutionContext(
                        event.agentName(),
                        history,
                        event.iterations(),
                        EvolutionCounters.ZERO,
                        5, // default memory review threshold
                        8, // default skill review threshold
                        event.tokensUsed(),
                        existingSkills);

        log.info(
                "Submitting evolution review for agent '{}' ({} iterations, {} messages)",
                event.agentName(),
                event.iterations(),
                history.size());

        orchestrator
                .submit(context)
                .subscribe(
                        unused -> {},
                        err -> {
                            failureCount.incrementAndGet();
                            log.warn(
                                    "Evolution review failed for agent '{}': {}",
                                    event.agentName(),
                                    err.getMessage());
                        },
                        () -> successCount.incrementAndGet());
    }

    /** Returns the number of successfully processed evolution reviews. */
    public int getSuccessCount() {
        return successCount.get();
    }

    /** Returns the number of failed evolution reviews. */
    public int getFailureCount() {
        return failureCount.get();
    }

    /** Returns the number of skipped sessions (non-completed, low iterations, empty history). */
    public int getSkipCount() {
        return skipCount.get();
    }
}
