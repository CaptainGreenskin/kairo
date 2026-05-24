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
package io.kairo.expertteam.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolInvocation;
import io.kairo.api.tool.ToolResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RoleScopedToolExecutorTest {

    private ToolExecutor delegate;
    private static final String ROLE_ID = "code-reviewer";
    private static final ToolResult SUCCESS_RESULT = ToolResult.success("id-1", "ok");

    @BeforeEach
    void setUp() {
        delegate = mock(ToolExecutor.class);
        when(delegate.execute(anyString(), anyMap())).thenReturn(Mono.just(SUCCESS_RESULT));
        when(delegate.execute(anyString(), anyMap(), any(Duration.class)))
                .thenReturn(Mono.just(SUCCESS_RESULT));
        when(delegate.executeSingle(any(ToolInvocation.class)))
                .thenReturn(Mono.just(SUCCESS_RESULT));
        when(delegate.executeParallel(any())).thenReturn(Flux.just(SUCCESS_RESULT));
    }

    @Test
    void allowedToolPassesThroughToDelegate() {
        var executor = new RoleScopedToolExecutor(delegate, List.of("read_file", "grep"), ROLE_ID);

        StepVerifier.create(executor.execute("read_file", Map.of("path", "/tmp")))
                .expectNext(SUCCESS_RESULT)
                .verifyComplete();

        verify(delegate).execute("read_file", Map.of("path", "/tmp"));
    }

    @Test
    void disallowedToolReturnsErrorWithoutCallingDelegate() {
        var executor = new RoleScopedToolExecutor(delegate, List.of("read_file", "grep"), ROLE_ID);

        StepVerifier.create(executor.execute("rm_file", Map.of()))
                .assertNext(
                        result -> {
                            assertThat(result.isError()).isTrue();
                            assertThat(result.content()).contains("rm_file");
                            assertThat(result.content()).contains(ROLE_ID);
                            assertThat(result.content()).contains("not permitted");
                        })
                .verifyComplete();

        verifyNoInteractions(delegate);
    }

    @Test
    void emptyAllowedToolsListPermitsAllTools() {
        var executor = new RoleScopedToolExecutor(delegate, List.of(), ROLE_ID);

        StepVerifier.create(executor.execute("any_tool", Map.of()))
                .expectNext(SUCCESS_RESULT)
                .verifyComplete();

        verify(delegate).execute("any_tool", Map.of());
    }

    @Test
    void disallowedToolWithTimeoutReturnsError() {
        var executor = new RoleScopedToolExecutor(delegate, List.of("read_file"), ROLE_ID);

        StepVerifier.create(executor.execute("write_file", Map.of(), Duration.ofSeconds(5)))
                .assertNext(
                        result -> {
                            assertThat(result.isError()).isTrue();
                            assertThat(result.content()).contains("write_file");
                        })
                .verifyComplete();

        verify(delegate, never()).execute(anyString(), anyMap(), any(Duration.class));
    }

    @Test
    void allowedToolWithTimeoutPassesThrough() {
        var executor = new RoleScopedToolExecutor(delegate, List.of("read_file"), ROLE_ID);
        Duration timeout = Duration.ofSeconds(10);

        StepVerifier.create(executor.execute("read_file", Map.of(), timeout))
                .expectNext(SUCCESS_RESULT)
                .verifyComplete();

        verify(delegate).execute("read_file", Map.of(), timeout);
    }

    @Test
    void executeSingleBlocksDisallowedTool() {
        var executor = new RoleScopedToolExecutor(delegate, List.of("read_file"), ROLE_ID);
        var invocation = new ToolInvocation("write_file", Map.of(), "call-123");

        StepVerifier.create(executor.executeSingle(invocation))
                .assertNext(
                        result -> {
                            assertThat(result.isError()).isTrue();
                            assertThat(result.content()).contains("write_file");
                            assertThat(result.toolUseId()).isEqualTo("call-123");
                        })
                .verifyComplete();

        verify(delegate, never()).executeSingle(any());
    }

    @Test
    void executeSinglePermitsAllowedTool() {
        var executor = new RoleScopedToolExecutor(delegate, List.of("read_file"), ROLE_ID);
        var invocation = new ToolInvocation("read_file", Map.of(), "call-456");

        StepVerifier.create(executor.executeSingle(invocation))
                .expectNext(SUCCESS_RESULT)
                .verifyComplete();

        verify(delegate).executeSingle(invocation);
    }

    @Test
    void executeParallelFiltersBlockedInvocations() {
        var allowedResult = ToolResult.success("id-a", "allowed-output");
        when(delegate.executeParallel(any())).thenReturn(Flux.just(allowedResult));

        var executor = new RoleScopedToolExecutor(delegate, List.of("read_file"), ROLE_ID);
        var invocations =
                List.of(
                        new ToolInvocation("read_file", Map.of(), "call-a"),
                        new ToolInvocation("write_file", Map.of(), "call-b"));

        StepVerifier.create(executor.executeParallel(invocations))
                .assertNext(result -> assertThat(result.content()).isEqualTo("allowed-output"))
                .assertNext(
                        result -> {
                            assertThat(result.isError()).isTrue();
                            assertThat(result.content()).contains("write_file");
                            assertThat(result.toolUseId()).isEqualTo("call-b");
                        })
                .verifyComplete();

        // Only the allowed invocation should reach delegate
        verify(delegate)
                .executeParallel(List.of(new ToolInvocation("read_file", Map.of(), "call-a")));
    }

    @Test
    void executeParallelAllBlockedReturnsOnlyErrors() {
        var executor = new RoleScopedToolExecutor(delegate, List.of("read_file"), ROLE_ID);
        var invocations =
                List.of(
                        new ToolInvocation("write_file", Map.of(), "call-1"),
                        new ToolInvocation("delete_file", Map.of(), "call-2"));

        StepVerifier.create(executor.executeParallel(invocations))
                .assertNext(
                        result -> {
                            assertThat(result.isError()).isTrue();
                            assertThat(result.content()).contains("write_file");
                        })
                .assertNext(
                        result -> {
                            assertThat(result.isError()).isTrue();
                            assertThat(result.content()).contains("delete_file");
                        })
                .verifyComplete();

        verify(delegate, never()).executeParallel(any());
    }

    @Test
    void errorMessageIncludesRoleNameAndAllowedList() {
        var executor = new RoleScopedToolExecutor(delegate, List.of("read_file", "grep"), ROLE_ID);

        StepVerifier.create(executor.execute("forbidden_tool", Map.of()))
                .assertNext(
                        result -> {
                            String content = result.content();
                            assertThat(content).contains("forbidden_tool");
                            assertThat(content).contains(ROLE_ID);
                            assertThat(content).contains("Allowed tools:");
                            assertThat(content).contains("read_file");
                            assertThat(content).contains("grep");
                        })
                .verifyComplete();
    }

    @Test
    void constructorRejectsNullDelegate() {
        assertThatThrownBy(() -> new RoleScopedToolExecutor(null, List.of(), ROLE_ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("delegate");
    }

    @Test
    void constructorRejectsNullAllowedTools() {
        assertThatThrownBy(() -> new RoleScopedToolExecutor(delegate, null, ROLE_ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("allowedTools");
    }

    @Test
    void constructorRejectsBlankRoleId() {
        assertThatThrownBy(() -> new RoleScopedToolExecutor(delegate, List.of(), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roleId");
    }

    @Test
    void constructorRejectsNullRoleId() {
        assertThatThrownBy(() -> new RoleScopedToolExecutor(delegate, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roleId");
    }
}
