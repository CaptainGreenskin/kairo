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
package io.kairo.api.hook;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HookResultTest {

    @Test
    void proceed_createsContinueResult() {
        HookResult<String> result = HookResult.proceed("event");
        assertEquals("event", result.event());
        assertEquals(HookResult.Decision.CONTINUE, result.decision());
        assertTrue(result.shouldProceed());
        assertNull(result.injectedContext());
        assertNull(result.modifiedInput());
        assertNull(result.reason());
        assertFalse(result.hasModifiedInput());
        assertFalse(result.hasInjectedContext());
    }

    @Test
    void abort_createsAbortResult() {
        HookResult<String> result = HookResult.abort("event", "blocked by policy");
        assertEquals("event", result.event());
        assertEquals(HookResult.Decision.ABORT, result.decision());
        assertFalse(result.shouldProceed());
        assertEquals("blocked by policy", result.reason());
    }

    @Test
    void modify_createsModifyResult() {
        Map<String, Object> newInput = Map.of("command", "npm test -- --bail");
        HookResult<String> result = HookResult.modify("event", newInput);
        assertEquals(HookResult.Decision.MODIFY, result.decision());
        assertTrue(result.shouldProceed());
        assertTrue(result.hasModifiedInput());
        assertEquals("npm test -- --bail", result.modifiedInput().get("command"));
    }

    @Test
    void withContext_createsContextResult() {
        HookResult<String> result = HookResult.withContext("event", "extra context");
        assertEquals(HookResult.Decision.CONTINUE, result.decision());
        assertTrue(result.shouldProceed());
        assertTrue(result.hasInjectedContext());
        assertEquals("extra context", result.injectedContext());
    }

    @Test
    void hasModifiedInput_emptyMapReturnsFalse() {
        HookResult<String> result =
                new HookResult<>("e", HookResult.Decision.MODIFY, null, Map.of(), null, null, null);
        assertFalse(result.hasModifiedInput());
    }

    @Test
    void hasInjectedContext_blankReturnsFalse() {
        HookResult<String> result =
                new HookResult<>("e", HookResult.Decision.CONTINUE, "", null, null, null, null);
        assertFalse(result.hasInjectedContext());
    }

    @Test
    @DisplayName("skip() factory creates result with SKIP decision and reason")
    void hookResult_skipFactory() {
        HookResult<String> result = HookResult.skip("event", "not needed");
        assertEquals("event", result.event());
        assertEquals(HookResult.Decision.SKIP, result.decision());
        assertEquals("not needed", result.reason());
        assertTrue(result.shouldProceed()); // SKIP still proceeds (not ABORT)
        assertTrue(result.shouldSkip());
        assertNull(result.injectedMessage());
        assertNull(result.hookSource());
    }

    @Test
    @DisplayName("inject() factory creates result with INJECT decision, message, and source")
    void hookResult_injectFactory() {
        Msg msg =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.TextContent("injected text"))
                        .build();
        HookResult<String> result = HookResult.inject("event", msg, "guardrail-hook");
        assertEquals("event", result.event());
        assertEquals(HookResult.Decision.INJECT, result.decision());
        assertSame(msg, result.injectedMessage());
        assertEquals("guardrail-hook", result.hookSource());
        assertTrue(result.hasInjectedMessage());
        assertTrue(result.shouldProceed());
        assertFalse(result.shouldSkip());
    }

    @Test
    @DisplayName("SKIP priority is between CONTINUE and ABORT")
    void hookResult_skipHasCorrectPriority() {
        assertTrue(HookResult.Decision.SKIP.priority() > HookResult.Decision.CONTINUE.priority());
        assertTrue(HookResult.Decision.SKIP.priority() > HookResult.Decision.INJECT.priority());
        assertTrue(HookResult.Decision.SKIP.priority() > HookResult.Decision.MODIFY.priority());
        assertTrue(HookResult.Decision.SKIP.priority() < HookResult.Decision.ABORT.priority());
    }

    @Test
    @DisplayName("shouldSkip() returns true only for SKIP results")
    void hookResult_shouldSkipHelper() {
        assertTrue(HookResult.skip("e", "reason").shouldSkip());
        assertFalse(HookResult.proceed("e").shouldSkip());
        assertFalse(HookResult.abort("e", "r").shouldSkip());
        assertFalse(HookResult.modify("e", Map.of()).shouldSkip());
        assertFalse(HookResult.inject("e", null, null).shouldSkip());
    }
}
