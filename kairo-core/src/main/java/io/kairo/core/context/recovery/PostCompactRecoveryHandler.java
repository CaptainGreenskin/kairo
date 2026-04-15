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
package io.kairo.core.context.recovery;

import io.kairo.api.context.McpInstructionProvider;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles post-compaction context recovery.
 *
 * <p>After compaction compresses the conversation, this handler restores critical context:
 *
 * <ol>
 *   <li>Re-reads recently accessed files (up to 5 files, 5K tokens each)
 *   <li>Re-injects active skill instructions (5K tokens each)
 *   <li>Re-injects MCP server instructions (extensible)
 * </ol>
 *
 * <p>Total recovery budget: 50K tokens across all injections. Priority: files &gt; skills &gt; MCP.
 */
public class PostCompactRecoveryHandler {

    private static final Logger log = LoggerFactory.getLogger(PostCompactRecoveryHandler.class);

    private static final int TOTAL_BUDGET = 50_000; // 50K token budget
    private static final int PER_FILE_BUDGET = 5_000; // 5K per file
    private static final int PER_SKILL_BUDGET = 5_000; // 5K per skill

    private final FileAccessTracker fileTracker;
    private final SkillRegistry skillRegistry; // nullable
    private final McpInstructionProvider mcpProvider;

    /**
     * Create a new recovery handler.
     *
     * @param fileTracker tracks recently accessed files
     * @param skillRegistry the skill registry (may be null)
     * @param mcpProvider the MCP instruction provider (may be null, defaults to noop)
     */
    public PostCompactRecoveryHandler(
            FileAccessTracker fileTracker,
            SkillRegistry skillRegistry,
            McpInstructionProvider mcpProvider) {
        this.fileTracker = fileTracker;
        this.skillRegistry = skillRegistry;
        this.mcpProvider = mcpProvider != null ? mcpProvider : McpInstructionProvider.noop();
    }

    /**
     * Execute recovery after compaction.
     *
     * @return list of recovery messages to inject into the compacted conversation
     */
    public List<Msg> recover() {
        List<Msg> recoveryMessages = new ArrayList<>();
        int budgetUsed = 0;

        // 1. File re-read (highest priority)
        for (String filePath : fileTracker.getRecentFiles()) {
            if (budgetUsed >= TOTAL_BUDGET) {
                break;
            }
            try {
                String content = readFileContent(filePath);
                String truncated = truncateToTokenBudget(content, PER_FILE_BUDGET);
                if (!truncated.isEmpty()) {
                    Msg msg =
                            Msg.builder()
                                    .role(MsgRole.USER)
                                    .addContent(
                                            new io.kairo.api.message.Content.TextContent(
                                                    "<memory-context>\n"
                                                            + "[Context Recovery] Re-reading "
                                                            + filePath
                                                            + ":\n"
                                                            + truncated
                                                            + "\n</memory-context>"))
                                    .metadata("recovery", true)
                                    .metadata("recoveryType", "file")
                                    .build();
                    recoveryMessages.add(msg);
                    budgetUsed += estimateTokens(truncated);
                }
            } catch (IOException e) {
                log.debug("Skipping file recovery for {} — {}", filePath, e.getMessage());
            }
        }

        // 2. Skill re-injection
        if (skillRegistry != null && budgetUsed < TOTAL_BUDGET) {
            for (SkillDefinition skill : skillRegistry.list()) {
                if (budgetUsed >= TOTAL_BUDGET) {
                    break;
                }
                String instructions = skill.instructions();
                if (instructions != null && !instructions.isEmpty()) {
                    String truncated = truncateToTokenBudget(instructions, PER_SKILL_BUDGET);
                    Msg msg =
                            Msg.builder()
                                    .role(MsgRole.SYSTEM)
                                    .addContent(
                                            new io.kairo.api.message.Content.TextContent(
                                                    "[Skill Recovery] "
                                                            + skill.name()
                                                            + ":\n"
                                                            + truncated))
                                    .metadata("recovery", true)
                                    .metadata("recoveryType", "skill")
                                    .build();
                    recoveryMessages.add(msg);
                    budgetUsed += estimateTokens(truncated);
                }
            }
        }

        // 3. MCP instruction re-injection (lowest priority)
        if (budgetUsed < TOTAL_BUDGET) {
            for (String instruction : mcpProvider.getActiveInstructions()) {
                if (budgetUsed >= TOTAL_BUDGET) {
                    break;
                }
                Msg msg =
                        Msg.builder()
                                .role(MsgRole.SYSTEM)
                                .addContent(
                                        new io.kairo.api.message.Content.TextContent(
                                                "[MCP Recovery] " + instruction))
                                .metadata("recovery", true)
                                .metadata("recoveryType", "mcp")
                                .build();
                recoveryMessages.add(msg);
                budgetUsed += estimateTokens(instruction);
            }
        }

        log.info(
                "Post-compact recovery: injected {} messages using ~{} tokens",
                recoveryMessages.size(),
                budgetUsed);
        return recoveryMessages;
    }

    private String readFileContent(String filePath) throws IOException {
        return Files.readString(Path.of(filePath));
    }

    /**
     * Truncate content to fit within a token budget. Uses a conservative estimate of ~3 chars per
     * token.
     */
    private String truncateToTokenBudget(String content, int tokenBudget) {
        int charBudget = tokenBudget * 3; // conservative: ~3 chars per token
        if (content.length() <= charBudget) {
            return content;
        }
        return content.substring(0, charBudget) + "\n... [truncated to " + tokenBudget + " tokens]";
    }

    /** Estimate token count from text length. Uses ~4 chars per 3 tokens heuristic. */
    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 3.0);
    }
}
