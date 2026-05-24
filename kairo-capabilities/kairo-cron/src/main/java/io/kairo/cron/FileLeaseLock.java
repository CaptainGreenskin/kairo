/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.cron;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-JVM "leader-election" via a heartbeat file. Whoever wrote the most recent heartbeat (within
 * {@link #leaseTtl}) owns the lock; everyone else waits. Holder rewrites the heartbeat every {@link
 * #renewInterval} so stale leases time out automatically after a crash.
 *
 * <p>This is intentionally simple — file content is {@code "<ownerId>\n<isoTimestamp>"}. The file
 * lives at a path the caller picks (typically alongside the cron tasks JSON). No fancy file locking
 * primitives: we rely on the heartbeat timestamp + atomic rename for write safety.
 *
 * <p>Use via {@link FileLockingCronScheduler}, which holds an instance and only ticks while {@link
 * #ownsLease()} returns true.
 */
public final class FileLeaseLock {

    private static final Logger log = LoggerFactory.getLogger(FileLeaseLock.class);

    private final Path lockFile;
    private final String ownerId;
    private final Duration leaseTtl;
    private final Duration renewInterval;

    private volatile boolean ownsLease;
    private volatile Instant lastRenewedAt;

    public FileLeaseLock(Path lockFile, Duration leaseTtl, Duration renewInterval) {
        this(lockFile, UUID.randomUUID().toString(), leaseTtl, renewInterval);
    }

    public FileLeaseLock(Path lockFile, String ownerId, Duration leaseTtl, Duration renewInterval) {
        this.lockFile = lockFile;
        this.ownerId = ownerId;
        this.leaseTtl = leaseTtl;
        this.renewInterval = renewInterval;
    }

    /** Returns true when this owner currently holds the lease. */
    public boolean ownsLease() {
        return ownsLease;
    }

    public String ownerId() {
        return ownerId;
    }

    public Path lockFile() {
        return lockFile;
    }

    /**
     * Try to acquire — or, if we already hold it, renew — the lease. Should be called once per
     * tick. Returns whether we hold the lease after the call.
     */
    public synchronized boolean tryAcquireOrRenew() {
        try {
            FileLockState current = readState();
            Instant now = Instant.now();
            if (current == null) {
                writeState(now);
                ownsLease = true;
                lastRenewedAt = now;
                log.info("Acquired empty cron lease at {} (owner={})", lockFile, ownerId);
                return true;
            }
            if (ownerId.equals(current.owner)) {
                // It's ours. Only re-write when the renew interval has elapsed (cuts IO).
                if (lastRenewedAt == null
                        || java.time.Duration.between(lastRenewedAt, now).compareTo(renewInterval)
                                >= 0) {
                    writeState(now);
                    lastRenewedAt = now;
                }
                ownsLease = true;
                return true;
            }
            // Someone else holds it. Take over only if their lease has expired.
            java.time.Duration age = java.time.Duration.between(current.heartbeat, now);
            if (age.compareTo(leaseTtl) > 0) {
                writeState(now);
                ownsLease = true;
                lastRenewedAt = now;
                log.warn(
                        "Took over expired cron lease from {} (idle for {}); new owner={}",
                        current.owner,
                        age,
                        ownerId);
                return true;
            }
            ownsLease = false;
            return false;
        } catch (IOException e) {
            log.warn("Cron lease IO failed at {}: {}", lockFile, e.getMessage());
            // On IO failure: keep whatever state we had so a single hiccup doesn't yank the lease
            // away from the current holder.
            return ownsLease;
        }
    }

    /** Release the lease if we hold it. Best-effort. */
    public synchronized void release() {
        if (!ownsLease) return;
        try {
            FileLockState current = readState();
            if (current != null && ownerId.equals(current.owner)) {
                Files.deleteIfExists(lockFile);
                log.info("Released cron lease at {} (owner={})", lockFile, ownerId);
            }
        } catch (IOException e) {
            log.debug("Lease release IO failed at {}: {}", lockFile, e.getMessage());
        } finally {
            ownsLease = false;
        }
    }

    private FileLockState readState() throws IOException {
        try {
            String content = Files.readString(lockFile, StandardCharsets.UTF_8).trim();
            if (content.isBlank()) return null;
            int newline = content.indexOf('\n');
            if (newline < 0) return null;
            String owner = content.substring(0, newline).trim();
            String iso = content.substring(newline + 1).trim();
            return new FileLockState(owner, Instant.parse(iso));
        } catch (NoSuchFileException e) {
            return null;
        } catch (RuntimeException e) {
            log.warn("Cron lease file at {} unparseable; treating as empty", lockFile);
            return null;
        }
    }

    private void writeState(Instant when) throws IOException {
        if (lockFile.getParent() != null) Files.createDirectories(lockFile.getParent());
        String payload = ownerId + "\n" + when;
        Path tmp = lockFile.resolveSibling(lockFile.getFileName() + ".tmp");
        Files.writeString(
                tmp,
                payload,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        Files.move(
                tmp,
                lockFile,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    private record FileLockState(String owner, Instant heartbeat) {}
}
