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
package io.kairo.core.memory.structured;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemoryDirectoryManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryDirectoryManager.class);
    private static final String MD_SUFFIX = ".md";
    private static final String TMP_SUFFIX = ".md.tmp";
    private static final String INDEX_FILE = "MEMORY.md";
    private static final int MAX_INDEX_LINES = 200;

    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.]{1,255}$");

    private static final double HALF_LIFE_DAYS = 7.0;
    private static final double LN2 = Math.log(2.0);
    private static final double TERM_WEIGHT = 0.7;
    private static final double RECENCY_WEIGHT = 0.3;

    private final Path memoryDir;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public MemoryDirectoryManager(Path memoryDir) {
        this.memoryDir = memoryDir;
    }

    public void write(MemoryFile file) {
        validateName(file.name());
        lock.writeLock().lock();
        Path tmpFile = null;
        try {
            Files.createDirectories(memoryDir);

            String content = MemoryFileParser.serialize(file);
            Path targetFile = memoryDir.resolve(file.name() + MD_SUFFIX);
            tmpFile = memoryDir.resolve(file.name() + TMP_SUFFIX);

            Files.writeString(tmpFile, content);
            Files.move(
                    tmpFile,
                    targetFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            log.debug("Wrote memory file: {}", file.name());
            regenerateIndexInternal();
        } catch (IOException e) {
            if (tmpFile != null) {
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (IOException cleanupEx) {
                    log.warn("Failed to clean up temp file: {}", tmpFile);
                }
            }
            throw new RuntimeException("Failed to write memory file: " + file.name(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public MemoryFile read(String name) {
        validateName(name);
        lock.readLock().lock();
        try {
            Path file = memoryDir.resolve(name + MD_SUFFIX);
            if (!Files.exists(file)) {
                return null;
            }
            String content = Files.readString(file);
            Instant mtime = Files.getLastModifiedTime(file).toInstant();
            return MemoryFileParser.parse(content, mtime);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read memory file: " + name, e);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean delete(String name) {
        validateName(name);
        lock.writeLock().lock();
        try {
            Path file = memoryDir.resolve(name + MD_SUFFIX);
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.debug("Deleted memory file: {}", name);
                regenerateIndexInternal();
            }
            return deleted;
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete memory file: " + name, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<MemoryFile> listAll() {
        lock.readLock().lock();
        try {
            return scanDirectory();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<MemoryFile> listByType(MemoryType type) {
        return listAll().stream().filter(f -> f.type() == type).toList();
    }

    public List<MemoryFile> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return listAll().stream().limit(limit).toList();
        }
        Instant now = Instant.now();
        List<MemoryFile> all = listAll();
        return all.stream()
                .map(f -> new ScoredFile(f, score(f, query, now)))
                .sorted(Comparator.comparingDouble(ScoredFile::score).reversed())
                .limit(limit)
                .filter(sf -> sf.score() > 0.0)
                .map(ScoredFile::file)
                .toList();
    }

    public String loadIndex() {
        Path indexFile = memoryDir.resolve(INDEX_FILE);
        if (!Files.exists(indexFile)) {
            return "";
        }
        try {
            return Files.readString(indexFile);
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", INDEX_FILE, e.getMessage());
            return "";
        }
    }

    public void regenerateIndex() {
        lock.writeLock().lock();
        try {
            regenerateIndexInternal();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Path getMemoryDir() {
        return memoryDir;
    }

    private void regenerateIndexInternal() {
        try {
            List<MemoryFile> files = scanDirectory();
            files.sort(Comparator.comparing(MemoryFile::updatedAt).reversed());

            StringBuilder sb = new StringBuilder();
            int written = 0;
            for (MemoryFile file : files) {
                if (written >= MAX_INDEX_LINES) {
                    break;
                }
                sb.append("- [")
                        .append(formatTitle(file))
                        .append("](")
                        .append(file.name())
                        .append(".md) — ")
                        .append(file.description())
                        .append('\n');
                written++;
            }

            int remaining = files.size() - written;
            if (remaining > 0) {
                sb.append("... and ")
                        .append(remaining)
                        .append(" more memories (use memory_read to search)\n");
            }

            Path indexFile = memoryDir.resolve(INDEX_FILE);
            Path tmpFile = memoryDir.resolve(INDEX_FILE + ".tmp");
            Files.writeString(tmpFile, sb.toString());
            Files.move(
                    tmpFile,
                    indexFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Failed to regenerate {}: {}", INDEX_FILE, e.getMessage());
        }
    }

    private List<MemoryFile> scanDirectory() {
        if (!Files.exists(memoryDir)) {
            return List.of();
        }
        List<MemoryFile> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(memoryDir, "*" + MD_SUFFIX)) {
            for (Path file : stream) {
                String filename = file.getFileName().toString();
                if (filename.equals(INDEX_FILE)) {
                    continue;
                }
                try {
                    String content = Files.readString(file);
                    Instant mtime = Files.getLastModifiedTime(file).toInstant();
                    result.add(MemoryFileParser.parse(content, mtime));
                } catch (Exception e) {
                    log.warn("Failed to parse memory file {}: {}", filename, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan memory directory: {}", e.getMessage());
        }
        return result;
    }

    private static String formatTitle(MemoryFile file) {
        String name = file.name();
        String[] parts = name.split("[_\\-]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.toString();
    }

    private static double score(MemoryFile file, String query, Instant now) {
        double termOverlap = computeTermOverlap(file, query);
        double recency = computeRecency(file, now);
        return TERM_WEIGHT * termOverlap + RECENCY_WEIGHT * recency;
    }

    private static double computeTermOverlap(MemoryFile file, String query) {
        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) return 0.0;

        String text = file.description() + " " + file.body();
        Set<String> entryTerms = tokenize(text);

        long matches = queryTerms.stream().filter(entryTerms::contains).count();
        return (double) matches / queryTerms.size();
    }

    private static double computeRecency(MemoryFile file, Instant now) {
        if (file.updatedAt() == null) return 0.5;
        long ageMillis = Duration.between(file.updatedAt(), now).toMillis();
        if (ageMillis < 0) return 1.0;
        double ageDays = ageMillis / 86_400_000.0;
        return Math.exp(-LN2 * ageDays / HALF_LIFE_DAYS);
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String[] parts = text.toLowerCase().split("[\\s\\p{Punct}]+");
        Set<String> result = new HashSet<>(Arrays.asList(parts));
        result.remove("");
        return result;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Memory name cannot be null or blank");
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException(
                    "Memory name contains path traversal characters: " + name);
        }
        if (!SAFE_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Memory name contains invalid characters (only alphanumeric, hyphen, "
                            + "underscore, dot allowed): "
                            + name);
        }
    }

    private record ScoredFile(MemoryFile file, double score) {}
}
