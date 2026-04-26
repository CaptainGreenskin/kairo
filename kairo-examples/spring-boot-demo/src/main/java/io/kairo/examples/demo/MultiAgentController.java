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
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamCoordinator;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamResult;
import io.kairo.multiagent.team.DefaultTaskDispatchCoordinator;
import io.kairo.multiagent.team.InProcessMessageBus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller demonstrating Kairo v0.10's {@link TeamCoordinator} SPI (ADR-015, ADR-016).
 *
 * <p>This is a pure orchestration demo — no LLM API key is required. A POST to {@code
 * /multi-agent/execute} assembles a {@link Team} of in-process echo agents, hands it to a {@link
 * DefaultTaskDispatchCoordinator}, and returns the resulting {@link TeamResult}.
 *
 * <p>Example:
 *
 * <pre>{@code
 * curl -X POST http://localhost:8080/multi-agent/execute \
 *   -H "Content-Type: application/json" \
 *   -d '{"goal": "Ship the v0.10 docs", "agentIds": ["architect", "coder", "tester"]}'
 * }</pre>
 */
@RestController
@RequestMapping("/multi-agent")
public class MultiAgentController {

    private final TeamCoordinator coordinator = new DefaultTaskDispatchCoordinator();

    /**
     * Execute a single team run and return its outcome.
     *
     * @param request goal + agent roster; agent roster defaults to {@code ["agent-1"]} if omitted
     * @return serialised {@link TeamResult}
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(@RequestBody ExecuteRequest request) {
        String goal = request.goal();
        if (goal == null || goal.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "'goal' must not be blank"));
        }

        List<String> agentIds =
                request.agentIds() == null || request.agentIds().isEmpty()
                        ? List.of("agent-1")
                        : request.agentIds();
        InProcessMessageBus bus = new InProcessMessageBus();
        List<Agent> agents = new ArrayList<>();
        for (String id : agentIds) {
            agents.add(new EchoAgent(id));
            bus.registerAgent(id);
        }
        Team team = new Team("demo-team", agents, bus);

        TeamExecutionRequest execRequest =
                new TeamExecutionRequest(
                        UUID.randomUUID().toString(), goal, Map.of(), TeamConfig.defaults());

        TeamResult result = coordinator.execute(execRequest, team).block();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", result.requestId());
        body.put("status", result.status().name());
        body.put("totalDurationMs", result.totalDuration().toMillis());
        body.put(
                "stepOutcomes",
                result.stepOutcomes().stream()
                        .map(
                                step ->
                                        Map.of(
                                                "stepId",
                                                step.stepId(),
                                                "verdict",
                                                step.finalVerdict().outcome().name(),
                                                "attempts",
                                                step.attempts(),
                                                "output",
                                                step.output()))
                        .toList());
        result.finalOutput().ifPresent(out -> body.put("finalOutput", out));
        body.put("warnings", result.warnings());
        return ResponseEntity.ok(body);
    }

    /** Request body for {@link #execute(ExecuteRequest)}. */
    public record ExecuteRequest(String goal, List<String> agentIds) {}

    /** Minimal in-process {@link Agent} that echoes its prompt — no LLM required. */
    private static final class EchoAgent implements Agent {

        private final String id;

        EchoAgent(String id) {
            this.id = id;
        }

        @Override
        public Mono<Msg> call(Msg input) {
            return Mono.just(
                    Msg.of(MsgRole.ASSISTANT, "[" + id + "] acknowledged: " + input.text()));
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return id;
        }

        @Override
        public AgentState state() {
            return AgentState.IDLE;
        }

        @Override
        public void interrupt() {
            // no-op
        }
    }
}
