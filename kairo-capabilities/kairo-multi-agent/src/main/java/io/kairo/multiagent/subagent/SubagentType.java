package io.kairo.multiagent.subagent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Built-in subagent types with specialized system prompts and tool whitelists.
 *
 * <p>Each type represents a different agent persona optimized for specific tasks. Models choose the
 * appropriate type based on the task at hand.
 *
 * @since 1.3
 */
public enum SubagentType {
    GENERAL_PURPOSE(
            "general-purpose",
            "General-purpose agent for complex multi-step tasks. Full tool access.",
            null,
            ""),

    EXPLORE(
            "explore",
            "Fast read-only search agent for locating code, grepping symbols, or answering 'where is X defined'. Never modifies files.",
            Set.of(
                    "bash",
                    "read",
                    "grep",
                    "glob",
                    "tree",
                    "batch_read",
                    "diff",
                    "json_query",
                    "git",
                    "web_fetch"),
            "You are a read-only exploration agent. Search and analyze code — never modify files. "
                    + "Be thorough but concise. Return structured findings the parent can act on."),

    PLAN(
            "plan",
            "Software architect agent for designing implementation plans. Read-only analysis, returns step-by-step plans.",
            Set.of(
                    "bash",
                    "read",
                    "grep",
                    "glob",
                    "tree",
                    "batch_read",
                    "diff",
                    "json_query",
                    "git"),
            "You are a planning agent. Analyze the codebase and produce a clear step-by-step "
                    + "implementation plan. Do NOT modify any files — only read and analyze."),

    CODER(
            "coder",
            "Implementation agent with full read/write capabilities for writing code and running tests.",
            null,
            "You are a coding agent. Implement the task described in the prompt. "
                    + "Write clean, correct code. Run tests after changes to verify correctness."),

    REVIEWER(
            "reviewer",
            "Code review agent. Identifies bugs, security issues, and improvements. Read-only.",
            Set.of(
                    "bash",
                    "read",
                    "grep",
                    "glob",
                    "tree",
                    "batch_read",
                    "diff",
                    "json_query",
                    "git"),
            "You are a code reviewer. Analyze code for correctness bugs, security issues, "
                    + "performance problems, and quality. Cite file paths and line numbers. Do NOT modify files.");

    private final String id;
    private final String description;
    private final Set<String> allowedTools;
    private final String systemPromptPrefix;

    SubagentType(
            String id, String description, Set<String> allowedTools, String systemPromptPrefix) {
        this.id = id;
        this.description = description;
        this.allowedTools = allowedTools;
        this.systemPromptPrefix = systemPromptPrefix;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    /**
     * Null means all tools allowed (except the subagent tool itself to prevent infinite recursion).
     */
    public Set<String> allowedTools() {
        return allowedTools;
    }

    public String systemPromptPrefix() {
        return systemPromptPrefix;
    }

    private static final Map<String, SubagentType> BY_ID;

    static {
        var map = new java.util.HashMap<String, SubagentType>();
        for (SubagentType t : values()) {
            map.put(t.id, t);
            // Short alias without hyphens
            map.put(t.id.replace("-", ""), t);
        }
        map.put("general", GENERAL_PURPOSE);
        BY_ID = Map.copyOf(map);
    }

    public static SubagentType resolve(String id) {
        if (id == null || id.isBlank()) return GENERAL_PURPOSE;
        return BY_ID.get(id.toLowerCase().trim());
    }

    public static List<String> availableIds() {
        return List.of("general-purpose", "explore", "plan", "coder", "reviewer");
    }
}
