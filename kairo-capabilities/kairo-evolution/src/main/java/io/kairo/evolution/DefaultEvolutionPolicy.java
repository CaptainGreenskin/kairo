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
import io.kairo.api.evolution.EvolutionOutcome;
import io.kairo.api.evolution.EvolutionPolicy;
import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.evolution.SkillTrustLevel;
import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Default LLM-backed evolution policy that reviews conversation history to propose new skills and
 * memory entries.
 *
 * <p>Sends two parallel review prompts (skill + memory) to the configured model, parses structured
 * responses, and merges the outcomes.
 *
 * @since v0.9 (Experimental)
 */
public class DefaultEvolutionPolicy implements EvolutionPolicy {

    private static final Logger log = LoggerFactory.getLogger(DefaultEvolutionPolicy.class);

    private static final String SKILL_REVIEW_PROMPT =
            "Review the agent conversation and determine if a non-trivial approach, technique, "
                    + "or workflow was demonstrated that should be captured as a reusable skill.\n\n"
                    + "If a skill is found, respond EXACTLY in this format:\n"
                    + "SKILL_NAME: <name>\n"
                    + "DESCRIPTION: <one-line description>\n"
                    + "CATEGORY: <category>\n"
                    + "INSTRUCTIONS: <detailed instructions for the agent to follow>\n\n"
                    + "If no skill is warranted, respond with: NO_SKILL\n\n"
                    + "Conversation:\n";

    private static final String MEMORY_REVIEW_PROMPT =
            "Review the conversation and determine if the user revealed preferences, "
                    + "constraints, or important context that should be remembered across sessions.\n\n"
                    + "If a memory is found, respond EXACTLY in this format:\n"
                    + "MEMORY: <concise summary>\n"
                    + "IMPORTANCE: <0.0-1.0>\n"
                    + "TAGS: <comma-separated tags>\n\n"
                    + "If no memory is warranted, respond with: NO_MEMORY\n\n"
                    + "Conversation:\n";

    private final ModelProvider modelProvider;
    private final String modelName;
    private final int iterationThreshold;
    private final EvolvedSkillStore skillStore;
    private final Duration timeout;

    /**
     * Create a new policy.
     *
     * @param modelProvider the model provider for LLM calls
     * @param modelName the model identifier to use for review calls
     * @param iterationThreshold minimum iterations before review triggers
     * @param skillStore the store for existing skills (used for deduplication context)
     * @param timeout timeout for each review call; null defaults to 60s
     */
    public DefaultEvolutionPolicy(
            ModelProvider modelProvider,
            String modelName,
            int iterationThreshold,
            EvolvedSkillStore skillStore,
            Duration timeout) {
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.iterationThreshold = iterationThreshold;
        this.skillStore = skillStore;
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(60);
    }

    @Override
    public Mono<EvolutionOutcome> review(EvolutionContext context) {
        if (context.iterationCount() < iterationThreshold) {
            return Mono.just(EvolutionOutcome.empty());
        }
        return Mono.zip(reviewSkills(context), reviewMemory(context))
                .map(tuple -> mergeOutcomes(tuple.getT1(), tuple.getT2()))
                .timeout(this.timeout)
                .onErrorResume(
                        e -> {
                            log.warn("Evolution review failed: {}", e.getMessage());
                            return Mono.just(EvolutionOutcome.empty());
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<EvolutionOutcome> reviewSkills(EvolutionContext context) {
        String conversationText = formatConversation(context.conversationHistory());
        String existingSkillsText =
                context.existingSkills().stream()
                        .map(s -> "- " + s.name() + ": " + s.description())
                        .collect(Collectors.joining("\n"));

        String prompt = SKILL_REVIEW_PROMPT + conversationText;
        if (!existingSkillsText.isEmpty()) {
            prompt += "\n\nExisting skills (avoid duplicates):\n" + existingSkillsText;
        }

        return callReviewLlm(prompt)
                .map(this::parseSkillResponse)
                .onErrorResume(
                        e -> {
                            log.debug("Skill review parse error: {}", e.getMessage());
                            return Mono.just(EvolutionOutcome.empty());
                        });
    }

    private Mono<EvolutionOutcome> reviewMemory(EvolutionContext context) {
        String conversationText = formatConversation(context.conversationHistory());
        String prompt = MEMORY_REVIEW_PROMPT + conversationText;

        return callReviewLlm(prompt)
                .map(this::parseMemoryResponse)
                .onErrorResume(
                        e -> {
                            log.debug("Memory review parse error: {}", e.getMessage());
                            return Mono.just(EvolutionOutcome.empty());
                        });
    }

    private Mono<String> callReviewLlm(String prompt) {
        ModelConfig config =
                ModelConfig.builder().model(modelName).maxTokens(2048).temperature(0.3).build();

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, prompt));

        return modelProvider
                .call(messages, config)
                .map(
                        response ->
                                response.contents().stream()
                                        .filter(Content.TextContent.class::isInstance)
                                        .map(c -> ((Content.TextContent) c).text())
                                        .findFirst()
                                        .orElse(""));
    }

    private EvolutionOutcome parseSkillResponse(String response) {
        if (response == null || response.isBlank() || response.trim().equals("NO_SKILL")) {
            return EvolutionOutcome.empty();
        }

        String name = extractField(response, "SKILL_NAME:");
        String description = extractField(response, "DESCRIPTION:");
        String category = extractField(response, "CATEGORY:");
        String instructions = extractField(response, "INSTRUCTIONS:");

        if (name.isEmpty() || instructions.isEmpty()) {
            log.debug("Incomplete skill response, skipping");
            return EvolutionOutcome.empty();
        }

        EvolvedSkill skill =
                new EvolvedSkill(
                        name,
                        "1.0.0",
                        description,
                        instructions,
                        category.isEmpty() ? "general" : category,
                        Set.of(),
                        SkillTrustLevel.DRAFT,
                        null,
                        Instant.now(),
                        Instant.now(),
                        0);

        return new EvolutionOutcome(
                Optional.of(skill), Optional.empty(), List.of(), "Skill extracted: " + name);
    }

    private EvolutionOutcome parseMemoryResponse(String response) {
        if (response == null || response.isBlank() || response.trim().equals("NO_MEMORY")) {
            return EvolutionOutcome.empty();
        }

        String memory = extractField(response, "MEMORY:");
        String importanceStr = extractField(response, "IMPORTANCE:");
        String tagsStr = extractField(response, "TAGS:");

        if (memory.isEmpty()) {
            log.debug("Incomplete memory response, skipping");
            return EvolutionOutcome.empty();
        }

        double importance = 0.5;
        try {
            if (!importanceStr.isEmpty()) {
                importance = Math.max(0.0, Math.min(1.0, Double.parseDouble(importanceStr.trim())));
            }
        } catch (NumberFormatException e) {
            // use default
        }

        Set<String> tags =
                tagsStr.isEmpty()
                        ? Set.of()
                        : Set.of(tagsStr.split(",")).stream()
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toUnmodifiableSet());

        MemoryEntry entry =
                new MemoryEntry(
                        "evo-" + System.nanoTime(),
                        null,
                        memory,
                        null,
                        io.kairo.api.memory.MemoryScope.GLOBAL,
                        importance,
                        null,
                        tags,
                        Instant.now(),
                        null);

        return new EvolutionOutcome(
                Optional.empty(),
                Optional.empty(),
                List.of(entry),
                "Memory extracted: " + memory.substring(0, Math.min(50, memory.length())));
    }

    private EvolutionOutcome mergeOutcomes(EvolutionOutcome skills, EvolutionOutcome memory) {
        return new EvolutionOutcome(
                skills.skillToCreate().isPresent()
                        ? skills.skillToCreate()
                        : memory.skillToCreate(),
                skills.skillToPatch().isPresent() ? skills.skillToPatch() : memory.skillToPatch(),
                mergeMemories(skills.memoriesToSave(), memory.memoriesToSave()),
                joinNotes(skills.reviewNotes(), memory.reviewNotes()));
    }

    private List<MemoryEntry> mergeMemories(List<MemoryEntry> a, List<MemoryEntry> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        var merged = new java.util.ArrayList<>(a);
        merged.addAll(b);
        return List.copyOf(merged);
    }

    private String joinNotes(String a, String b) {
        if (a == null || a.isEmpty()) return b != null ? b : "";
        if (b == null || b.isEmpty()) return a;
        return a + "; " + b;
    }

    private String formatConversation(List<Msg> history) {
        return history.stream()
                .map(msg -> msg.role().name() + ": " + msg.text())
                .collect(Collectors.joining("\n"));
    }

    private String extractField(String text, String fieldPrefix) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(fieldPrefix)) {
                return trimmed.substring(fieldPrefix.length()).trim();
            }
        }
        return "";
    }
}
