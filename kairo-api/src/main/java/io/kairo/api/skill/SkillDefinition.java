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
package io.kairo.api.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Definition of a skill that can be activated by an agent.
 *
 * <p>Skills follow an explicit-trigger model to prevent prompt pollution: a skill is only activated
 * when its trigger conditions match user input with high confidence.
 *
 * <p>Supports progressive disclosure: skills can be loaded metadata-only (instructions=null) and
 * full content loaded on demand when activated.
 *
 * @param name the skill name
 * @param version the skill version
 * @param description a description of what the skill does
 * @param instructions the instructions injected into the prompt when activated (null = not loaded)
 * @param triggerConditions explicit trigger conditions (anti-pollution)
 * @param category the skill category
 * @param pathPatterns glob patterns for conditional activation (null = always eligible)
 * @param requiredTools tool names that must exist for this skill to be eligible (null = no
 *     requirement)
 * @param platform target platform: "macos", "linux", "windows" (null = any platform)
 * @param matchScore priority weight for ordering when multiple skills match (higher = preferred)
 * @param allowedTools tool names whitelist (null = all tools allowed, non-null non-empty =
 *     whitelist)
 * @param bundleRoot the bundle directory root (null = traditional single-file skill)
 */
public record SkillDefinition(
        String name,
        String version,
        String description,
        String instructions,
        List<String> triggerConditions,
        SkillCategory category,
        List<String> pathPatterns,
        List<String> requiredTools,
        String platform,
        int matchScore,
        List<String> allowedTools,
        Path bundleRoot) {

    /**
     * Backward-compatible constructor without conditional activation fields.
     *
     * <p>Sets pathPatterns=null, requiredTools=null, platform=null, matchScore=0,
     * allowedTools=null, bundleRoot=null.
     */
    public SkillDefinition(
            String name,
            String version,
            String description,
            String instructions,
            List<String> triggerConditions,
            SkillCategory category) {
        this(
                name,
                version,
                description,
                instructions,
                triggerConditions,
                category,
                null,
                null,
                null,
                0);
    }

    /**
     * Backward-compatible 10-param constructor without allowedTools.
     *
     * <p>Sets allowedTools=null, bundleRoot=null.
     */
    public SkillDefinition(
            String name,
            String version,
            String description,
            String instructions,
            List<String> triggerConditions,
            SkillCategory category,
            List<String> pathPatterns,
            List<String> requiredTools,
            String platform,
            int matchScore) {
        this(
                name,
                version,
                description,
                instructions,
                triggerConditions,
                category,
                pathPatterns,
                requiredTools,
                platform,
                matchScore,
                null,
                null);
    }

    /**
     * Backward-compatible 11-param constructor without bundleRoot.
     *
     * <p>Sets bundleRoot=null.
     */
    public SkillDefinition(
            String name,
            String version,
            String description,
            String instructions,
            List<String> triggerConditions,
            SkillCategory category,
            List<String> pathPatterns,
            List<String> requiredTools,
            String platform,
            int matchScore,
            List<String> allowedTools) {
        this(
                name,
                version,
                description,
                instructions,
                triggerConditions,
                category,
                pathPatterns,
                requiredTools,
                platform,
                matchScore,
                allowedTools,
                null);
    }

    /** Whether this skill has full instructions loaded (vs metadata-only). */
    public boolean hasInstructions() {
        return instructions != null && !instructions.isEmpty();
    }

    /** Whether this skill has conditional activation rules. */
    public boolean isConditional() {
        return (pathPatterns != null && !pathPatterns.isEmpty())
                || (requiredTools != null && !requiredTools.isEmpty())
                || platform != null;
    }

    /** Whether this skill restricts which tools can be used. */
    public boolean hasToolRestrictions() {
        return allowedTools != null && !allowedTools.isEmpty();
    }

    /** Whether this skill is a multi-file bundle. */
    public boolean isBundle() {
        return bundleRoot != null;
    }

    /**
     * Resolve a relative path within the bundle directory.
     *
     * @param relativePath the relative path to resolve
     * @return the resolved absolute path
     * @throws IllegalStateException if this is not a bundle skill
     */
    public Path resolveResource(String relativePath) {
        if (bundleRoot == null) {
            throw new IllegalStateException("Not a bundle skill: " + name());
        }
        return bundleRoot.resolve(relativePath);
    }

    /**
     * List resources in the bundle directory (excluding SKILL.md).
     *
     * @return relative paths of resources, or empty list if not a bundle
     */
    public List<String> listResources() {
        if (bundleRoot == null) return List.of();
        try (var stream = Files.walk(bundleRoot, 2)) {
            return stream.filter(p -> !p.equals(bundleRoot)
                            && !p.getFileName().toString().equals("SKILL.md"))
                    .map(p -> bundleRoot.relativize(p).toString())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Create a metadata-only copy of this skill (instructions stripped). */
    public SkillDefinition metadataOnly() {
        return new SkillDefinition(
                name,
                version,
                description,
                null,
                triggerConditions,
                category,
                pathPatterns,
                requiredTools,
                platform,
                matchScore,
                allowedTools,
                bundleRoot);
    }
}
