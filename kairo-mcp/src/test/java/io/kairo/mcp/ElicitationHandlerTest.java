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

import io.modelcontextprotocol.client.McpAsyncClient;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ElicitationHandlerTest {

    @Test
    void autoApproveHandlerReturnsAccept() {
        AutoApproveElicitationHandler handler = new AutoApproveElicitationHandler();
        ElicitationRequest request =
                new ElicitationRequest("Please provide your name", Map.of("type", "string"));

        StepVerifier.create(handler.handle(request))
                .assertNext(
                        response -> {
                            assertEquals(ElicitationAction.ACCEPT, response.action());
                            assertTrue(response.data().isEmpty());
                        })
                .verifyComplete();
    }

    @Test
    void customHandlerReceivesRequestCorrectly() {
        AtomicReference<ElicitationRequest> captured = new AtomicReference<>();
        ElicitationHandler handler =
                request -> {
                    captured.set(request);
                    return Mono.just(
                            ElicitationResponse.accept(Map.of("name", "Alice")));
                };

        ElicitationRequest request =
                new ElicitationRequest(
                        "Enter your name",
                        Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))));

        StepVerifier.create(handler.handle(request))
                .assertNext(
                        response -> {
                            assertEquals(ElicitationAction.ACCEPT, response.action());
                            assertEquals("Alice", response.data().get("name"));
                        })
                .verifyComplete();

        assertNotNull(captured.get());
        assertEquals("Enter your name", captured.get().message());
        assertFalse(captured.get().requestedSchema().isEmpty());
    }

    @Test
    void customHandlerCanDecline() {
        ElicitationHandler handler = request -> Mono.just(ElicitationResponse.decline());

        ElicitationRequest request = new ElicitationRequest("Confirm?", Map.of());

        StepVerifier.create(handler.handle(request))
                .assertNext(
                        response -> {
                            assertEquals(ElicitationAction.DECLINE, response.action());
                            assertTrue(response.data().isEmpty());
                        })
                .verifyComplete();
    }

    @Test
    void customHandlerCanCancel() {
        ElicitationHandler handler = request -> Mono.just(ElicitationResponse.cancel());

        ElicitationRequest request = new ElicitationRequest("Confirm?", Map.of());

        StepVerifier.create(handler.handle(request))
                .assertNext(
                        response -> {
                            assertEquals(ElicitationAction.CANCEL, response.action());
                            assertTrue(response.data().isEmpty());
                        })
                .verifyComplete();
    }

    @Test
    void builderOnElicitationWiresHandler() throws Exception {
        AtomicReference<ElicitationRequest> captured = new AtomicReference<>();
        ElicitationHandler handler =
                request -> {
                    captured.set(request);
                    return Mono.just(ElicitationResponse.accept(Map.of("key", "value")));
                };

        McpClientBuilder builder =
                McpClientBuilder.create("test")
                        .stdioTransport("echo", "hello")
                        .onElicitation(handler);

        // Verify the handler is wired by checking the field via reflection
        Field field = McpClientBuilder.class.getDeclaredField("elicitationHandler");
        field.setAccessible(true);
        ElicitationHandler wired = (ElicitationHandler) field.get(builder);
        assertSame(handler, wired);
    }

    @Test
    void builderUsesDefaultHandlerWhenNoneSet() throws Exception {
        McpClientBuilder builder =
                McpClientBuilder.create("test").stdioTransport("echo", "hello");

        // When no handler is set, field should be null (default is applied in build())
        Field field = McpClientBuilder.class.getDeclaredField("elicitationHandler");
        field.setAccessible(true);
        ElicitationHandler wired = (ElicitationHandler) field.get(builder);
        assertNull(wired);

        // Build should succeed without error (uses AutoApproveElicitationHandler internally)
        McpAsyncClient client = builder.build();
        assertNotNull(client);
    }

    @Test
    void builderWithCustomHandlerBuildsClient() {
        ElicitationHandler handler =
                request -> Mono.just(ElicitationResponse.accept(Map.of()));

        McpAsyncClient client =
                McpClientBuilder.create("test")
                        .stdioTransport("echo", "hello")
                        .onElicitation(handler)
                        .build();
        assertNotNull(client);
    }
}
