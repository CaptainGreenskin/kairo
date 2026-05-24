/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.component;

import io.kairo.api.plugin.PluginComponent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Discovers skill contributions from {@code skills/<name>/SKILL.md} (preferred layout) or a
 * root-level {@code SKILL.md} (single-skill plugin).
 */
public final class SkillComponentLoader {

    public List<PluginComponent.SkillComponent> load(Path pluginRoot, String namespace)
            throws IOException {
        List<PluginComponent.SkillComponent> out = new ArrayList<>();

        Path rootSkill = pluginRoot.resolve("SKILL.md");
        if (Files.isRegularFile(rootSkill)) {
            out.add(
                    new PluginComponent.SkillComponent(
                            pluginRoot.getFileName().toString(), rootSkill, namespace));
        }

        Path skillsDir = pluginRoot.resolve("skills");
        if (Files.isDirectory(skillsDir)) {
            try (Stream<Path> dirs = Files.list(skillsDir)) {
                dirs.filter(Files::isDirectory)
                        .forEach(
                                dir -> {
                                    Path md = dir.resolve("SKILL.md");
                                    if (Files.isRegularFile(md)) {
                                        out.add(
                                                new PluginComponent.SkillComponent(
                                                        dir.getFileName().toString(),
                                                        md,
                                                        namespace));
                                    }
                                });
            }
        }
        return out;
    }
}
