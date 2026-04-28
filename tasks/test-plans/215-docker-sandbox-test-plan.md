# Test Plan: Task 215 — DockerSandbox

## Setup

```java
DockerSandboxConfig config = DockerSandboxConfig.of("alpine:3.19");
DockerSandbox sandbox = new DockerSandbox(config);
```

Note: Tests requiring real Docker execution should be tagged @Tag("docker") and skipped in CI environments without Docker.

---

## DockerSandboxConfig

### T1: of() creates config with defaults
```java
DockerSandboxConfig cfg = DockerSandboxConfig.of("alpine:3.19");
assertThat(cfg.image()).isEqualTo("alpine:3.19");
assertThat(cfg.cpuLimit()).isEqualTo("0.5");
assertThat(cfg.memoryLimit()).isEqualTo("256m");
assertThat(cfg.networkMode()).isEqualTo("none");
```

### T2: Full constructor preserves values
```java
DockerSandboxConfig cfg = new DockerSandboxConfig("my-image", "1.0", "512m", "host");
assertThat(cfg.cpuLimit()).isEqualTo("1.0");
assertThat(cfg.memoryLimit()).isEqualTo("512m");
assertThat(cfg.networkMode()).isEqualTo("host");
```

---

## DockerSandbox — unit tests (no real Docker)

### T3: start() throws UnsupportedOperationException when docker not on PATH
```java
// Mock or use ProcessBuilder override to simulate docker not found
// Verify UnsupportedOperationException is thrown with message containing "docker"
```

### T4: start() throws IllegalArgumentException for non-existent workspaceRoot
```java
DockerSandbox sandbox = new DockerSandbox(config);
SandboxRequest req = new SandboxRequest("echo hi", Path.of("/nonexistent"), ...);
assertThatThrownBy(() -> sandbox.start(req)).isInstanceOf(IllegalArgumentException.class);
```

### T5: buildDockerCommand includes --rm, --cpus, -m, --network flags (via reflection/visible-for-test)
```java
// Verify command list contains:
// ["docker", "run", "--rm", "--cpus", "0.5", "-m", "256m", "--network", "none", ...]
```

### T6: readOnly=true appends :ro to volume mount
```java
// Verify -v mount string ends with ":/workspace:ro"
```

### T7: readOnly=false uses plain mount (no :ro)
```java
// Verify -v mount string ends with ":/workspace" without :ro
```

---

## DockerSandbox — integration tests (@Tag("docker"))

### T8: Simple echo command — output and exit code 0
```java
@Tag("docker")
void simpleEchoCommand() {
    Path ws = Files.createTempDirectory("docker-sandbox-test");
    SandboxRequest req = SandboxRequest.builder()
        .command("echo hello")
        .workspaceRoot(ws)
        .timeout(Duration.ofSeconds(10))
        .maxOutputBytes(1024)
        .build();
    SandboxHandle handle = sandbox.start(req);
    String output = collectOutput(handle);
    SandboxExit exit = handle.exit().block();
    assertThat(output.trim()).isEqualTo("hello");
    assertThat(exit.exitCode()).isEqualTo(0);
    assertThat(exit.timedOut()).isFalse();
}
```

### T9: Timeout enforced — long-running command killed
```java
@Tag("docker")
void timeoutIsEnforced() {
    SandboxRequest req = req("sleep 60", Duration.ofMillis(500));
    SandboxHandle handle = sandbox.start(req);
    SandboxExit exit = handle.exit().block(Duration.ofSeconds(5));
    assertThat(exit.timedOut()).isTrue();
}
```

### T10: Output truncation
```java
@Tag("docker")
void outputTruncation() {
    SandboxRequest req = SandboxRequest.builder()
        .command("yes A | head -c 10000")  // 10KB
        .maxOutputBytes(100)
        .build();
    SandboxHandle handle = sandbox.start(req);
    SandboxExit exit = handle.exit().block(Duration.ofSeconds(10));
    assertThat(exit.truncated()).isTrue();
}
```

### T11: Workspace file accessible in container
```java
@Tag("docker")
void workspaceIsAccessible() {
    Path ws = Files.createTempDirectory("ws");
    Files.writeString(ws.resolve("hello.txt"), "from host");
    SandboxRequest req = req("cat /workspace/hello.txt", ws);
    String output = collectOutput(sandbox.start(req));
    assertThat(output).contains("from host");
}
```

### T12: cancel() terminates container process
```java
@Tag("docker")
void cancelTerminatesProcess() {
    SandboxHandle handle = sandbox.start(req("sleep 60"));
    handle.cancel();
    SandboxExit exit = handle.exit().block(Duration.ofSeconds(5));
    assertThat(exit).isNotNull();
}
```
