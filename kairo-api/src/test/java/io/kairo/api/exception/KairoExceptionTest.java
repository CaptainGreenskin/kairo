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
package io.kairo.api.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Tests for the Kairo exception hierarchy. */
class KairoExceptionTest {

    // --- Hierarchy tests ---

    @Test
    void kairoExceptionExtendsRuntimeException() {
        KairoException ex = new KairoException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void agentExceptionExtendsKairoException() {
        AgentException ex = new AgentException("test");
        assertInstanceOf(KairoException.class, ex);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void agentInterruptedExceptionExtendsAgentException() {
        AgentInterruptedException ex = new AgentInterruptedException("interrupted");
        assertInstanceOf(AgentException.class, ex);
        assertInstanceOf(KairoException.class, ex);
    }

    @Test
    void agentExecutionExceptionExtendsAgentException() {
        AgentExecutionException ex = new AgentExecutionException("execution failed");
        assertInstanceOf(AgentException.class, ex);
        assertInstanceOf(KairoException.class, ex);
    }

    @Test
    void modelExceptionExtendsKairoException() {
        ModelException ex = new ModelException("model error");
        assertInstanceOf(KairoException.class, ex);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void modelRateLimitExceptionExtendsModelException() {
        ModelRateLimitException ex = new ModelRateLimitException("rate limited");
        assertInstanceOf(ModelException.class, ex);
        assertInstanceOf(KairoException.class, ex);
    }

    @Test
    void modelTimeoutExceptionExtendsModelException() {
        ModelTimeoutException ex = new ModelTimeoutException("timed out");
        assertInstanceOf(ModelException.class, ex);
        assertInstanceOf(KairoException.class, ex);
    }

    @Test
    void toolExceptionExtendsKairoException() {
        ToolException ex = new ToolException("tool error");
        assertInstanceOf(KairoException.class, ex);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void toolPermissionExceptionExtendsToolException() {
        ToolPermissionException ex = new ToolPermissionException("denied");
        assertInstanceOf(ToolException.class, ex);
        assertInstanceOf(KairoException.class, ex);
    }

    @Test
    void planModeViolationExceptionExtendsToolException() {
        PlanModeViolationException ex = new PlanModeViolationException("blocked");
        assertInstanceOf(ToolException.class, ex);
        assertInstanceOf(KairoException.class, ex);
    }

    // --- Message and cause propagation ---

    @Test
    void kairoExceptionMessageAndCause() {
        Throwable cause = new IllegalStateException("root cause");
        KairoException ex = new KairoException("msg", cause);
        assertEquals("msg", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void kairoExceptionCauseOnlyConstructor() {
        Throwable cause = new IllegalStateException("root cause");
        KairoException ex = new KairoException(cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void agentInterruptedExceptionMessageAndCause() {
        Throwable cause = new RuntimeException("timeout");
        AgentInterruptedException ex = new AgentInterruptedException("interrupted", cause);
        assertEquals("interrupted", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void agentExecutionExceptionMessageAndCause() {
        Throwable cause = new NullPointerException();
        AgentExecutionException ex = new AgentExecutionException("failed", cause);
        assertEquals("failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void modelRateLimitExceptionMessageAndCause() {
        Throwable cause = new RuntimeException("429");
        ModelRateLimitException ex = new ModelRateLimitException("rate limited", cause);
        assertEquals("rate limited", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void modelTimeoutExceptionMessageAndCause() {
        Throwable cause = new RuntimeException("timeout");
        ModelTimeoutException ex = new ModelTimeoutException("timed out", cause);
        assertEquals("timed out", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void toolPermissionExceptionMessageAndCause() {
        Throwable cause = new SecurityException("access denied");
        ToolPermissionException ex = new ToolPermissionException("denied", cause);
        assertEquals("denied", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void planModeViolationExceptionWithToolName() {
        PlanModeViolationException ex = new PlanModeViolationException("blocked", "file_write");
        assertEquals("blocked", ex.getMessage());
        assertEquals("file_write", ex.getToolName());
    }

    @Test
    void planModeViolationExceptionMessageOnly() {
        PlanModeViolationException ex = new PlanModeViolationException("blocked");
        assertEquals("blocked", ex.getMessage());
        assertNull(ex.getToolName());
    }

    @Test
    void planModeViolationExceptionWithCause() {
        Throwable cause = new RuntimeException("inner");
        PlanModeViolationException ex = new PlanModeViolationException("blocked", cause);
        assertEquals("blocked", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertNull(ex.getToolName());
    }

    // --- Catch semantics ---

    @Test
    void catchKairoExceptionCatchesAllSubtypes() {
        Exception[] exceptions = {
            new AgentInterruptedException("a"),
            new AgentExecutionException("b"),
            new ModelRateLimitException("c"),
            new ModelTimeoutException("d"),
            new ToolPermissionException("e"),
            new PlanModeViolationException("f"),
        };
        for (Exception ex : exceptions) {
            assertInstanceOf(
                    KairoException.class,
                    ex,
                    ex.getClass().getSimpleName() + " should be catchable as KairoException");
        }
    }

    @Test
    void catchAgentExceptionCatchesBothSubtypes() {
        assertInstanceOf(AgentException.class, new AgentInterruptedException("a"));
        assertInstanceOf(AgentException.class, new AgentExecutionException("b"));
    }

    @Test
    void catchModelExceptionCatchesBothSubtypes() {
        assertInstanceOf(ModelException.class, new ModelRateLimitException("a"));
        assertInstanceOf(ModelException.class, new ModelTimeoutException("b"));
    }

    @Test
    void catchToolExceptionCatchesBothSubtypes() {
        assertInstanceOf(ToolException.class, new ToolPermissionException("a"));
        assertInstanceOf(ToolException.class, new PlanModeViolationException("b"));
    }
}
