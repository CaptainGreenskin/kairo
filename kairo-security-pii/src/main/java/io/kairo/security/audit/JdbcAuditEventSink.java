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
package io.kairo.security.audit;

import io.kairo.api.guardrail.SecurityEvent;
import io.kairo.api.guardrail.SecurityEventSink;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Append-only JDBC implementation of {@link SecurityEventSink} that persists every guardrail
 * decision to the {@code kairo_audit} table for compliance audit trails.
 *
 * <p>Schema is created by the bundled Flyway migration at {@code
 * classpath:db/migration/V1__create_kairo_audit_table.sql}. Hosting apps wire Flyway (or apply the
 * SQL manually) before constructing this sink.
 *
 * <p>This is not a new SPI — it is a stock implementation of the existing {@link SecurityEventSink}
 * contract. Failures are logged and swallowed (per the SPI contract) so audit persistence never
 * breaks the request path.
 *
 * <p>The {@link SecurityEvent#attributes()} map is rendered with {@link String#valueOf(Object)} —
 * this preserves human readability without requiring a JSON dependency. Hosting apps that need
 * structured/queryable audit attributes should compose a custom sink that serializes via Jackson or
 * another JSON library.
 *
 * @since v1.0.0
 */
public final class JdbcAuditEventSink implements SecurityEventSink {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditEventSink.class);

    private static final String INSERT_SQL =
            "INSERT INTO kairo_audit"
                    + " (event_id, event_type, agent_name, target_name, phase, policy_name,"
                    + " reason, attributes_json, created_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final DataSource dataSource;

    public JdbcAuditEventSink(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void record(SecurityEvent event) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, event.type().name());
            ps.setString(3, event.agentName());
            ps.setString(4, event.targetName());
            ps.setString(5, event.phase() == null ? null : event.phase().name());
            ps.setString(6, event.policyName());
            ps.setString(7, truncate(event.reason(), 1024));
            ps.setString(8, renderAttributes(event.attributes()));
            ps.setTimestamp(9, Timestamp.from(event.timestamp()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn(
                    "kairo_audit insert failed for event type={} reason={}",
                    event.type(),
                    e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String renderAttributes(Map<String, Object> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return null;
        }
        return String.valueOf(attrs);
    }
}
