/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Strategy for turning {@code (command, cwd)} into a running {@link Process}. Pulled out so tests
 * can substitute a piped fake without touching the real OS.
 */
@FunctionalInterface
public interface ProcessSpawner {

    Process spawn(List<String> command, Path workingDir) throws IOException;

    /** Default: {@link ProcessBuilder} with stderr separate so we can drain it. */
    ProcessSpawner DEFAULT =
            (command, workingDir) -> {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(workingDir.toFile());
                pb.redirectErrorStream(false);
                return pb.start();
            };
}
