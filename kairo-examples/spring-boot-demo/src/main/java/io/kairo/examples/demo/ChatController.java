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
package io.kairo.examples.demo;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the Kairo agent as a simple chat API.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * curl -X POST http://localhost:8080/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "What is Kairo?"}'
 * }</pre>
 */
@RestController
public class ChatController {

    private final Agent agent;

    public ChatController(Agent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody ChatRequest request) {
        Msg input = Msg.of(MsgRole.USER, request.message());
        Msg response = agent.call(input).block();
        String reply = (response != null) ? response.text() : "No response";
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    /** Simple request body for the chat endpoint. */
    public record ChatRequest(String message) {}
}
