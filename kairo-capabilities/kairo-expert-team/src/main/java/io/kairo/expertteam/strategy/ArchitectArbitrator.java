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
package io.kairo.expertteam.strategy;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.TeamResult.StepOutcome;
import io.kairo.expertteam.role.ExpertRoleRegistry;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Architect arbitration component that resolves conflicts and deadlocks in team execution.
 *
 * <p>The Architect is escalated to when:
 *
 * <ol>
 *   <li><strong>Exhausted feedback loop</strong> — Reviewer keeps rejecting (REVISE), Coder
 *       exhausts maxFeedbackRounds, and senior model escalation also fails. The Architect decides
 *       whether to accept the output with caveats or provide revised instructions for one final
 *       attempt.
 *   <li><strong>Parallel group score divergence</strong> — Steps within the same parallelGroup
 *       produce artifacts with evaluation score variance exceeding the configured threshold. The
 *       Architect picks the winning approach or produces a merged resolution.
 * </ol>
 *
 * <p>When no Architect agent is available (null), all arbitration methods gracefully fall back to
 * {@link ArbitrationDecision#ACCEPT_WITH_CAVEATS} with the original output preserved.
 *
 * @since v0.10 (Experimental)
 */
public class ArchitectArbitrator {

    private static final Logger log = LoggerFactory.getLogger(ArchitectArbitrator.class);

    /** Score divergence threshold above which arbitration is triggered. */
    private static final double ARBITRATION_THRESHOLD = 0.4;

    private final ExpertRoleRegistry registry;
    @Nullable private final Agent architectAgent;

    /**
     * Creates an arbitrator with an optional Architect agent.
     *
     * @param registry the expert role registry; must not be null
     * @param architectAgent the Architect role's agent instance; may be {@code null} for graceful
     *     fallback behavior
     */
    public ArchitectArbitrator(ExpertRoleRegistry registry, @Nullable Agent architectAgent) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.architectAgent = architectAgent;
    }

    /**
     * Trigger 1: Arbitrate after feedback loop exhaustion.
     *
     * <p>When the Reviewer keeps rejecting and the Coder cannot fix, the Architect decides:
     *
     * <ul>
     *   <li>{@link ArbitrationDecision#ACCEPT_WITH_CAVEATS} — accept the coder's output noting
     *       limitations
     *   <li>{@link ArbitrationDecision#REVISED_INSTRUCTION} — provide revised instructions for one
     *       final attempt
     * </ul>
     *
     * @param goal the overall team goal
     * @param coderOutput the coder's last step outcome
     * @param reviewerVerdict the reviewer's last evaluation verdict
     * @param lastFeedback the last feedback string from the reviewer
     * @return a Mono emitting the arbitration result
     */
    public Mono<ArbitrationResult> arbitrateFeedbackExhaustion(
            String goal,
            StepOutcome coderOutput,
            EvaluationVerdict reviewerVerdict,
            String lastFeedback) {
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(coderOutput, "coderOutput must not be null");
        Objects.requireNonNull(reviewerVerdict, "reviewerVerdict must not be null");

        if (architectAgent == null) {
            log.debug("No architect agent available; falling back to ACCEPT_WITH_CAVEATS");
            return Mono.just(
                    new ArbitrationResult(
                            ArbitrationDecision.ACCEPT_WITH_CAVEATS,
                            coderOutput.output(),
                            "No architect agent available; accepting output as-is"));
        }

        String prompt =
                buildFeedbackExhaustionPrompt(goal, coderOutput, reviewerVerdict, lastFeedback);
        return callArchitect(prompt)
                .map(response -> parseFeedbackExhaustionResponse(response, coderOutput.output()))
                .onErrorResume(
                        ex -> {
                            log.warn(
                                    "Architect arbitration failed for feedback exhaustion: {}",
                                    ex.toString());
                            return Mono.just(
                                    new ArbitrationResult(
                                            ArbitrationDecision.ACCEPT_WITH_CAVEATS,
                                            coderOutput.output(),
                                            "Architect error: " + ex.getMessage()));
                        });
    }

    /**
     * Trigger 2: Arbitrate parallel group score divergence.
     *
     * <p>When parallel steps produce conflicting artifacts with score variance exceeding the
     * threshold, the Architect resolves the conflict by picking a winner or producing a merged
     * resolution.
     *
     * @param goal the overall team goal
     * @param artifacts the scored artifacts from the parallel group
     * @return a Mono emitting the arbitration result
     */
    public Mono<ArbitrationResult> arbitrateScoreDivergence(
            String goal, List<ScoredArtifact> artifacts) {
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(artifacts, "artifacts must not be null");

        if (architectAgent == null) {
            log.debug(
                    "No architect agent available; falling back to PICK_ARTIFACT (highest score)");
            ScoredArtifact best =
                    artifacts.stream()
                            .max((a, b) -> Double.compare(a.score(), b.score()))
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "artifacts must not be empty"));
            return Mono.just(
                    new ArbitrationResult(
                            ArbitrationDecision.PICK_ARTIFACT,
                            best.output(),
                            "No architect agent available; picked highest-scored artifact from step '"
                                    + best.stepId()
                                    + "'"));
        }

        String prompt = buildDivergencePrompt(goal, artifacts);
        return callArchitect(prompt)
                .map(response -> parseDivergenceResponse(response, artifacts))
                .onErrorResume(
                        ex -> {
                            log.warn(
                                    "Architect arbitration failed for score divergence: {}",
                                    ex.toString());
                            ScoredArtifact best =
                                    artifacts.stream()
                                            .max((a, b) -> Double.compare(a.score(), b.score()))
                                            .orElse(artifacts.get(0));
                            return Mono.just(
                                    new ArbitrationResult(
                                            ArbitrationDecision.PICK_ARTIFACT,
                                            best.output(),
                                            "Architect error: " + ex.getMessage()));
                        });
    }

    /**
     * Check if a set of scored artifacts should trigger arbitration based on score divergence.
     *
     * <p>Arbitration is triggered when the difference between the maximum and minimum scores
     * exceeds {@link #ARBITRATION_THRESHOLD} (0.4).
     *
     * @param artifacts the scored artifacts to evaluate
     * @return {@code true} if arbitration should be triggered
     */
    public boolean shouldArbitrate(List<ScoredArtifact> artifacts) {
        if (artifacts == null || artifacts.size() < 2) {
            return false;
        }
        double max = artifacts.stream().mapToDouble(ScoredArtifact::score).max().orElse(0);
        double min = artifacts.stream().mapToDouble(ScoredArtifact::score).min().orElse(0);
        return (max - min) > ARBITRATION_THRESHOLD;
    }

    // ---------------------------------------------------------------------- private

    private Mono<String> callArchitect(String prompt) {
        Msg input = Msg.of(MsgRole.USER, prompt);
        return architectAgent.call(input).map(Msg::text);
    }

    private String buildFeedbackExhaustionPrompt(
            String goal,
            StepOutcome coderOutput,
            EvaluationVerdict reviewerVerdict,
            @Nullable String lastFeedback) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ARCHITECT ARBITRATION — Feedback Loop Exhausted]\n\n");
        sb.append("Goal: ").append(goal).append("\n\n");
        sb.append("Coder produced:\n").append(coderOutput.output()).append("\n\n");
        sb.append("Reviewer verdict: ").append(reviewerVerdict.outcome().name());
        sb.append(" (score: ").append(reviewerVerdict.score()).append(")\n");
        if (lastFeedback != null && !lastFeedback.isBlank()) {
            sb.append("Last feedback: ").append(lastFeedback).append("\n");
        }
        if (!reviewerVerdict.suggestions().isEmpty()) {
            sb.append("Suggestions:\n");
            for (String s : reviewerVerdict.suggestions()) {
                sb.append(" - ").append(s).append('\n');
            }
        }
        sb.append("\nThe coder has exhausted all retry attempts.\n\n");
        sb.append("Decide:\n");
        sb.append("(A) ACCEPT — Accept coder's output noting caveats/limitations\n");
        sb.append("(B) REVISE — Provide ONE revised instruction for a final attempt\n\n");
        sb.append("Respond with exactly one of:\n");
        sb.append("DECISION: ACCEPT\n<your rationale>\n<the accepted output>\n\n");
        sb.append("OR\n\n");
        sb.append("DECISION: REVISE\n<your rationale>\n<revised instruction for the coder>\n");
        return sb.toString();
    }

    private String buildDivergencePrompt(String goal, List<ScoredArtifact> artifacts) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ARCHITECT ARBITRATION — Score Divergence]\n\n");
        sb.append("Goal: ").append(goal).append("\n\n");
        sb.append("Multiple experts produced conflicting outputs:\n\n");
        for (int i = 0; i < artifacts.size(); i++) {
            ScoredArtifact a = artifacts.get(i);
            sb.append("--- Artifact ").append(i + 1).append(" ---\n");
            sb.append("Step: ").append(a.stepId()).append("\n");
            sb.append("Role: ").append(a.roleId()).append("\n");
            sb.append("Score: ").append(a.score()).append("\n");
            sb.append("Output:\n").append(a.output()).append("\n\n");
        }
        sb.append("Decide:\n");
        sb.append("(A) PICK — Choose the best artifact\n");
        sb.append("(B) MERGE — Produce a merged resolution combining the best of all\n\n");
        sb.append("Respond with exactly one of:\n");
        sb.append("DECISION: PICK <artifact_number>\n<your rationale>\n\n");
        sb.append("OR\n\n");
        sb.append("DECISION: MERGE\n<your rationale>\n<merged output>\n");
        return sb.toString();
    }

    private ArbitrationResult parseFeedbackExhaustionResponse(
            String response, String originalOutput) {
        String normalized = response.trim();

        if (normalized.contains("DECISION: REVISE") || normalized.contains("DECISION:REVISE")) {
            String content = extractContentAfterDecision(normalized, "REVISE");
            String rationale = extractRationale(content);
            String instruction = extractOutput(content);
            return new ArbitrationResult(
                    ArbitrationDecision.REVISED_INSTRUCTION,
                    instruction.isBlank() ? content : instruction,
                    rationale.isBlank() ? "Architect provided revised instruction" : rationale);
        }

        // Default to ACCEPT_WITH_CAVEATS for any response containing ACCEPT or unrecognized format
        String content = extractContentAfterDecision(normalized, "ACCEPT");
        String rationale = extractRationale(content.isEmpty() ? normalized : content);
        return new ArbitrationResult(
                ArbitrationDecision.ACCEPT_WITH_CAVEATS,
                originalOutput,
                rationale.isBlank() ? "Architect accepted the output" : rationale);
    }

    private ArbitrationResult parseDivergenceResponse(
            String response, List<ScoredArtifact> artifacts) {
        String normalized = response.trim();

        if (normalized.contains("DECISION: MERGE") || normalized.contains("DECISION:MERGE")) {
            String content = extractContentAfterDecision(normalized, "MERGE");
            String rationale = extractRationale(content);
            String mergedOutput = extractOutput(content);
            return new ArbitrationResult(
                    ArbitrationDecision.MERGED_RESOLUTION,
                    mergedOutput.isBlank() ? content : mergedOutput,
                    rationale.isBlank() ? "Architect produced merged resolution" : rationale);
        }

        if (normalized.contains("DECISION: PICK") || normalized.contains("DECISION:PICK")) {
            // Try to extract artifact number
            int pickedIndex = extractPickedIndex(normalized, artifacts.size());
            ScoredArtifact picked = artifacts.get(pickedIndex);
            String content = extractContentAfterDecision(normalized, "PICK");
            String rationale = extractRationale(content);
            return new ArbitrationResult(
                    ArbitrationDecision.PICK_ARTIFACT,
                    picked.output(),
                    rationale.isBlank()
                            ? "Architect picked artifact from step '" + picked.stepId() + "'"
                            : rationale);
        }

        // Unrecognized format: pick the highest-scored artifact
        ScoredArtifact best =
                artifacts.stream()
                        .max((a, b) -> Double.compare(a.score(), b.score()))
                        .orElse(artifacts.get(0));
        return new ArbitrationResult(
                ArbitrationDecision.PICK_ARTIFACT,
                best.output(),
                "Could not parse architect response; defaulting to highest-scored artifact");
    }

    private String extractContentAfterDecision(String text, String decisionKeyword) {
        int idx = text.indexOf("DECISION:");
        if (idx < 0) return text;
        int afterDecision = text.indexOf('\n', idx);
        if (afterDecision < 0) return "";
        return text.substring(afterDecision + 1).trim();
    }

    private String extractRationale(String content) {
        if (content.isEmpty()) return "";
        // Take the first non-empty line as rationale
        String[] lines = content.split("\n", 3);
        return lines.length > 0 ? lines[0].trim() : "";
    }

    private String extractOutput(String content) {
        if (content.isEmpty()) return "";
        // Everything after the first line is the output
        int firstNewline = content.indexOf('\n');
        if (firstNewline < 0) return "";
        return content.substring(firstNewline + 1).trim();
    }

    private int extractPickedIndex(String text, int maxSize) {
        // Look for a number after "PICK"
        int pickIdx = text.indexOf("PICK");
        if (pickIdx < 0) return 0;
        String afterPick = text.substring(pickIdx + 4).trim();
        // Try to parse the first number
        StringBuilder numBuilder = new StringBuilder();
        for (char c : afterPick.toCharArray()) {
            if (Character.isDigit(c)) {
                numBuilder.append(c);
            } else if (numBuilder.length() > 0) {
                break;
            }
        }
        if (numBuilder.length() > 0) {
            try {
                int idx = Integer.parseInt(numBuilder.toString()) - 1; // 1-based to 0-based
                if (idx >= 0 && idx < maxSize) {
                    return idx;
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 0; // default to first
    }
}
