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
package io.kairo.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AutoApproveElicitationHandlerTest {

    private final ElicitationRequest request =
            new ElicitationRequest("Provide your name", Map.of());

    @Test
    void devOnlyAutoApproveHandlerAcceptsAll() {
        DevOnlyAutoApproveHandler handler = new DevOnlyAutoApproveHandler();
        ElicitationResponse response = handler.handle(request).block();

        assertNotNull(response);
        assertEquals(ElicitationAction.ACCEPT, response.action());
    }

    @Test
    void autoDeclineHandlerDeclinesAll() {
        AutoDeclineElicitationHandler handler = new AutoDeclineElicitationHandler();
        ElicitationResponse response = handler.handle(request).block();

        assertNotNull(response);
        assertEquals(ElicitationAction.DECLINE, response.action());
    }

    @Test
    void autoApproveElicitationHandlerIsDeprecated() {
        assertTrue(AutoApproveElicitationHandler.class.isAnnotationPresent(Deprecated.class));
    }

    @Test
    void autoApproveElicitationHandlerExtendsDevOnly() {
        AutoApproveElicitationHandler handler = new AutoApproveElicitationHandler();
        assertInstanceOf(DevOnlyAutoApproveHandler.class, handler);
    }

    @Test
    void autoApproveElicitationHandlerAlsoAccepts() {
        AutoApproveElicitationHandler handler = new AutoApproveElicitationHandler();
        ElicitationResponse response = handler.handle(request).block();

        assertNotNull(response);
        assertEquals(ElicitationAction.ACCEPT, response.action());
    }
}
