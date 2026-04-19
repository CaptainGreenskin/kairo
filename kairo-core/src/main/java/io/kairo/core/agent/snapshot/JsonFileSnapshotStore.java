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
package io.kairo.core.agent.snapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.agent.AgentSnapshot;
import io.kairo.api.agent.AgentState;
import io.kairo.api.agent.SnapshotStore;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.session.SessionSerializer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * File-based {@link SnapshotStore} that persists {@link AgentSnapshot}s as JSON files.
 *
 * <p>Each snapshot is stored as a separate {@code .json} file in the configured storage directory.
 * Keys are sanitized for filesystem safety by replacing {@code /}, {@code \}, and {@code :} with
 * {@code _}. Blocking I/O is offloaded to {@link Schedulers#boundedElastic()}.
 *
 * <p>Reuses the pre-configured {@link ObjectMapper} from {@link SessionSerializer} for consistent
 * JSON serialization across the framework. Message objects are serialized using the same map-based
 * approach as {@link SessionSerializer} for forward compatibility.
 */
public final class JsonFileSnapshotStore implements SnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileSnapshotStore.class);
    private static final String JSON_EXTENSION = ".json";
    private static final int SCHEMA_VERSION = 1;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final Path storageDir;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new {@code JsonFileSnapshotStore}.
     *
     * <p>The storage directory is created if it does not exist.
     *
     * @param storageDir the directory to store JSON snapshot files
     * @param sessionSerializer the serializer whose {@link ObjectMapper} will be reused
     * @throws IllegalArgumentException if any parameter is null
     * @throws RuntimeException if the directory cannot be created
     */
    public JsonFileSnapshotStore(Path storageDir, SessionSerializer sessionSerializer) {
        if (storageDir == null) {
            throw new IllegalArgumentException("storageDir must not be null");
        }
        if (sessionSerializer == null) {
            throw new IllegalArgumentException("sessionSerializer must not be null");
        }
        this.storageDir = storageDir;
        this.objectMapper = sessionSerializer.objectMapper();
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to create snapshot storage directory: " + storageDir, e);
        }
    }

    @Override
    public Mono<Void> save(String key, AgentSnapshot snapshot) {
        return Mono.fromCallable(
                        () -> {
                            Path file = resolveFile(key);
                            String json = serializeSnapshot(snapshot);
                            Files.writeString(file, json);
                            log.debug("Saved snapshot to {}", file);
                            return (Void) null;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<AgentSnapshot> load(String key) {
        return Mono.fromCallable(
                        () -> {
                            Path file = resolveFile(key);
                            if (!Files.exists(file)) {
                                return null;
                            }
                            String json = Files.readString(file);
                            AgentSnapshot snapshot = deserializeSnapshot(json);
                            log.debug("Loaded snapshot from {}", file);
                            return snapshot;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> delete(String key) {
        return Mono.fromCallable(
                        () -> {
                            Path file = resolveFile(key);
                            boolean deleted = Files.deleteIfExists(file);
                            if (deleted) {
                                log.debug("Deleted snapshot {}", file);
                            }
                            return (Void) null;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Flux<String> listKeys(String agentIdPrefix) {
        String prefix = agentIdPrefix != null ? agentIdPrefix : "";
        String sanitizedPrefix = sanitizeKey(prefix);
        return Mono.fromCallable(
                        () -> {
                            if (!Files.exists(storageDir)) {
                                return Stream.<String>empty();
                            }
                            return Files.list(storageDir)
                                    .filter(Files::isRegularFile)
                                    .map(p -> p.getFileName().toString())
                                    .filter(name -> name.endsWith(JSON_EXTENSION))
                                    .map(
                                            name ->
                                                    name.substring(
                                                            0,
                                                            name.length()
                                                                    - JSON_EXTENSION.length()))
                                    .filter(name -> name.startsWith(sanitizedPrefix));
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromStream);
    }

    // -------------------------------------------------------------------------
    // Internal serialization (map-based, aligned with SessionSerializer)
    // -------------------------------------------------------------------------

    private String serializeSnapshot(AgentSnapshot snapshot) {
        try {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("schemaVersion", SCHEMA_VERSION);
            wrapper.put("agentId", snapshot.agentId());
            wrapper.put("agentName", snapshot.agentName());
            wrapper.put("state", snapshot.state().name());
            wrapper.put("iteration", snapshot.iteration());
            wrapper.put("totalTokensUsed", snapshot.totalTokensUsed());
            wrapper.put("conversationHistory", serializeMessages(snapshot.conversationHistory()));
            wrapper.put("contextState", snapshot.contextState());
            wrapper.put("createdAt", snapshot.createdAt().toString());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to serialize agent snapshot: " + snapshot.agentId(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private AgentSnapshot deserializeSnapshot(String json) {
        try {
            Map<String, Object> wrapper = objectMapper.readValue(json, MAP_TYPE);

            String agentId = (String) wrapper.get("agentId");
            String agentName = (String) wrapper.get("agentName");
            AgentState state = AgentState.valueOf((String) wrapper.get("state"));
            int iteration =
                    wrapper.containsKey("iteration")
                            ? ((Number) wrapper.get("iteration")).intValue()
                            : 0;
            long totalTokensUsed =
                    wrapper.containsKey("totalTokensUsed")
                            ? ((Number) wrapper.get("totalTokensUsed")).longValue()
                            : 0L;

            List<Map<String, Object>> rawMessages =
                    wrapper.containsKey("conversationHistory")
                            ? (List<Map<String, Object>>) wrapper.get("conversationHistory")
                            : List.of();
            List<Msg> messages = deserializeMessages(rawMessages);

            Map<String, Object> contextState =
                    wrapper.containsKey("contextState")
                            ? (Map<String, Object>) wrapper.get("contextState")
                            : Map.of();

            Instant createdAt =
                    wrapper.containsKey("createdAt")
                            ? Instant.parse((String) wrapper.get("createdAt"))
                            : Instant.now();

            return new AgentSnapshot(
                    agentId,
                    agentName,
                    state,
                    iteration,
                    totalTokensUsed,
                    messages,
                    contextState,
                    createdAt);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize agent snapshot", e);
        }
    }

    private List<Map<String, Object>> serializeMessages(List<Msg> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
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
            result.add(map);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Msg> deserializeMessages(List<Map<String, Object>> rawMessages) {
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
            }
            result.add(map);
        }
        return result;
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
                    (String) raw.get("thinking"), ((Number) raw.get("budgetTokens")).intValue());
        }
        return null;
    }

    private Path resolveFile(String key) {
        return storageDir.resolve(sanitizeKey(key) + JSON_EXTENSION);
    }

    /**
     * Sanitize a key for filesystem safety.
     *
     * <p>Replaces {@code /}, {@code \}, {@code :}, {@code *}, {@code ?}, {@code "}, {@code <},
     * {@code >}, and {@code |} with {@code _}.
     *
     * @param key the raw key
     * @return the sanitized key safe for use as a filename
     */
    static String sanitizeKey(String key) {
        return key.replaceAll("[/\\\\:*?\"<>|]", "_");
    }
}
