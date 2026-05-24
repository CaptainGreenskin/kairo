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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.guardrail.GuardrailPhase;
import io.kairo.api.guardrail.SecurityEvent;
import io.kairo.api.guardrail.SecurityEventType;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link JdbcAuditEventSink} backed by H2 + Flyway. */
class JdbcAuditEventSinkTest {

    private DataSource dataSource;
    private JdbcAuditEventSink sink;

    @BeforeEach
    void setUp() {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:audit_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        h2.setUser("sa");
        Flyway flyway =
                Flyway.configure().dataSource(h2).locations("classpath:db/migration").load();
        flyway.migrate();
        this.dataSource = h2;
        this.sink = new JdbcAuditEventSink(h2);
    }

    @Test
    void singleEventIsPersisted() throws Exception {
        SecurityEvent event =
                new SecurityEvent(
                        Instant.parse("2026-04-24T10:00:00Z"),
                        SecurityEventType.GUARDRAIL_DENY,
                        "agent-alpha",
                        "tool-search",
                        GuardrailPhase.PRE_TOOL,
                        "deny-list",
                        "tool not allowed",
                        Map.of("requestId", "req-1"));
        sink.record(event);

        List<Row> rows = readAll();
        assertThat(rows).hasSize(1);
        Row row = rows.get(0);
        assertThat(row.eventType).isEqualTo("GUARDRAIL_DENY");
        assertThat(row.agentName).isEqualTo("agent-alpha");
        assertThat(row.targetName).isEqualTo("tool-search");
        assertThat(row.phase).isEqualTo("PRE_TOOL");
        assertThat(row.policyName).isEqualTo("deny-list");
        assertThat(row.reason).isEqualTo("tool not allowed");
        assertThat(row.attributesJson).contains("requestId").contains("req-1");
    }

    @Test
    void manyEventsAreAppended() throws Exception {
        for (int i = 0; i < 25; i++) {
            sink.record(
                    new SecurityEvent(
                            Instant.now(),
                            SecurityEventType.GUARDRAIL_ALLOW,
                            "agent",
                            "tool-" + i,
                            GuardrailPhase.POST_TOOL,
                            "policy",
                            "ok",
                            Map.of()));
        }
        assertThat(readAll()).hasSize(25);
    }

    @Test
    void nullPhaseAndAttributesAreToleratedAsNullColumns() throws Exception {
        sink.record(
                new SecurityEvent(
                        Instant.now(),
                        SecurityEventType.MCP_BLOCK,
                        "agent",
                        "tool",
                        null,
                        "policy",
                        "blocked",
                        Map.of()));
        List<Row> rows = readAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).phase).isNull();
        assertThat(rows.get(0).attributesJson).isNull();
    }

    @Test
    void overlongReasonIsTruncated() throws Exception {
        String longReason = "x".repeat(2000);
        sink.record(
                new SecurityEvent(
                        Instant.now(),
                        SecurityEventType.GUARDRAIL_WARN,
                        "agent",
                        "tool",
                        GuardrailPhase.POST_TOOL,
                        "policy",
                        longReason,
                        Map.of()));
        Row row = readAll().get(0);
        assertThat(row.reason).hasSize(1024);
    }

    @Test
    void sqlFailureIsSwallowed() {
        // Simulate failure by closing the underlying H2 db before recording.
        JdbcAuditEventSink badSink = new JdbcAuditEventSink(new JdbcDataSource());
        // The default JdbcDataSource has no URL → connection will fail. Must NOT throw.
        badSink.record(
                new SecurityEvent(
                        Instant.now(),
                        SecurityEventType.GUARDRAIL_ALLOW,
                        "agent",
                        "tool",
                        GuardrailPhase.PRE_TOOL,
                        "policy",
                        "ok",
                        Map.of()));
    }

    private List<Row> readAll() throws Exception {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT event_type, agent_name, target_name, phase, policy_name,"
                                        + " reason, attributes_json FROM kairo_audit ORDER BY id")) {
            List<Row> out = new java.util.ArrayList<>();
            while (rs.next()) {
                Row r = new Row();
                r.eventType = rs.getString(1);
                r.agentName = rs.getString(2);
                r.targetName = rs.getString(3);
                r.phase = rs.getString(4);
                r.policyName = rs.getString(5);
                r.reason = rs.getString(6);
                r.attributesJson = rs.getString(7);
                out.add(r);
            }
            return out;
        }
    }

    private static final class Row {
        String eventType;
        String agentName;
        String targetName;
        String phase;
        String policyName;
        String reason;
        String attributesJson;
    }
}
