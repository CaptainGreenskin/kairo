/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.acp.server;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session registry. ACP sessions are stable across a single agent process; for
 * persistence (load / resume across process restarts) a follow-up will add disk-backed storage. The
 * MVP value is: {@code session/new} hands back an id, {@code session/prompt} resolves it back to
 * its state, period.
 */
public final class AcpSessionManager {

    public record AcpSessionState(String sessionId, String cwd) {}

    private final Map<String, AcpSessionState> sessions = new ConcurrentHashMap<>();

    /** Allocate a new session id and record its {@code cwd}. */
    public AcpSessionState newSession(String cwd) {
        String id = "acp-" + UUID.randomUUID();
        AcpSessionState state = new AcpSessionState(id, cwd);
        sessions.put(id, state);
        return state;
    }

    public Optional<AcpSessionState> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /** Explicitly register a session — used by {@code session/load} to resurrect ids. */
    public void put(AcpSessionState state) {
        sessions.put(state.sessionId(), state);
    }

    public int sessionCount() {
        return sessions.size();
    }

    public void forget(String sessionId) {
        sessions.remove(sessionId);
    }

    /** All known session ids in registration order. Used to answer {@code session/list}. */
    public Map<String, AcpSessionState> snapshot() {
        return Map.copyOf(sessions);
    }
}
