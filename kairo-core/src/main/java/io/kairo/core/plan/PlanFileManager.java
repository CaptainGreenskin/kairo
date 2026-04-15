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
package io.kairo.core.plan;

import io.kairo.api.plan.PlanFile;
import io.kairo.api.plan.PlanStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages plan files on disk under {@code .kairo/plans/}.
 *
 * <p>Each plan is persisted as a Markdown file with YAML front-matter containing metadata (id,
 * name, status, createdAt) followed by the plan content.
 */
public class PlanFileManager {

    private static final Logger log = LoggerFactory.getLogger(PlanFileManager.class);
    private final Path plansDir;

    /**
     * Create a new manager rooted at the given working directory.
     *
     * @param workingDir the project working directory
     */
    public PlanFileManager(Path workingDir) {
        this.plansDir = workingDir.resolve(".kairo").resolve("plans");
    }

    /**
     * Create a new plan in DRAFT status.
     *
     * @param name the plan name
     * @return the created plan file
     */
    public PlanFile createPlan(String name) {
        ensureDir();
        String id = UUID.randomUUID().toString().substring(0, 8);
        var plan = new PlanFile(id, name, "", Instant.now(), PlanStatus.DRAFT);
        savePlan(plan);
        log.info("Created plan: {} ({})", name, id);
        return plan;
    }

    /**
     * Get a plan by its ID.
     *
     * @param id the plan identifier
     * @return the plan file, or null if not found
     */
    public PlanFile getPlan(String id) {
        Path file = plansDir.resolve(id + ".md");
        if (!Files.exists(file)) {
            return null;
        }
        return parsePlanFile(file);
    }

    /**
     * Update an existing plan's content and status.
     *
     * @param id the plan identifier
     * @param content the new plan content
     * @param status the new plan status
     * @return the updated plan file
     * @throws IllegalArgumentException if the plan is not found
     */
    public PlanFile updatePlan(String id, String content, PlanStatus status) {
        var existing = getPlan(id);
        if (existing == null) {
            throw new IllegalArgumentException("Plan not found: " + id);
        }
        var updated = new PlanFile(id, existing.name(), content, existing.createdAt(), status);
        savePlan(updated);
        return updated;
    }

    /**
     * List all plans, sorted by creation time descending (newest first).
     *
     * @return the list of plans
     */
    public List<PlanFile> listPlans() {
        ensureDir();
        try (var stream = Files.list(plansDir)) {
            return stream.filter(p -> p.toString().endsWith(".md"))
                    .map(this::parsePlanFile)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(PlanFile::createdAt).reversed())
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list plans", e);
            return List.of();
        }
    }

    /**
     * Delete a plan by its ID.
     *
     * @param id the plan identifier
     * @return true if the plan was deleted
     */
    public boolean deletePlan(String id) {
        try {
            return Files.deleteIfExists(plansDir.resolve(id + ".md"));
        } catch (IOException e) {
            log.error("Failed to delete plan: {}", id, e);
            return false;
        }
    }

    private void savePlan(PlanFile plan) {
        ensureDir();
        String fileContent =
                "---\n"
                        + "id: "
                        + plan.id()
                        + "\n"
                        + "name: "
                        + plan.name()
                        + "\n"
                        + "status: "
                        + plan.status()
                        + "\n"
                        + "createdAt: "
                        + plan.createdAt()
                        + "\n"
                        + "---\n"
                        + plan.content();
        try {
            Files.writeString(plansDir.resolve(plan.id() + ".md"), fileContent);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save plan: " + plan.id(), e);
        }
    }

    private PlanFile parsePlanFile(Path file) {
        try {
            String raw = Files.readString(file);
            if (!raw.startsWith("---")) {
                return null;
            }
            int endIndex = raw.indexOf("---", 3);
            if (endIndex < 0) {
                return null;
            }
            String frontMatter = raw.substring(3, endIndex).trim();
            String content = raw.substring(endIndex + 3);
            if (content.startsWith("\n")) {
                content = content.substring(1);
            }

            String id = null;
            String name = null;
            PlanStatus status = PlanStatus.DRAFT;
            Instant createdAt = Instant.now();

            for (String line : frontMatter.split("\n")) {
                int colonIdx = line.indexOf(':');
                if (colonIdx < 0) continue;
                String key = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                switch (key) {
                    case "id" -> id = value;
                    case "name" -> name = value;
                    case "status" -> {
                        try {
                            status = PlanStatus.valueOf(value);
                        } catch (IllegalArgumentException ignored) {
                            // keep default
                        }
                    }
                    case "createdAt" -> {
                        try {
                            createdAt = Instant.parse(value);
                        } catch (Exception ignored) {
                            // keep default
                        }
                    }
                    default -> {
                        /* ignore unknown keys */
                    }
                }
            }

            if (id == null || name == null) {
                log.warn("Plan file missing required fields: {}", file);
                return null;
            }
            return new PlanFile(id, name, content, createdAt, status);
        } catch (IOException e) {
            log.error("Failed to read plan file: {}", file, e);
            return null;
        }
    }

    private void ensureDir() {
        try {
            Files.createDirectories(plansDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
