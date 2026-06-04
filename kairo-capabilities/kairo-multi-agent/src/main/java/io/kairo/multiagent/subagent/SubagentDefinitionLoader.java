package io.kairo.multiagent.subagent;

import io.kairo.api.agent.SubagentDefinition;
import io.kairo.api.agent.SubagentRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads custom subagent definitions from markdown files and registers them into a {@link
 * SubagentRegistry}.
 *
 * <p>File format (compatible with Claude Code's {@code .claude/agents/*.md}):
 *
 * <pre>
 * ---
 * name: my-agent
 * description: Short description for model selection
 * tools: [bash, read, grep]
 * model: glm-5.1
 * ---
 *
 * You are a specialized agent that...
 * (markdown body = system prompt)
 * </pre>
 *
 * Searches:
 *
 * <ul>
 *   <li>{@code <workingDir>/.kairo/agents/} — project-level agents
 *   <li>{@code ~/.kairo/agents/} — user-level agents
 * </ul>
 *
 * @since 1.3
 */
public final class SubagentDefinitionLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SubagentDefinitionLoader.class);

    private SubagentDefinitionLoader() {}

    /**
     * Load agents from both project and user directories, register into the given registry.
     *
     * @param workingDir the project working directory
     * @param registry the registry to populate
     * @return number of agents loaded
     */
    public static int loadAndRegister(Path workingDir, SubagentRegistry registry) {
        List<SubagentDefinition> defs = loadAll(workingDir);
        int count = 0;
        for (SubagentDefinition def : defs) {
            try {
                registry.register(def);
                count++;
            } catch (IllegalStateException e) {
                LOG.debug("Skipping duplicate agent '{}': {}", def.qualifiedName(), e.getMessage());
            }
        }
        LOG.info("Loaded {} custom subagent definition(s) from .kairo/agents/", count);
        return count;
    }

    /** Load all agent definitions from project + user directories. */
    public static List<SubagentDefinition> loadAll(Path workingDir) {
        List<SubagentDefinition> results = new ArrayList<>();
        if (workingDir != null) {
            results.addAll(loadFromDir(workingDir.resolve(".kairo").resolve("agents"), null));
        }
        Path userDir = Path.of(System.getProperty("user.home"), ".kairo", "agents");
        results.addAll(loadFromDir(userDir, "user"));
        return results;
    }

    /** Load agent definitions from a single directory. */
    public static List<SubagentDefinition> loadFromDir(Path dir, String namespace) {
        if (!Files.isDirectory(dir)) return List.of();
        List<SubagentDefinition> results = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".md"))
                    .filter(Files::isRegularFile)
                    .forEach(
                            p -> {
                                try {
                                    SubagentDefinition def = parseFile(p, namespace);
                                    if (def != null) results.add(def);
                                } catch (Exception e) {
                                    LOG.warn(
                                            "Failed to parse agent file {}: {}", p, e.getMessage());
                                }
                            });
        } catch (IOException e) {
            LOG.warn("Failed to list agents dir {}: {}", dir, e.getMessage());
        }
        return results;
    }

    /** Parse a single markdown agent file into a SubagentDefinition. */
    public static SubagentDefinition parseFile(Path file, String namespace) throws IOException {
        String content = Files.readString(file);
        String fileName = file.getFileName().toString().replace(".md", "");

        if (content.startsWith("---")) {
            int endIdx = content.indexOf("---", 3);
            if (endIdx > 0) {
                String frontmatter = content.substring(3, endIdx).trim();
                String body = content.substring(endIdx + 3).trim();

                String name = extractValue(frontmatter, "name", fileName);
                String description =
                        extractValue(frontmatter, "description", "Custom agent: " + name);
                String toolsStr = extractValue(frontmatter, "tools", "");
                String model = extractValue(frontmatter, "model", null);

                List<String> tools =
                        toolsStr.isBlank()
                                ? List.of()
                                : Arrays.stream(
                                                toolsStr.replace("[", "")
                                                        .replace("]", "")
                                                        .split(","))
                                        .map(String::trim)
                                        .filter(s -> !s.isEmpty())
                                        .toList();

                return new SubagentDefinition(name, description, body, tools, model, namespace);
            }
        }

        return new SubagentDefinition(
                fileName, "Custom agent: " + fileName, content, List.of(), null, namespace);
    }

    private static String extractValue(String frontmatter, String key, String defaultVal) {
        for (String line : frontmatter.split("\n")) {
            line = line.trim();
            if (line.startsWith(key + ":")) {
                String value = line.substring(key.length() + 1).trim();
                if (value.isEmpty()) return defaultVal;
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return defaultVal;
    }
}
