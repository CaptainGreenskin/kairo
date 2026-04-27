# DiffTool 测试计划

模块: `kairo-tools`
测试文件: `kairo-tools/src/test/java/io/kairo/tools/file/DiffToolTest.java`
包: `io.kairo.tools.file`

## 测试场景（10+个）

所有带文件路径的测试用 ToolContext + WorkspaceRequest.writable(null) 模式（参考 BatchWriteToolTest）。
内联内容测试直接调用静态辅助方法 `DiffTool.unifiedDiff(...)` 和 `DiffTool.myersDiff(...)`。

### 1. 相同内容 → "No differences found"
```java
ToolResult r = tool.execute(Map.of(
    "originalPath", "-", "originalContent", "same\n",
    "modifiedPath", "-", "modifiedContent", "same\n"), ctx);
assertThat(r.content()).isEqualTo("No differences found");
assertThat((Boolean) r.metadata().get("hasDiff")).isFalse();
```

### 2. 单行增加 → +line 输出
```java
String diff = DiffTool.unifiedDiff("-", "-",
    new String[]{"a"}, new String[]{"a", "b"}, 3);
assertThat(diff).contains("+b");
assertThat(diff).contains("@@ -1 +1,2 @@");
```

### 3. 单行删除 → -line 输出
```java
String diff = DiffTool.unifiedDiff("-", "-",
    new String[]{"a", "b"}, new String[]{"a"}, 3);
assertThat(diff).contains("-b");
assertThat(diff).contains("@@ -1,2 +1 @@");
```

### 4. 行替换 → -old +new 输出
```java
String diff = DiffTool.unifiedDiff("-", "-",
    new String[]{"hello"}, new String[]{"world"}, 3);
assertThat(diff).contains("-hello").contains("+world");
```

### 5. contextLines=0 只显示变更行，不显示上下文
```java
String diff = DiffTool.unifiedDiff("-", "-",
    new String[]{"a", "b", "c"}, new String[]{"a", "x", "c"}, 0);
assertThat(diff).doesNotContain(" a").doesNotContain(" c");
assertThat(diff).contains("-b").contains("+x");
```

### 6. 多处变更分为多个 hunk（间距 > 2*ctx）
```java
// 10行文件，第1行和第9行各有变更，ctx=2 时应产生两个 hunk
String[] orig = {"a","b","c","d","e","f","g","h","i","j"};
String[] mod  = {"X","b","c","d","e","f","g","h","Y","j"};
String diff = DiffTool.unifiedDiff("-", "-", orig, mod, 2);
long hunkCount = diff.lines().filter(l -> l.startsWith("@@")).count();
assertThat(hunkCount).isEqualTo(2);
```

### 7. --- original / +++ modified 头正确
```java
String diff = DiffTool.unifiedDiff("orig.txt", "mod.txt",
    new String[]{"a"}, new String[]{"b"}, 3);
assertThat(diff).startsWith("--- orig.txt\n+++ mod.txt\n");
```

### 8. 空→有内容（all inserts）头正确
```java
String diff = DiffTool.unifiedDiff("-", "-",
    new String[]{}, new String[]{"a", "b"}, 3);
assertThat(diff).contains("@@ -0,0 +1,2 @@");
assertThat(diff).contains("+a").contains("+b");
```

### 9. 有内容→空（all deletes）头正确
```java
String diff = DiffTool.unifiedDiff("-", "-",
    new String[]{"a", "b"}, new String[]{}, 3);
assertThat(diff).contains("@@ -1,2 +0,0 @@");
```

### 10. 文件不存在 → isError=true
```java
ToolResult r = tool.execute(
    Map.of("originalPath", "nonexistent.txt", "modifiedPath", "-", "modifiedContent", ""), ctx);
assertThat(r.isError()).isTrue();
assertThat(r.content()).contains("File not found");
```

### 11. 路径越界 → isError=true
```java
ToolResult r = tool.execute(
    Map.of("originalPath", "../../etc/passwd", "modifiedPath", "-", "modifiedContent", ""), ctx);
assertThat(r.isError()).isTrue();
assertThat(r.content()).contains("Path traversal not allowed");
```

### 12. 基于文件路径的 diff
```java
// 写两个文件到 tempDir，然后 diff
Files.writeString(tempDir.resolve("old.txt"), "line1\nline2\n");
Files.writeString(tempDir.resolve("new.txt"), "line1\nlineX\n");
ToolResult r = tool.execute(
    Map.of("originalPath", "old.txt", "modifiedPath", "new.txt"), ctx);
assertThat((Boolean) r.metadata().get("hasDiff")).isTrue();
assertThat(r.content()).contains("-line2").contains("+lineX");
```

### 13. 缺少 originalPath → error
```java
ToolResult r = tool.execute(Map.of("modifiedPath", "-", "modifiedContent", ""), ctx);
assertThat(r.isError()).isTrue();
```

## setUp 样板

```java
@TempDir Path tempDir;
DiffTool tool;
ToolContext ctx;

@BeforeEach
void setUp() throws IOException {
    tool = new DiffTool();
    Workspace ws = new LocalDirectoryWorkspaceProvider(tempDir)
        .acquire(WorkspaceRequest.writable(null));
    ctx = new ToolContext("a", "s", Map.of(), null, null, ws);
}
```
