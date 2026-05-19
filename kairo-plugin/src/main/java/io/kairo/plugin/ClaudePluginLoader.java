/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin;

import io.kairo.api.plugin.PluginComponent;
import io.kairo.api.plugin.PluginManifest;
import io.kairo.api.plugin.PluginMetadata;
import io.kairo.plugin.component.AgentComponentLoader;
import io.kairo.plugin.component.BinComponentLoader;
import io.kairo.plugin.component.CommandComponentLoader;
import io.kairo.plugin.component.OutputStyleComponentLoader;
import io.kairo.plugin.component.SkillComponentLoader;
import io.kairo.plugin.manifest.PluginManifestParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads a plugin from an on-disk directory in 100%-Claude-Code-compatible layout, producing a
 * {@link PluginManifest} that aggregates parsed metadata and every discovered component.
 *
 * <p>This loader is purely declarative: it does not register anything with runtime registries. That
 * is {@code DefaultPluginManager}'s job. Keeping the two phases separate makes it possible to
 * preview a plugin's contributions before enabling it.
 */
public final class ClaudePluginLoader {

    private static final Logger log = LoggerFactory.getLogger(ClaudePluginLoader.class);

    private final PluginManifestParser manifestParser = new PluginManifestParser();
    private final SkillComponentLoader skillLoader = new SkillComponentLoader();
    private final CommandComponentLoader commandLoader = new CommandComponentLoader();
    private final AgentComponentLoader agentLoader = new AgentComponentLoader();
    private final OutputStyleComponentLoader outputStyleLoader = new OutputStyleComponentLoader();
    private final BinComponentLoader binLoader = new BinComponentLoader();

    /**
     * Reads {@code pluginRoot} and produces a manifest. The plugin's namespace defaults to its
     * metadata name; pass {@code null} for the default behaviour.
     *
     * @throws IOException if the root directory cannot be scanned
     * @throws IllegalArgumentException if the manifest is malformed (e.g. non-exact version)
     */
    public PluginManifest load(Path pluginRoot, String namespaceOverride) throws IOException {
        if (!Files.isDirectory(pluginRoot)) {
            throw new IllegalArgumentException("Plugin root is not a directory: " + pluginRoot);
        }

        Path manifestPath = manifestParser.locateManifest(pluginRoot);
        PluginMetadata metadata =
                manifestParser.parseManifestFile(manifestPath, pluginRoot.getFileName().toString());
        String namespace = namespaceOverride == null ? metadata.name() : namespaceOverride;

        List<PluginComponent> components = new ArrayList<>();
        components.addAll(skillLoader.load(pluginRoot, namespace));
        components.addAll(commandLoader.load(pluginRoot, namespace));
        components.addAll(agentLoader.load(pluginRoot, namespace));
        components.addAll(outputStyleLoader.load(pluginRoot));
        components.addAll(binLoader.load(pluginRoot));
        // hook + mcp components are handled by Phase B loaders, not here.
        components.sort(Comparator.comparingInt(PluginComponent::order));

        Map<String, Object> mcpServers =
                manifestPath == null ? Map.of() : manifestParser.readMcpServers(manifestPath);

        log.info(
                "Loaded plugin '{}' v{} from {} with {} component(s)",
                metadata.name(),
                metadata.version(),
                pluginRoot,
                components.size());

        return new PluginManifest(metadata, pluginRoot, components, mcpServers);
    }
}
