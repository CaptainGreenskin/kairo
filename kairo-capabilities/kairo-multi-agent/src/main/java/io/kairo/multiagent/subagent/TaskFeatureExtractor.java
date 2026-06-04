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
package io.kairo.multiagent.subagent;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts structured {@link TaskFeatures} from free-text task descriptions using keyword
 * dictionaries.
 */
public final class TaskFeatureExtractor {

    private TaskFeatureExtractor() {}

    private static final Pattern WORD_BOUNDARY = Pattern.compile("[\\s,;:!?()\\[\\]{}/|]+");

    private static final Map<String, String> LANGUAGE_KEYWORDS =
            Map.ofEntries(
                    Map.entry("java", "java"),
                    Map.entry("python", "python"),
                    Map.entry("javascript", "javascript"),
                    Map.entry("js", "javascript"),
                    Map.entry("typescript", "typescript"),
                    Map.entry("ts", "typescript"),
                    Map.entry("go", "go"),
                    Map.entry("golang", "go"),
                    Map.entry("rust", "rust"),
                    Map.entry("kotlin", "kotlin"),
                    Map.entry("scala", "scala"),
                    Map.entry("ruby", "ruby"),
                    Map.entry("php", "php"),
                    Map.entry("c++", "cpp"),
                    Map.entry("cpp", "cpp"),
                    Map.entry("c#", "csharp"),
                    Map.entry("csharp", "csharp"),
                    Map.entry("swift", "swift"),
                    Map.entry("sql", "sql"),
                    Map.entry("html", "html"),
                    Map.entry("css", "css"),
                    Map.entry("shell", "shell"),
                    Map.entry("bash", "shell"),
                    Map.entry("yaml", "yaml"),
                    Map.entry("json", "json"));

    private static final Map<String, String> FRAMEWORK_KEYWORDS =
            Map.ofEntries(
                    Map.entry("spring", "spring"),
                    Map.entry("springboot", "spring"),
                    Map.entry("react", "react"),
                    Map.entry("reactjs", "react"),
                    Map.entry("vue", "vue"),
                    Map.entry("vuejs", "vue"),
                    Map.entry("angular", "angular"),
                    Map.entry("django", "django"),
                    Map.entry("flask", "flask"),
                    Map.entry("express", "express"),
                    Map.entry("nextjs", "nextjs"),
                    Map.entry("next.js", "nextjs"),
                    Map.entry("gradle", "gradle"),
                    Map.entry("maven", "maven"),
                    Map.entry("docker", "docker"),
                    Map.entry("kubernetes", "kubernetes"),
                    Map.entry("k8s", "kubernetes"),
                    Map.entry("terraform", "terraform"),
                    Map.entry("junit", "junit"),
                    Map.entry("mockito", "mockito"),
                    Map.entry("reactor", "reactor"),
                    Map.entry("webflux", "reactor"),
                    Map.entry("jackson", "jackson"),
                    Map.entry("hibernate", "hibernate"),
                    Map.entry("jpa", "jpa"),
                    Map.entry("mybatis", "mybatis"),
                    Map.entry("redis", "redis"),
                    Map.entry("kafka", "kafka"),
                    Map.entry("graphql", "graphql"),
                    Map.entry("grpc", "grpc"),
                    Map.entry("opentelemetry", "opentelemetry"),
                    Map.entry("prometheus", "prometheus"),
                    Map.entry("grafana", "grafana"));

    private static final Map<String, String> DOMAIN_KEYWORDS =
            Map.ofEntries(
                    Map.entry("test", "testing"),
                    Map.entry("tests", "testing"),
                    Map.entry("testing", "testing"),
                    Map.entry("unittest", "testing"),
                    Map.entry("security", "security"),
                    Map.entry("auth", "security"),
                    Map.entry("authentication", "security"),
                    Map.entry("authorization", "security"),
                    Map.entry("oauth", "security"),
                    Map.entry("jwt", "security"),
                    Map.entry("database", "database"),
                    Map.entry("db", "database"),
                    Map.entry("migration", "database"),
                    Map.entry("schema", "database"),
                    Map.entry("api", "api"),
                    Map.entry("rest", "api"),
                    Map.entry("endpoint", "api"),
                    Map.entry("devops", "devops"),
                    Map.entry("ci", "devops"),
                    Map.entry("cd", "devops"),
                    Map.entry("pipeline", "devops"),
                    Map.entry("deploy", "devops"),
                    Map.entry("deployment", "devops"),
                    Map.entry("monitoring", "observability"),
                    Map.entry("observability", "observability"),
                    Map.entry("logging", "observability"),
                    Map.entry("tracing", "observability"),
                    Map.entry("metrics", "observability"),
                    Map.entry("performance", "performance"),
                    Map.entry("optimization", "performance"),
                    Map.entry("latency", "performance"),
                    Map.entry("frontend", "frontend"),
                    Map.entry("ui", "frontend"),
                    Map.entry("backend", "backend"),
                    Map.entry("refactor", "refactoring"),
                    Map.entry("refactoring", "refactoring"),
                    Map.entry("documentation", "documentation"),
                    Map.entry("docs", "documentation"),
                    Map.entry("architecture", "architecture"),
                    Map.entry("design", "architecture"));

    private static final Map<String, String> ACTION_KEYWORDS =
            Map.ofEntries(
                    Map.entry("implement", "implement"),
                    Map.entry("add", "implement"),
                    Map.entry("create", "implement"),
                    Map.entry("build", "implement"),
                    Map.entry("write", "implement"),
                    Map.entry("develop", "implement"),
                    Map.entry("fix", "debug"),
                    Map.entry("bug", "debug"),
                    Map.entry("debug", "debug"),
                    Map.entry("troubleshoot", "debug"),
                    Map.entry("investigate", "research"),
                    Map.entry("research", "research"),
                    Map.entry("analyze", "research"),
                    Map.entry("explore", "research"),
                    Map.entry("review", "review"),
                    Map.entry("audit", "review"),
                    Map.entry("inspect", "review"),
                    Map.entry("test", "test"),
                    Map.entry("tests", "test"),
                    Map.entry("verify", "test"),
                    Map.entry("validate", "test"),
                    Map.entry("design", "design"),
                    Map.entry("architect", "design"),
                    Map.entry("plan", "design"),
                    Map.entry("integrate", "integrate"),
                    Map.entry("merge", "integrate"),
                    Map.entry("combine", "integrate"),
                    Map.entry("summarize", "synthesize"),
                    Map.entry("report", "synthesize"),
                    Map.entry("document", "synthesize"));

    /**
     * Extract structured features from a task description.
     *
     * @param text the task description or goal text
     * @return extracted features; never null
     */
    public static TaskFeatures extract(String text) {
        if (text == null || text.isBlank()) {
            return new TaskFeatures(Set.of(), Set.of(), Set.of(), Set.of());
        }

        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> tokens = tokenize(lower);

        Set<String> languages = match(tokens, LANGUAGE_KEYWORDS);
        Set<String> frameworks = match(tokens, FRAMEWORK_KEYWORDS);
        Set<String> domains = match(tokens, DOMAIN_KEYWORDS);
        Set<String> actions = match(tokens, ACTION_KEYWORDS);

        // Also check multi-word patterns in the raw lowercase text
        matchPhrases(lower, FRAMEWORK_KEYWORDS, frameworks);
        matchPhrases(lower, DOMAIN_KEYWORDS, domains);

        return new TaskFeatures(languages, frameworks, domains, actions);
    }

    private static Set<String> tokenize(String lower) {
        String[] parts = WORD_BOUNDARY.split(lower);
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : parts) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private static Set<String> match(Set<String> tokens, Map<String, String> dictionary) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : tokens) {
            String mapped = dictionary.get(token);
            if (mapped != null) {
                result.add(mapped);
            }
        }
        return result;
    }

    private static void matchPhrases(
            String text, Map<String, String> dictionary, Set<String> result) {
        for (Map.Entry<String, String> entry : dictionary.entrySet()) {
            if (entry.getKey().contains(".") || entry.getKey().contains(" ")) {
                if (text.contains(entry.getKey())) {
                    result.add(entry.getValue());
                }
            }
        }
    }
}
