package io.kairo.core.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Cross-platform file operation helpers. */
public final class SafeFileOps {

    private SafeFileOps() {}

    /**
     * Move a file with atomic semantics when possible, falling back to a plain replace when the
     * filesystem does not support atomic moves (e.g. cross-drive moves on Windows).
     */
    public static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
