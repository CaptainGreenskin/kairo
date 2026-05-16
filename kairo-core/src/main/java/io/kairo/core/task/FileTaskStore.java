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
package io.kairo.core.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File-based {@link TaskStore} implementation backed by a single {@code .kairo/tasks.json} file.
 *
 * <p>Thread-safe via {@link ReadWriteLock}. Writes are atomic (tmp + move). Data is lazily loaded
 * on first access and cached in memory.
 */
public class FileTaskStore implements TaskStore {

    private static final Logger log = LoggerFactory.getLogger(FileTaskStore.class);
    static final String TASKS_FILE = ".kairo/tasks.json";

    private static final ConcurrentHashMap<Path, FileTaskStore> INSTANCES =
            new ConcurrentHashMap<>();

    private final ObjectMapper mapper;
    private final Path storePath;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private LinkedHashMap<String, TaskEntry> tasks;
    private int nextId = 1;
    private boolean loaded = false;

    public FileTaskStore(Path workspaceRoot) {
        this(workspaceRoot, new ObjectMapper());
    }

    FileTaskStore(Path workspaceRoot, ObjectMapper mapper) {
        this.mapper = mapper;
        this.storePath = workspaceRoot.resolve(TASKS_FILE);
    }

    /**
     * Get or create a shared FileTaskStore instance for the given workspace root. Tools should use
     * this to avoid creating multiple stores for the same workspace.
     */
    public static FileTaskStore forWorkspace(Path workspaceRoot) {
        return INSTANCES.computeIfAbsent(
                workspaceRoot.toAbsolutePath().normalize(), FileTaskStore::new);
    }

    /** Clear the shared instance cache. Intended for testing. */
    public static void clearInstances() {
        INSTANCES.clear();
    }

    @Override
    public TaskEntry create(
            String subject,
            @Nullable String description,
            @Nullable String activeForm,
            @Nullable Map<String, Object> metadata) {
        lock.writeLock().lock();
        try {
            ensureLoaded();
            String id = String.valueOf(nextId++);
            TaskEntry entry =
                    new TaskEntry(
                            id,
                            subject,
                            description,
                            TaskStatus.PENDING,
                            null,
                            Set.of(),
                            Set.of(),
                            activeForm,
                            metadata);
            tasks.put(id, entry);
            persist();
            return entry;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<TaskEntry> get(String taskId) {
        lock.readLock().lock();
        try {
            ensureLoaded();
            return Optional.ofNullable(tasks.get(taskId));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<TaskEntry> listAll() {
        lock.readLock().lock();
        try {
            ensureLoaded();
            return List.copyOf(tasks.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void update(TaskEntry entry) {
        lock.writeLock().lock();
        try {
            ensureLoaded();
            tasks.put(entry.id(), entry);
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(String taskId) {
        lock.writeLock().lock();
        try {
            ensureLoaded();
            tasks.remove(taskId);
            for (Map.Entry<String, TaskEntry> e : tasks.entrySet()) {
                TaskEntry t = e.getValue();
                boolean blocksChanged = t.blocks().contains(taskId);
                boolean blockedByChanged = t.blockedBy().contains(taskId);
                if (blocksChanged || blockedByChanged) {
                    Set<String> newBlocks = blocksChanged ? remove(t.blocks(), taskId) : t.blocks();
                    Set<String> newBlockedBy =
                            blockedByChanged ? remove(t.blockedBy(), taskId) : t.blockedBy();
                    tasks.put(e.getKey(), t.withBlocks(newBlocks).withBlockedBy(newBlockedBy));
                }
            }
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void addDependency(String fromId, String toId) {
        lock.writeLock().lock();
        try {
            ensureLoaded();
            TaskEntry from = tasks.get(fromId);
            TaskEntry to = tasks.get(toId);
            if (from == null || to == null) {
                return;
            }
            Set<String> newFromBlocks = add(from.blocks(), toId);
            tasks.put(fromId, from.withBlocks(newFromBlocks));

            Set<String> newToBlockedBy = add(to.blockedBy(), fromId);
            tasks.put(toId, to.withBlockedBy(newToBlockedBy));

            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void cascadeCompletion(String completedTaskId) {
        lock.writeLock().lock();
        try {
            ensureLoaded();
            for (Map.Entry<String, TaskEntry> e : tasks.entrySet()) {
                TaskEntry t = e.getValue();
                if (t.blockedBy().contains(completedTaskId)) {
                    Set<String> newBlockedBy = remove(t.blockedBy(), completedTaskId);
                    tasks.put(e.getKey(), t.withBlockedBy(newBlockedBy));
                }
            }
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int pendingOrInProgressCount() {
        lock.readLock().lock();
        try {
            ensureLoaded();
            return (int)
                    tasks.values().stream().filter(t -> t.status() != TaskStatus.COMPLETED).count();
        } finally {
            lock.readLock().unlock();
        }
    }

    private void ensureLoaded() {
        if (!loaded) {
            loadFromFile();
            loaded = true;
        }
    }

    private void loadFromFile() {
        tasks = new LinkedHashMap<>();
        if (!Files.exists(storePath)) {
            return;
        }
        try {
            String content = Files.readString(storePath, StandardCharsets.UTF_8);
            JsonNode root = mapper.readTree(content);
            if (!root.isObject()) {
                log.warn("Expected JSON object in {}, ignoring", storePath);
                return;
            }
            JsonNode nextIdNode = root.get("_nextId");
            if (nextIdNode != null && nextIdNode.isInt()) {
                nextId = nextIdNode.asInt();
            }
            JsonNode tasksNode = root.get("tasks");
            if (tasksNode != null && tasksNode.isArray()) {
                for (JsonNode node : tasksNode) {
                    TaskEntry entry = parseTaskNode(node);
                    if (entry != null) {
                        tasks.put(entry.id(), entry);
                        int idNum = parseIdSafe(entry.id());
                        if (idNum >= nextId) {
                            nextId = idNum + 1;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to load tasks from {}: {}", storePath, e.getMessage());
        }
    }

    @Nullable
    private TaskEntry parseTaskNode(JsonNode node) {
        try {
            String id = node.path("id").asText(null);
            String subject = node.path("subject").asText(null);
            if (id == null || subject == null) {
                return null;
            }
            String description = node.path("description").asText(null);
            TaskStatus status =
                    node.has("status")
                            ? TaskStatus.fromString(node.path("status").asText())
                            : TaskStatus.PENDING;
            String owner = node.path("owner").asText(null);
            Set<String> blocks = parseStringSet(node.get("blocks"));
            Set<String> blockedBy = parseStringSet(node.get("blockedBy"));
            String activeForm = node.path("activeForm").asText(null);
            Map<String, Object> metadata =
                    node.has("metadata")
                            ? mapper.convertValue(
                                    node.get("metadata"),
                                    new TypeReference<Map<String, Object>>() {})
                            : Map.of();
            return new TaskEntry(
                    id,
                    subject,
                    description,
                    status,
                    owner,
                    blocks,
                    blockedBy,
                    activeForm,
                    metadata);
        } catch (Exception e) {
            log.warn("Skipping malformed task entry: {}", e.getMessage());
            return null;
        }
    }

    private Set<String> parseStringSet(@Nullable JsonNode node) {
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private void persist() {
        try {
            Files.createDirectories(storePath.getParent());
            ObjectNode root = mapper.createObjectNode();
            root.put("_nextId", nextId);
            ArrayNode tasksArray = root.putArray("tasks");
            for (TaskEntry entry : tasks.values()) {
                tasksArray.add(serializeTask(entry));
            }
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            Path tmp = storePath.resolveSibling(storePath.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(
                        tmp,
                        storePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, storePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist tasks to " + storePath, e);
        }
    }

    private ObjectNode serializeTask(TaskEntry entry) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", entry.id());
        node.put("subject", entry.subject());
        node.put("description", entry.description());
        node.put("status", entry.status().toJson());
        node.put("owner", entry.owner());
        ArrayNode blocksArr = node.putArray("blocks");
        entry.blocks().forEach(blocksArr::add);
        ArrayNode blockedByArr = node.putArray("blockedBy");
        entry.blockedBy().forEach(blockedByArr::add);
        node.put("activeForm", entry.activeForm());
        node.set("metadata", mapper.valueToTree(entry.metadata()));
        return node;
    }

    private static Set<String> add(Set<String> original, String value) {
        Set<String> result = new LinkedHashSet<>(original);
        result.add(value);
        return result;
    }

    private static Set<String> remove(Set<String> original, String value) {
        Set<String> result = new LinkedHashSet<>(original);
        result.remove(value);
        return result;
    }

    private static int parseIdSafe(String id) {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
