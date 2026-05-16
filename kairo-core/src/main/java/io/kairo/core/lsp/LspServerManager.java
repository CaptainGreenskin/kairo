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
package io.kairo.core.lsp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages language server processes and their LSP client connections.
 *
 * <p>Each workspace + language combination gets one server instance. Servers are started lazily on
 * first use and stopped on close. Crashed servers can be restarted.
 */
public final class LspServerManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LspServerManager.class);

    private final List<LspServerConfig> configs;
    private final Map<String, ManagedServer> servers = new ConcurrentHashMap<>();

    public LspServerManager(List<LspServerConfig> configs) {
        this.configs = List.copyOf(Objects.requireNonNull(configs));
    }

    public @Nullable LspClient getClient(String fileExtension, Path workspaceRoot) {
        LspServerConfig config = findConfig(fileExtension);
        if (config == null) {
            return null;
        }

        String key = config.languageId() + ":" + workspaceRoot.toAbsolutePath();
        ManagedServer managed =
                servers.computeIfAbsent(key, k -> startServer(config, workspaceRoot));

        if (managed == null) {
            return null;
        }

        if (!managed.isAlive()) {
            log.warn(
                    "LSP server '{}' crashed for workspace {}, restarting",
                    config.languageId(),
                    workspaceRoot);
            managed.close();
            managed = startServer(config, workspaceRoot);
            if (managed == null) {
                servers.remove(key);
                return null;
            }
            servers.put(key, managed);
        }

        return managed.client;
    }

    public Optional<String> detectLanguageId(String fileExtension) {
        LspServerConfig config = findConfig(fileExtension);
        return config != null ? Optional.of(config.languageId()) : Optional.empty();
    }

    public List<String> supportedExtensions() {
        List<String> exts = new ArrayList<>();
        for (LspServerConfig config : configs) {
            exts.addAll(config.fileExtensions());
        }
        return exts;
    }

    @Override
    public void close() {
        for (ManagedServer managed : servers.values()) {
            try {
                managed.close();
            } catch (Exception e) {
                log.debug("Error closing LSP server: {}", e.getMessage());
            }
        }
        servers.clear();
    }

    private @Nullable LspServerConfig findConfig(String fileExtension) {
        String ext = fileExtension.startsWith(".") ? fileExtension : "." + fileExtension;
        for (LspServerConfig config : configs) {
            if (config.fileExtensions().contains(ext)) {
                return config;
            }
        }
        return null;
    }

    private @Nullable ManagedServer startServer(LspServerConfig config, Path workspaceRoot) {
        try {
            ProcessBuilder pb =
                    new ProcessBuilder(config.command())
                            .directory(workspaceRoot.toFile())
                            .redirectError(ProcessBuilder.Redirect.PIPE);

            Process process = pb.start();
            LspTransport transport =
                    new LspTransport(process.getInputStream(), process.getOutputStream());
            LspClient client = new LspClient(transport);

            String workspaceUri = workspaceRoot.toUri().toString();
            client.initialize(workspaceUri);

            log.info(
                    "Started LSP server '{}' (pid={}) for workspace {}",
                    config.languageId(),
                    process.pid(),
                    workspaceRoot);

            return new ManagedServer(process, transport, client);
        } catch (IOException e) {
            log.warn(
                    "Failed to start LSP server '{}': {}. "
                            + "Ensure the language server binary is installed and on PATH.",
                    config.languageId(),
                    e.getMessage());
            return null;
        } catch (LspException e) {
            log.warn(
                    "LSP server '{}' initialization failed: {}",
                    config.languageId(),
                    e.getMessage());
            return null;
        }
    }

    private static final class ManagedServer {
        final Process process;
        final LspTransport transport;
        final LspClient client;

        ManagedServer(Process process, LspTransport transport, LspClient client) {
            this.process = process;
            this.transport = transport;
            this.client = client;
        }

        boolean isAlive() {
            return process.isAlive();
        }

        void close() {
            try {
                client.close();
            } catch (Exception e) {
                // best effort
            }
            process.destroyForcibly();
        }
    }
}
