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
package io.kairo.api.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @Test
    void singleDefaultIsBackwardCompatibleSentinel() {
        assertEquals("default", TenantContext.SINGLE.tenantId());
        assertEquals("anonymous", TenantContext.SINGLE.principalId());
        assertTrue(TenantContext.SINGLE.attributes().isEmpty());
    }

    @Test
    void rejectsNullComponents() {
        assertThrows(NullPointerException.class, () -> new TenantContext(null, "user", Map.of()));
        assertThrows(NullPointerException.class, () -> new TenantContext("tenant", null, Map.of()));
        assertThrows(NullPointerException.class, () -> new TenantContext("tenant", "user", null));
    }

    @Test
    void rejectsBlankIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new TenantContext("", "user", Map.of()));
        assertThrows(
                IllegalArgumentException.class, () -> new TenantContext("   ", "user", Map.of()));
        assertThrows(
                IllegalArgumentException.class, () -> new TenantContext("tenant", "", Map.of()));
    }

    @Test
    void attributesAreDefensivelyCopied() {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("region", "us-west-2");
        TenantContext ctx = new TenantContext("acme", "alice", mutable);

        mutable.put("region", "tampered");
        mutable.put("plan", "enterprise");

        assertEquals("us-west-2", ctx.attributes().get("region"));
        assertEquals(1, ctx.attributes().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> ctx.attributes().put("region", "tampered"));
    }

    @Test
    void shorthandConstructorOmitsAttributes() {
        TenantContext ctx = new TenantContext("acme", "alice");
        assertTrue(ctx.attributes().isEmpty());
    }

    @Test
    void singleConstantIsSharedInstance() {
        assertSame(TenantContext.SINGLE, TenantContext.SINGLE);
    }
}
