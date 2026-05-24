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
 * Discovers executable contributions from {@code bin/}. Files (regular and executable) become
 * {@link PluginComponent.BinComponent}; the file name is the bin name.
 *
 * <p>v1.2 only catalogs the binaries — actual PATH augmentation happens in the runtime when a shell
 * tool spawns a subprocess. Symlinks are followed via {@code Files.isRegularFile} default.
 */
public final class BinComponentLoader {

    public List<PluginComponent.BinComponent> load(Path pluginRoot) throws IOException {
        Path binDir = pluginRoot.resolve("bin");
        if (!Files.isDirectory(binDir)) return List.of();

        List<PluginComponent.BinComponent> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(binDir)) {
            files.filter(Files::isRegularFile)
                    .forEach(
                            f ->
                                    out.add(
                                            new PluginComponent.BinComponent(
                                                    f.getFileName().toString(), f)));
        }
        return out;
    }
}
