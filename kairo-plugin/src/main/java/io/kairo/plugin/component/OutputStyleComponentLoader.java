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

/** Discovers output-style contributions from {@code output-styles/*.md}. */
public final class OutputStyleComponentLoader {

    public List<PluginComponent.OutputStyleComponent> load(Path pluginRoot) throws IOException {
        Path dir = pluginRoot.resolve("output-styles");
        if (!Files.isDirectory(dir)) return List.of();

        List<PluginComponent.OutputStyleComponent> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .forEach(
                            md -> {
                                String fname = md.getFileName().toString();
                                String name = fname.substring(0, fname.length() - 3);
                                out.add(new PluginComponent.OutputStyleComponent(name, md));
                            });
        }
        return out;
    }
}
