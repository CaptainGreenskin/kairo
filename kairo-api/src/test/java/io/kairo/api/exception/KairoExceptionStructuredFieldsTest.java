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

/** Tests for structured error fields on KairoException and all subclasses. */
class KairoExceptionStructuredFieldsTest {

    // --- ErrorCategory enum ---

    @Test
    void errorCategoryHasExactlySixValues() {
        assertEquals(6, ErrorCategory.values().length);
        assertNotNull(ErrorCategory.MODEL);
        assertNotNull(ErrorCategory.TOOL);
        assertNotNull(ErrorCategory.AGENT);
        assertNotNull(ErrorCategory.STORAGE);
        assertNotNull(ErrorCategory.SECURITY);
        assertNotNull(ErrorCategory.UNKNOWN);
    }

    // --- KairoException old constructors: fields default to null/false ---

    @Test
    void kairoExceptionMessageConstructorDefaultsToNullFields() {
        var ex = new KairoException("msg");
        assertEquals("msg", ex.getMessage());
        assertNull(ex.getErrorCode());
        assertNull(ex.getCategory());
        assertFalse(ex.isRetryable());
        assertNull(ex.getRetryAfterMs());
        assertNull(ex.getCause());
    }

    @Test
    void kairoExceptionMessageCauseConstructorDefaultsToNullFields() {
        var cause = new RuntimeException("root");
        var ex = new KairoException("msg", cause);
        assertEquals("msg", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertNull(ex.getErrorCode());
        assertNull(ex.getCategory());
        assertFalse(ex.isRetryable());
        assertNull(ex.getRetryAfterMs());
    }

    @Test
    void kairoExceptionCauseConstructorDefaultsToNullFields() {
        var cause = new RuntimeException("root");
        var ex = new KairoException(cause);
        assertSame(cause, ex.getCause());
        assertNull(ex.getErrorCode());
        assertNull(ex.getCategory());
        assertFalse(ex.isRetryable());
        assertNull(ex.getRetryAfterMs());
    }

    // --- ModelException: category MODEL ---

    @Test
    void modelExceptionHasCategoryModel() {
        var ex = new ModelException("model fail");
        assertEquals(ErrorCategory.MODEL, ex.getCategory());
        assertNull(ex.getErrorCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void modelExceptionWithCauseHasCategoryModel() {
        var ex = new ModelException("model fail", new RuntimeException());
        assertEquals(ErrorCategory.MODEL, ex.getCategory());
    }

    // --- ModelApiException: errorCode MODEL_API_ERROR ---

    @Test
    void modelApiExceptionHasCorrectErrorCode() {
        var ex = new ModelApiException("api error");
        assertEquals("MODEL_API_ERROR", ex.getErrorCode());
        assertEquals(ErrorCategory.MODEL, ex.getCategory());
        assertFalse(ex.isRetryable());
    }

    @Test
    void modelApiExceptionWithCauseHasCorrectErrorCode() {
        var ex = new ModelApiException("api error", new RuntimeException());
        assertEquals("MODEL_API_ERROR", ex.getErrorCode());
        assertEquals(ErrorCategory.MODEL, ex.getCategory());
    }

    // --- ModelRateLimitException: errorCode MODEL_RATE_LIMITED, retryable=true ---

    @Test
    void modelRateLimitExceptionIsRetryable() {
        var ex = new ModelRateLimitException("rate limited");
        assertEquals("MODEL_RATE_LIMITED", ex.getErrorCode());
        assertEquals(ErrorCategory.MODEL, ex.getCategory());
        assertTrue(ex.isRetryable());
        assertNull(ex.getRetryAfterMs());
    }

    @Test
    void modelRateLimitExceptionWithCauseIsRetryable() {
        var ex = new ModelRateLimitException("rate limited", new RuntimeException());
        assertTrue(ex.isRetryable());
        assertNull(ex.getRetryAfterMs());
    }

    @Test
    void modelRateLimitExceptionWithRetryAfterMs() {
        var ex = new ModelRateLimitException("rate limited", new RuntimeException(), 30000L);
        assertTrue(ex.isRetryable());
        assertEquals(30000L, ex.getRetryAfterMs());
        assertEquals("MODEL_RATE_LIMITED", ex.getErrorCode());
        assertEquals(ErrorCategory.MODEL, ex.getCategory());
    }

    // --- ModelTimeoutException: errorCode MODEL_TIMEOUT, retryable=true ---

    @Test
    void modelTimeoutExceptionIsRetryable() {
        var ex = new ModelTimeoutException("timed out");
        assertEquals("MODEL_TIMEOUT", ex.getErrorCode());
        assertEquals(ErrorCategory.MODEL, ex.getCategory());
        assertTrue(ex.isRetryable());
        assertNull(ex.getRetryAfterMs());
    }

    @Test
    void modelTimeoutExceptionWithCauseIsRetryable() {
        var ex = new ModelTimeoutException("timed out", new RuntimeException());
        assertTrue(ex.isRetryable());
        assertEquals("MODEL_TIMEOUT", ex.getErrorCode());
    }

    // --- MemoryStoreException: category STORAGE, errorCode STORAGE_ERROR ---

    @Test
    void memoryStoreExceptionHasStorageCategory() {
        var ex = new MemoryStoreException("storage fail");
        assertEquals("STORAGE_ERROR", ex.getErrorCode());
        assertEquals(ErrorCategory.STORAGE, ex.getCategory());
        assertFalse(ex.isRetryable());
    }

    @Test
    void memoryStoreExceptionWithCauseHasStorageCategory() {
        var ex = new MemoryStoreException("storage fail", new RuntimeException());
        assertEquals("STORAGE_ERROR", ex.getErrorCode());
        assertEquals(ErrorCategory.STORAGE, ex.getCategory());
    }

    // --- ToolException: category TOOL ---

    @Test
    void toolExceptionHasCategoryTool() {
        var ex = new ToolException("tool fail");
        assertEquals(ErrorCategory.TOOL, ex.getCategory());
        assertNull(ex.getErrorCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void toolExceptionWithCauseHasCategoryTool() {
        var ex = new ToolException("tool fail", new RuntimeException());
        assertEquals(ErrorCategory.TOOL, ex.getCategory());
    }

    // --- ToolPermissionException: errorCode TOOL_PERMISSION_DENIED ---

    @Test
    void toolPermissionExceptionHasCorrectErrorCode() {
        var ex = new ToolPermissionException("denied");
        assertEquals("TOOL_PERMISSION_DENIED", ex.getErrorCode());
        assertEquals(ErrorCategory.TOOL, ex.getCategory());
        assertFalse(ex.isRetryable());
    }

    @Test
    void toolPermissionExceptionWithCauseHasCorrectErrorCode() {
        var ex = new ToolPermissionException("denied", new RuntimeException());
        assertEquals("TOOL_PERMISSION_DENIED", ex.getErrorCode());
        assertEquals(ErrorCategory.TOOL, ex.getCategory());
    }

    // --- PlanModeViolationException: errorCode PLAN_MODE_VIOLATION ---

    @Test
    void planModeViolationExceptionHasCorrectErrorCode() {
        var ex = new PlanModeViolationException("blocked");
        assertEquals("PLAN_MODE_VIOLATION", ex.getErrorCode());
        assertEquals(ErrorCategory.TOOL, ex.getCategory());
        assertFalse(ex.isRetryable());
    }

    @Test
    void planModeViolationExceptionWithToolNameHasCorrectErrorCode() {
        var ex = new PlanModeViolationException("blocked", "writeFile");
        assertEquals("PLAN_MODE_VIOLATION", ex.getErrorCode());
        assertEquals(ErrorCategory.TOOL, ex.getCategory());
        assertEquals("writeFile", ex.getToolName());
    }

    @Test
    void planModeViolationExceptionWithCauseHasCorrectErrorCode() {
        var ex = new PlanModeViolationException("blocked", new RuntimeException());
        assertEquals("PLAN_MODE_VIOLATION", ex.getErrorCode());
        assertEquals(ErrorCategory.TOOL, ex.getCategory());
        assertNull(ex.getToolName());
    }

    // --- AgentException: category AGENT ---

    @Test
    void agentExceptionHasCategoryAgent() {
        var ex = new AgentException("agent fail");
        assertEquals(ErrorCategory.AGENT, ex.getCategory());
        assertNull(ex.getErrorCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void agentExceptionWithCauseHasCategoryAgent() {
        var ex = new AgentException("agent fail", new RuntimeException());
        assertEquals(ErrorCategory.AGENT, ex.getCategory());
    }

    // --- AgentInterruptedException: errorCode AGENT_INTERRUPTED ---

    @Test
    void agentInterruptedExceptionHasCorrectErrorCode() {
        var ex = new AgentInterruptedException("interrupted");
        assertEquals("AGENT_INTERRUPTED", ex.getErrorCode());
        assertEquals(ErrorCategory.AGENT, ex.getCategory());
        assertFalse(ex.isRetryable());
    }

    @Test
    void agentInterruptedExceptionWithCauseHasCorrectErrorCode() {
        var ex = new AgentInterruptedException("interrupted", new RuntimeException());
        assertEquals("AGENT_INTERRUPTED", ex.getErrorCode());
        assertEquals(ErrorCategory.AGENT, ex.getCategory());
    }

    // --- AgentExecutionException: errorCode AGENT_EXECUTION_ERROR ---

    @Test
    void agentExecutionExceptionHasCorrectErrorCode() {
        var ex = new AgentExecutionException("exec error");
        assertEquals("AGENT_EXECUTION_ERROR", ex.getErrorCode());
        assertEquals(ErrorCategory.AGENT, ex.getCategory());
        assertFalse(ex.isRetryable());
    }

    @Test
    void agentExecutionExceptionWithCauseHasCorrectErrorCode() {
        var ex = new AgentExecutionException("exec error", new RuntimeException());
        assertEquals("AGENT_EXECUTION_ERROR", ex.getErrorCode());
        assertEquals(ErrorCategory.AGENT, ex.getCategory());
    }
}
