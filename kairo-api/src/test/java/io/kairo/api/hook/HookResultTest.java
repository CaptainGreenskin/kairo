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

import java.util.Map;
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
                new HookResult<>("e", HookResult.Decision.MODIFY, null, Map.of(), null);
        assertFalse(result.hasModifiedInput());
    }

    @Test
    void hasInjectedContext_blankReturnsFalse() {
        HookResult<String> result =
                new HookResult<>("e", HookResult.Decision.CONTINUE, "", null, null);
        assertFalse(result.hasInjectedContext());
    }
}
