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
package io.kairo.core.agent.checkpoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.agent.IterationCheckpoint;
import io.kairo.api.agent.IterationCheckpointStore;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.session.SessionSerializer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * File-based {@link IterationCheckpointStore} that persists iteration checkpoints as JSON files.
 *
 * <p>Each checkpoint is stored as two files:
 *
 * <ul>
 *   <li>{@code iteration-{N}.json} — lightweight metadata (iteration index, timestamp, message
 *       count)
 *   <li>{@code iteration-{N}-messages.json} — full message snapshot
 * </ul>
 *
 * <p>Only the latest {@code maxRetained} checkpoints are kept; older files are pruned automatically
 * after each save. Blocking I/O is offloaded to {@link Schedulers#boundedElastic()}.
 *
 * <p>Reuses the pre-configured {@link ObjectMapper} from {@link SessionSerializer} for consistent
 * JSON serialization.
 */
public final class JsonFileIterationCheckpointStore implements IterationCheckpointStore {

    private static final Logger log =
            LoggerFactory.getLogger(JsonFileIterationCheckpointStore.class);
    private static final String META_SUFFIX = ".json";
    private static final String MESSAGES_SUFFIX = "-messages.json";
    private static final String ITERATION_PREFIX = "iteration-";

    // Retained checkpoints double as rewind targets, so the default is generous enough for a
    // meaningful rewind depth (was 3, which only covered crash recovery). Override with
    // KAIRO_ITERATION_CHECKPOINT_RETENTION.
    private static final int DEFAULT_MAX_RETAINED = resolveDefaultRetention();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static int resolveDefaultRetention() {
        String env = System.getenv("KAIRO_ITERATION_CHECKPOINT_RETENTION");
        if (env != null && !env.isBlank()) {
            try {
                int parsed = Integer.parseInt(env.trim());
                if (parsed >= 1) return parsed;
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return 20;
    }

    private final Path storageDir;
    private final ObjectMapper objectMapper;
    private final int maxRetained;

    /**
     * Creates a new {@code JsonFileIterationCheckpointStore} with default retention (3
     * checkpoints).
     *
     * @param storageDir the directory to store checkpoint files
     * @param sessionSerializer the serializer whose {@link ObjectMapper} will be reused
     * @throws IllegalArgumentException if any parameter is null
     */
    public JsonFileIterationCheckpointStore(Path storageDir, SessionSerializer sessionSerializer) {
        this(storageDir, sessionSerializer, DEFAULT_MAX_RETAINED);
    }

    /**
     * Creates a new {@code JsonFileIterationCheckpointStore}.
     *
     * @param storageDir the directory to store checkpoint files
     * @param sessionSerializer the serializer whose {@link ObjectMapper} will be reused
     * @param maxRetained maximum number of checkpoints to retain (older ones are pruned)
     * @throws IllegalArgumentException if storageDir or sessionSerializer is null, or maxRetained <
     *     1
     */
    public JsonFileIterationCheckpointStore(
            Path storageDir, SessionSerializer sessionSerializer, int maxRetained) {
        if (storageDir == null) {
            throw new IllegalArgumentException("storageDir must not be null");
        }
        if (sessionSerializer == null) {
            throw new IllegalArgumentException("sessionSerializer must not be null");
        }
        if (maxRetained < 1) {
            throw new IllegalArgumentException("maxRetained must be >= 1");
        }
        this.storageDir = storageDir;
        this.objectMapper = sessionSerializer.objectMapper();
        this.maxRetained = maxRetained;
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to create checkpoint storage directory: " + storageDir, e);
        }
    }

    @Override
    public Mono<Void> save(int iteration, List<Msg> messages) {
        return Mono.fromCallable(
                        () -> {
                            Instant now = Instant.now();

                            // Write messages file
                            Path messagesFile = resolveMessagesFile(iteration);
                            String messagesJson = serializeMessages(messages);
                            Files.writeString(messagesFile, messagesJson);

                            // Write metadata file
                            Path metaFile = resolveMetaFile(iteration);
                            String metaJson = serializeMeta(iteration, now, messages.size());
                            Files.writeString(metaFile, metaJson);

                            log.debug(
                                    "Saved iteration checkpoint {} ({} messages) to {}",
                                    iteration,
                                    messages.size(),
                                    storageDir);

                            // Prune old checkpoints
                            pruneOldCheckpoints(iteration);

                            return (Void) null;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Optional<IterationCheckpoint>> loadLast() {
        return Mono.<Optional<IterationCheckpoint>>fromCallable(
                        () -> {
                            if (!Files.exists(storageDir)) {
                                return Optional.<IterationCheckpoint>empty();
                            }

                            // Find the highest iteration number
                            try (Stream<Path> stream = Files.list(storageDir)) {
                                Optional<Integer> maxIteration =
                                        stream.filter(
                                                        p -> {
                                                            String name =
                                                                    p.getFileName().toString();
                                                            return name.startsWith(ITERATION_PREFIX)
                                                                    && name.endsWith(META_SUFFIX)
                                                                    && !name.contains(
                                                                            MESSAGES_SUFFIX);
                                                        })
                                                .map(
                                                        p -> {
                                                            String name =
                                                                    p.getFileName().toString();
                                                            return parseIterationNumber(name);
                                                        })
                                                .filter(i -> i >= 0)
                                                .max(Comparator.naturalOrder());

                                if (maxIteration.isEmpty()) {
                                    return Optional.<IterationCheckpoint>empty();
                                }

                                int iteration = maxIteration.get();
                                return loadCheckpoint(iteration);
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteAll() {
        return Mono.fromRunnable(
                        () -> {
                            if (!Files.exists(storageDir)) {
                                return;
                            }
                            try (Stream<Path> stream = Files.list(storageDir)) {
                                stream.filter(
                                                p -> {
                                                    String name = p.getFileName().toString();
                                                    return name.startsWith(ITERATION_PREFIX)
                                                            && (name.endsWith(META_SUFFIX)
                                                                    || name.endsWith(
                                                                            MESSAGES_SUFFIX));
                                                })
                                        .forEach(
                                                p -> {
                                                    try {
                                                        Files.deleteIfExists(p);
                                                    } catch (IOException e) {
                                                        log.warn(
                                                                "Failed to delete checkpoint file {}: {}",
                                                                p,
                                                                e.getMessage());
                                                    }
                                                });
                            } catch (IOException e) {
                                log.warn(
                                        "Failed to list checkpoint files for deletion: {}",
                                        e.getMessage());
                            }
                            log.debug("Deleted all iteration checkpoints from {}", storageDir);
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * Load a specific iteration checkpoint by index — the rewind counterpart to {@link
     * #loadLast()}.
     *
     * <p>Not part of the {@link IterationCheckpointStore} SPI (which only exposes {@code
     * loadLast}); callers that need a specific iteration hold the concrete type. Returns empty if
     * that checkpoint no longer exists (e.g. it was pruned beyond the retention window).
     *
     * @param iteration the iteration index to load
     * @return a Mono emitting the checkpoint, or empty if absent
     */
    public Mono<Optional<IterationCheckpoint>> loadAt(int iteration) {
        return Mono.fromCallable(() -> loadCheckpoint(iteration))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Delete every checkpoint with an iteration index greater than {@code iteration}. Used by
     * rewind so that {@link #loadLast()} subsequently returns the rewind target and a resumed run
     * continues from there rather than from the (now discarded) later turns.
     *
     * @param iteration the highest iteration to keep
     * @return a Mono completing when the later checkpoints are removed
     */
    public Mono<Void> deleteAfter(int iteration) {
        return Mono.fromRunnable(
                        () -> {
                            try {
                                for (int it : listAllIterations()) {
                                    if (it > iteration) {
                                        deleteCheckpointFiles(it);
                                    }
                                }
                            } catch (IOException e) {
                                log.warn(
                                        "Failed to delete checkpoints after {}: {}",
                                        iteration,
                                        e.getMessage());
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Optional<IterationCheckpoint> loadCheckpoint(int iteration) {
        try {
            Path metaFile = resolveMetaFile(iteration);
            Path messagesFile = resolveMessagesFile(iteration);

            if (!Files.exists(metaFile) || !Files.exists(messagesFile)) {
                return Optional.empty();
            }

            String metaJson = Files.readString(metaFile);
            Map<String, Object> meta = objectMapper.readValue(metaJson, MAP_TYPE);

            Instant timestamp =
                    meta.containsKey("timestamp")
                            ? Instant.parse((String) meta.get("timestamp"))
                            : Instant.now();

            String messagesJson = Files.readString(messagesFile);
            List<Msg> messages = deserializeMessages(messagesJson);

            return Optional.of(new IterationCheckpoint(iteration, messages, timestamp));
        } catch (IOException e) {
            log.warn("Failed to load iteration checkpoint {}: {}", iteration, e.getMessage());
            return Optional.empty();
        }
    }

    private void pruneOldCheckpoints(int currentIteration) {
        try {
            List<Integer> iterations = listAllIterations();
            if (iterations.size() <= maxRetained) {
                return;
            }

            // Keep the maxRetained highest iteration numbers
            iterations.sort(Comparator.naturalOrder());
            int pruneUpTo = iterations.size() - maxRetained;
            for (int i = 0; i < pruneUpTo; i++) {
                int toPrune = iterations.get(i);
                deleteCheckpointFiles(toPrune);
            }

            log.debug(
                    "Pruned {} old checkpoint(s), retained {} (max: {})",
                    pruneUpTo,
                    maxRetained,
                    maxRetained);
        } catch (IOException e) {
            log.warn("Failed to prune old checkpoints: {}", e.getMessage());
        }
    }

    private List<Integer> listAllIterations() throws IOException {
        if (!Files.exists(storageDir)) {
            return new ArrayList<>();
        }
        try (Stream<Path> stream = Files.list(storageDir)) {
            return new ArrayList<>(
                    stream.filter(
                                    p -> {
                                        String name = p.getFileName().toString();
                                        return name.startsWith(ITERATION_PREFIX)
                                                && name.endsWith(META_SUFFIX)
                                                && !name.contains(MESSAGES_SUFFIX);
                                    })
                            .map(p -> parseIterationNumber(p.getFileName().toString()))
                            .filter(i -> i >= 0)
                            .toList());
        }
    }

    private void deleteCheckpointFiles(int iteration) {
        try {
            Files.deleteIfExists(resolveMetaFile(iteration));
            Files.deleteIfExists(resolveMessagesFile(iteration));
        } catch (IOException e) {
            log.warn(
                    "Failed to delete checkpoint files for iteration {}: {}",
                    iteration,
                    e.getMessage());
        }
    }

    private int parseIterationNumber(String filename) {
        try {
            String num =
                    filename.substring(ITERATION_PREFIX.length(), filename.indexOf(META_SUFFIX));
            return Integer.parseInt(num);
        } catch (Exception e) {
            return -1;
        }
    }

    private Path resolveMetaFile(int iteration) {
        return storageDir.resolve(ITERATION_PREFIX + iteration + META_SUFFIX);
    }

    private Path resolveMessagesFile(int iteration) {
        return storageDir.resolve(ITERATION_PREFIX + iteration + MESSAGES_SUFFIX);
    }

    private String serializeMeta(int iteration, Instant timestamp, int messageCount) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("iteration", iteration);
            meta.put("timestamp", timestamp.toString());
            meta.put("messageCount", messageCount);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(meta);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize iteration meta", e);
        }
    }

    private String serializeMessages(List<Msg> messages) {
        try {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (Msg msg : messages) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", msg.id());
                map.put("role", msg.role().name());
                map.put("timestamp", msg.timestamp().toString());
                map.put("tokenCount", msg.tokenCount());
                map.put("verbatimPreserved", msg.verbatimPreserved());
                if (msg.sourceAgentId() != null) {
                    map.put("sourceAgentId", msg.sourceAgentId());
                }
                map.put("metadata", msg.metadata());
                map.put("contents", serializeContents(msg.contents()));
                serialized.add(map);
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(serialized);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize messages", e);
        }
    }

    private List<Map<String, Object>> serializeContents(List<Content> contents) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Content content : contents) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (content instanceof Content.TextContent tc) {
                map.put("type", "text");
                map.put("text", tc.text());
            } else if (content instanceof Content.ImageContent ic) {
                map.put("type", "image");
                map.put("url", ic.url());
                map.put("mediaType", ic.mediaType());
            } else if (content instanceof Content.ToolUseContent tuc) {
                map.put("type", "tool_use");
                map.put("toolId", tuc.toolId());
                map.put("toolName", tuc.toolName());
                map.put("input", tuc.input());
            } else if (content instanceof Content.ToolResultContent trc) {
                map.put("type", "tool_result");
                map.put("toolUseId", trc.toolUseId());
                map.put("content", trc.content());
                map.put("isError", trc.isError());
            } else if (content instanceof Content.ThinkingContent thc) {
                map.put("type", "thinking");
                map.put("thinking", thc.thinking());
                map.put("budgetTokens", thc.budgetTokens());
                if (thc.signature() != null) {
                    map.put("signature", thc.signature());
                }
            }
            result.add(map);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Msg> deserializeMessages(String json) {
        try {
            List<Map<String, Object>> rawMessages =
                    objectMapper.readValue(json, new TypeReference<>() {});
            List<Msg> result = new ArrayList<>();
            for (Map<String, Object> raw : rawMessages) {
                Msg.Builder builder = Msg.builder();
                if (raw.containsKey("id")) {
                    builder.id((String) raw.get("id"));
                }
                builder.role(MsgRole.valueOf((String) raw.get("role")));
                if (raw.containsKey("timestamp")) {
                    builder.timestamp(Instant.parse((String) raw.get("timestamp")));
                }
                if (raw.containsKey("tokenCount")) {
                    builder.tokenCount(((Number) raw.get("tokenCount")).intValue());
                }
                if (raw.containsKey("verbatimPreserved")) {
                    builder.verbatimPreserved((Boolean) raw.get("verbatimPreserved"));
                }
                if (raw.containsKey("sourceAgentId")) {
                    builder.sourceAgentId((String) raw.get("sourceAgentId"));
                }
                if (raw.containsKey("metadata")) {
                    Map<String, Object> meta = (Map<String, Object>) raw.get("metadata");
                    meta.forEach(builder::metadata);
                }
                if (raw.containsKey("contents")) {
                    List<Map<String, Object>> rawContents =
                            (List<Map<String, Object>>) raw.get("contents");
                    for (Map<String, Object> rc : rawContents) {
                        Content content = deserializeContent(rc);
                        if (content != null) {
                            builder.addContent(content);
                        }
                    }
                }
                result.add(builder.build());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize messages", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Content deserializeContent(Map<String, Object> raw) {
        String type = (String) raw.get("type");
        if (type == null) {
            return null;
        }
        if ("text".equals(type)) {
            return new Content.TextContent((String) raw.get("text"));
        } else if ("image".equals(type)) {
            return new Content.ImageContent(
                    (String) raw.get("url"), (String) raw.get("mediaType"), null);
        } else if ("tool_use".equals(type)) {
            return new Content.ToolUseContent(
                    (String) raw.get("toolId"),
                    (String) raw.get("toolName"),
                    (Map<String, Object>) raw.get("input"));
        } else if ("tool_result".equals(type)) {
            return new Content.ToolResultContent(
                    (String) raw.get("toolUseId"),
                    (String) raw.get("content"),
                    (Boolean) raw.get("isError"));
        } else if ("thinking".equals(type)) {
            return new Content.ThinkingContent(
                    (String) raw.get("thinking"),
                    ((Number) raw.get("budgetTokens")).intValue(),
                    (String) raw.get("signature"));
        }
        return null;
    }
}
