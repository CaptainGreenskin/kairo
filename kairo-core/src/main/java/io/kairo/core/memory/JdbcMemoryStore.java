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
package io.kairo.core.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.exception.MemoryStoreException;
import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.memory.MemoryQuery;
import io.kairo.api.memory.MemoryScope;
import io.kairo.api.memory.MemoryStore;
import io.kairo.core.model.ExceptionMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * JDBC-based persistent implementation of {@link MemoryStore}.
 *
 * <p>Compatible with both H2 (testing + demo) and PostgreSQL (production). Auto-creates the schema
 * on initialization. Vector search is not supported — queries with {@code queryVector} set will log
 * a warning and ignore the vector field.
 */
public class JdbcMemoryStore implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcMemoryStore.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final String CREATE_TABLE_SQL =
            """
            CREATE TABLE IF NOT EXISTS kairo_memory (
                id VARCHAR(255) PRIMARY KEY,
                agent_id VARCHAR(255),
                content TEXT NOT NULL,
                raw_content TEXT,
                scope VARCHAR(50) NOT NULL,
                importance DOUBLE PRECISION NOT NULL DEFAULT 0.5,
                embedding BLOB,
                tags VARCHAR(1000),
                timestamp TIMESTAMP NOT NULL,
                metadata TEXT
            )
            """;

    private static final String CREATE_INDEX_AGENT =
            "CREATE INDEX IF NOT EXISTS idx_kairo_memory_agent ON kairo_memory(agent_id)";
    private static final String CREATE_INDEX_SCOPE =
            "CREATE INDEX IF NOT EXISTS idx_kairo_memory_scope ON kairo_memory(scope)";
    private static final String CREATE_INDEX_IMPORTANCE =
            "CREATE INDEX IF NOT EXISTS idx_kairo_memory_importance ON kairo_memory(importance)";
    private static final String CREATE_INDEX_TIMESTAMP =
            "CREATE INDEX IF NOT EXISTS idx_kairo_memory_timestamp ON kairo_memory(timestamp)";

    private static final String UPSERT_H2_SQL =
            """
            MERGE INTO kairo_memory (id, agent_id, content, raw_content, scope, importance,
                embedding, tags, timestamp, metadata)
            KEY (id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPSERT_PG_SQL =
            """
            INSERT INTO kairo_memory (id, agent_id, content, raw_content, scope, importance,
                embedding, tags, timestamp, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                agent_id = EXCLUDED.agent_id,
                content = EXCLUDED.content,
                raw_content = EXCLUDED.raw_content,
                scope = EXCLUDED.scope,
                importance = EXCLUDED.importance,
                embedding = EXCLUDED.embedding,
                tags = EXCLUDED.tags,
                timestamp = EXCLUDED.timestamp,
                metadata = EXCLUDED.metadata
            """;

    private static final String SELECT_BY_ID = "SELECT * FROM kairo_memory WHERE id = ?";
    private static final String DELETE_BY_ID = "DELETE FROM kairo_memory WHERE id = ?";

    private final DataSource dataSource;
    private volatile Boolean isH2;

    /**
     * Creates a new JdbcMemoryStore backed by the given DataSource.
     *
     * <p>Automatically creates the kairo_memory table and indexes if they do not exist.
     *
     * @param dataSource the JDBC DataSource to use for connections
     */
    public JdbcMemoryStore(DataSource dataSource) {
        this.dataSource = dataSource;
        initSchema();
    }

    private boolean isH2(Connection conn) throws SQLException {
        if (isH2 == null) {
            isH2 = conn.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
        }
        return isH2;
    }

    private String getUpsertSql(Connection conn) throws SQLException {
        return isH2(conn) ? UPSERT_H2_SQL : UPSERT_PG_SQL;
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute(CREATE_TABLE_SQL);
            conn.createStatement().execute(CREATE_INDEX_AGENT);
            conn.createStatement().execute(CREATE_INDEX_SCOPE);
            conn.createStatement().execute(CREATE_INDEX_IMPORTANCE);
            conn.createStatement().execute(CREATE_INDEX_TIMESTAMP);
        } catch (SQLException e) {
            throw new MemoryStoreException("Failed to initialize kairo_memory schema", e);
        }
    }

    @Override
    public Mono<MemoryEntry> save(MemoryEntry entry) {
        return Mono.fromCallable(
                        () -> {
                            try (Connection conn = dataSource.getConnection();
                                    PreparedStatement ps =
                                            conn.prepareStatement(getUpsertSql(conn))) {
                                ps.setString(1, entry.id());
                                ps.setString(2, entry.agentId());
                                ps.setString(3, entry.content());
                                ps.setString(4, entry.rawContent());
                                ps.setString(5, entry.scope().name());
                                ps.setDouble(6, entry.importance());
                                if (entry.embedding() != null) {
                                    ps.setBytes(7, serializeEmbedding(entry.embedding()));
                                } else {
                                    ps.setNull(7, Types.BLOB);
                                }
                                ps.setString(8, serializeTags(entry.tags()));
                                ps.setTimestamp(9, Timestamp.from(entry.timestamp()));
                                ps.setString(10, serializeMetadata(entry.metadata()));
                                ps.executeUpdate();
                                return entry;
                            } catch (SQLException e) {
                                throw new MemoryStoreException(
                                        "Failed to save memory entry: " + entry.id(), e);
                            }
                        })
                .onErrorMap(ExceptionMapper::toStorageException);
    }

    @Override
    public Mono<MemoryEntry> get(String id) {
        return Mono.fromCallable(
                        () -> {
                            try (Connection conn = dataSource.getConnection();
                                    PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
                                ps.setString(1, id);
                                try (ResultSet rs = ps.executeQuery()) {
                                    if (rs.next()) {
                                        return mapRow(rs);
                                    }
                                    return null;
                                }
                            } catch (SQLException e) {
                                throw new MemoryStoreException(
                                        "Failed to get memory entry: " + id, e);
                            }
                        })
                .onErrorMap(ExceptionMapper::toStorageException);
    }

    @Override
    public Flux<MemoryEntry> search(String query, MemoryScope scope) {
        return search(MemoryQuery.builder().keyword(query).build())
                .filter(entry -> entry.scope() == scope);
    }

    @Override
    public Mono<Void> delete(String id) {
        return Mono.<Void>fromRunnable(
                        () -> {
                            try (Connection conn = dataSource.getConnection();
                                    PreparedStatement ps = conn.prepareStatement(DELETE_BY_ID)) {
                                ps.setString(1, id);
                                ps.executeUpdate();
                            } catch (SQLException e) {
                                throw new MemoryStoreException(
                                        "Failed to delete memory entry: " + id, e);
                            }
                        })
                .onErrorMap(ExceptionMapper::toStorageException);
    }

    @Override
    public Flux<MemoryEntry> list(MemoryScope scope) {
        return Flux.using(
                        () -> dataSource.getConnection(),
                        connection -> {
                            try (PreparedStatement ps =
                                    connection.prepareStatement(
                                            "SELECT * FROM kairo_memory WHERE scope = ? ORDER BY timestamp DESC")) {
                                ps.setString(1, scope.name());
                                try (ResultSet rs = ps.executeQuery()) {
                                    List<MemoryEntry> results = new ArrayList<>();
                                    while (rs.next()) {
                                        results.add(mapRow(rs));
                                    }
                                    return Flux.fromIterable(results);
                                }
                            } catch (SQLException e) {
                                return Flux.<MemoryEntry>error(
                                        new MemoryStoreException(
                                                "Failed to list memory entries", e));
                            }
                        },
                        connection -> {
                            try {
                                connection.close();
                            } catch (SQLException ignored) {
                            }
                        })
                .onErrorMap(ExceptionMapper::toStorageException);
    }

    @Override
    public Flux<MemoryEntry> search(MemoryQuery query) {
        if (query.queryVector() != null) {
            log.warn(
                    "JdbcMemoryStore does not support vector search. "
                            + "queryVector will be ignored. Vector search is deferred to v0.8 with pgvector.");
        }

        return Flux.using(
                        () -> dataSource.getConnection(),
                        connection -> {
                            StringBuilder sql =
                                    new StringBuilder("SELECT * FROM kairo_memory WHERE 1=1");
                            List<Object> params = new ArrayList<>();

                            if (query.agentId() != null) {
                                sql.append(" AND agent_id = ?");
                                params.add(query.agentId());
                            }
                            if (query.keyword() != null && !query.keyword().isBlank()) {
                                sql.append(
                                        " AND (content LIKE ? ESCAPE '\\' OR raw_content LIKE ? ESCAPE '\\')");
                                String like = "%" + escapeLike(query.keyword()) + "%";
                                params.add(like);
                                params.add(like);
                            }
                            if (query.tags() != null && !query.tags().isEmpty()) {
                                for (String tag : query.tags()) {
                                    sql.append(" AND tags LIKE ? ESCAPE '\\'");
                                    params.add("%," + escapeLike(tag) + ",%");
                                }
                            }
                            if (query.minImportance() > 0.0) {
                                sql.append(" AND importance >= ?");
                                params.add(query.minImportance());
                            }
                            if (query.from() != null) {
                                sql.append(" AND timestamp >= ?");
                                params.add(Timestamp.from(query.from()));
                            }
                            if (query.to() != null) {
                                sql.append(" AND timestamp <= ?");
                                params.add(Timestamp.from(query.to()));
                            }

                            sql.append(" ORDER BY timestamp DESC");
                            sql.append(" LIMIT ?");
                            params.add(query.limit());

                            try (PreparedStatement ps =
                                    connection.prepareStatement(sql.toString())) {
                                for (int i = 0; i < params.size(); i++) {
                                    Object param = params.get(i);
                                    if (param instanceof String s) {
                                        ps.setString(i + 1, s);
                                    } else if (param instanceof Double d) {
                                        ps.setDouble(i + 1, d);
                                    } else if (param instanceof Timestamp ts) {
                                        ps.setTimestamp(i + 1, ts);
                                    } else if (param instanceof Integer n) {
                                        ps.setInt(i + 1, n);
                                    }
                                }
                                try (ResultSet rs = ps.executeQuery()) {
                                    List<MemoryEntry> results = new ArrayList<>();
                                    while (rs.next()) {
                                        results.add(mapRow(rs));
                                    }
                                    return Flux.fromIterable(results);
                                }
                            } catch (SQLException e) {
                                return Flux.<MemoryEntry>error(
                                        new MemoryStoreException(
                                                "Failed to search memory entries", e));
                            }
                        },
                        connection -> {
                            try {
                                connection.close();
                            } catch (SQLException ignored) {
                            }
                        })
                .onErrorMap(ExceptionMapper::toStorageException);
    }

    private MemoryEntry mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String agentId = rs.getString("agent_id");
        String content = rs.getString("content");
        String rawContent = rs.getString("raw_content");
        MemoryScope scope = MemoryScope.valueOf(rs.getString("scope"));
        double importance = rs.getDouble("importance");
        byte[] embeddingBytes = rs.getBytes("embedding");
        float[] embedding = embeddingBytes != null ? deserializeEmbedding(embeddingBytes) : null;
        Set<String> tags = deserializeTags(rs.getString("tags"));
        Timestamp ts = rs.getTimestamp("timestamp");
        Instant timestamp = ts != null ? ts.toInstant() : Instant.now();
        Map<String, Object> metadata = deserializeMetadata(rs.getString("metadata"));

        return new MemoryEntry(
                id,
                agentId,
                content,
                rawContent,
                scope,
                importance,
                embedding,
                tags,
                timestamp,
                metadata);
    }

    // --- Serialization helpers ---

    private static byte[] serializeEmbedding(float[] embedding) {
        ByteBuffer buffer =
                ByteBuffer.allocate(embedding.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : embedding) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    private static float[] deserializeEmbedding(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] result = new float[bytes.length / 4];
        for (int i = 0; i < result.length; i++) {
            result[i] = buffer.getFloat();
        }
        return result;
    }

    private static String serializeTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return "," + String.join(",", tags) + ",";
    }

    private static Set<String> deserializeTags(String tagsStr) {
        if (tagsStr == null || tagsStr.isBlank()) {
            return Set.of();
        }
        // Strip leading/trailing delimiter commas
        String stripped = tagsStr;
        if (stripped.startsWith(",")) {
            stripped = stripped.substring(1);
        }
        if (stripped.endsWith(",")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        Set<String> tags = new LinkedHashSet<>();
        for (String tag : stripped.split(",")) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new MemoryStoreException("Failed to serialize metadata", e);
        }
    }

    private static Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (IOException e) {
            log.warn("Failed to deserialize metadata JSON: {}", json, e);
            return Map.of();
        }
    }
}
