# Test Plan: Task 216 — MvnTool

## Setup

```java
MvnTool tool = new MvnTool();
Path tmpDir = Files.createTempDirectory("mvn-tool-test");
```

---

## Parameter validation

### T1: missing goals → error result
```java
ToolResult r = tool.execute(Map.of());
assertThat(r.isError()).isTrue();
assertThat(r.content()).contains("goals");
```

### T2: empty goals list → error result
```java
ToolResult r = tool.execute(Map.of("goals", List.of()));
assertThat(r.isError()).isTrue();
```

---

## parseFailedTests (unit test via reflection or package-private)

### T3: parses single failed test
```java
String output = "[ERROR] io.kairo.SomeTest.badMethod -- Time elapsed: 0.1 s <<< FAILURE!";
List<String> failed = MvnTool.parseFailedTests(output);
assertThat(failed).containsExactly("io.kairo.SomeTest.badMethod");
```

### T4: parses ERROR signal too
```java
String output = "[ERROR] io.kairo.FooTest.bar -- Time elapsed: 0.2 s <<< ERROR!";
assertThat(MvnTool.parseFailedTests(output)).containsExactly("io.kairo.FooTest.bar");
```

### T5: no failed tests in successful output
```java
String output = "[INFO] BUILD SUCCESS\n[INFO] Total time: 3.2 s";
assertThat(MvnTool.parseFailedTests(output)).isEmpty();
```

### T6: multiple failures parsed correctly
```java
String output = """
    [ERROR] io.kairo.ATest.a -- Time elapsed: 0.1 s <<< FAILURE!
    [ERROR] io.kairo.BTest.b -- Time elapsed: 0.2 s <<< ERROR!
    """;
assertThat(MvnTool.parseFailedTests(output)).containsExactlyInAnyOrder(
    "io.kairo.ATest.a", "io.kairo.BTest.b");
```

---

## drainOutput ring buffer

### T7: output shorter than maxBytes preserved exactly
```java
byte[] data = "hello world".getBytes(UTF_8);
InputStream in = new ByteArrayInputStream(data);
byte[] result = MvnTool.drainOutput(in, 100);
assertThat(new String(result, UTF_8)).isEqualTo("hello world");
```

### T8: output longer than maxBytes keeps tail
```java
byte[] data = ("A".repeat(80_000) + "TAIL").getBytes(UTF_8);
InputStream in = new ByteArrayInputStream(data);
byte[] result = MvnTool.drainOutput(in, 100_000);
String s = new String(result, UTF_8);
assertThat(s).endsWith("TAIL");
// if data > maxBytes, last maxBytes are kept:
byte[] overLimit = ("X".repeat(120_000) + "END").getBytes(UTF_8);
result = MvnTool.drainOutput(new ByteArrayInputStream(overLimit), 100_000);
assertThat(new String(result, UTF_8)).endsWith("END");
```

---

## buildCommand

### T9: goals + profiles + skipTests assembled correctly
```java
List<String> cmd = MvnTool.buildCommand(
    List.of("test", "-pl", "kairo-core"),
    List.of("-Pintegration-tests"),
    true);
assertThat(cmd).containsExactly(
    "mvn", "test", "-pl", "kairo-core", "-Pintegration-tests", "-DskipTests");
```

### T10: skipTests=false — no -DskipTests flag
```java
List<String> cmd = MvnTool.buildCommand(List.of("compile"), List.of(), false);
assertThat(cmd).doesNotContain("-DskipTests");
```

---

## Integration (requires Maven on PATH)

### T11: compile a minimal Maven project → exitCode 0, buildSuccess=true
```java
// Create minimal pom.xml in tmpDir
// tool.execute(Map.of("goals", List.of("compile"), "workingDir", tmpDir.toString()))
// assertThat(result.metadata().get("buildSuccess")).isEqualTo(true)
```

### T12: failing build → buildSuccess=false, exitCode != 0
```java
// goals: ["test", "-pl", "nonexistent-module"]
// assertThat(result.metadata().get("buildSuccess")).isEqualTo(false)
```

### T13: timeout fires → timedOut=true in metadata
```java
// goals: ["install"] on huge project, timeout: 1
// assertThat(result.metadata()).containsEntry("timedOut", true)
```
