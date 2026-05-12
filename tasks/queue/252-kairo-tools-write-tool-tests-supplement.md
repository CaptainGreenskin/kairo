状态: DONE
优先级: P1
模块: kairo-tools
标题: WriteToolTest 补充测试用例（当前 8 个，目标 10+）

目标:
WriteToolTest 当前有 8 个测试用例，不满足 M57 Wave 1 门控（每个核心工具 ≥10 用例）。
补充 2 个以上缺失场景。

## 当前已覆盖场景（不要重复）

- writeNewFile
- overwriteExistingFile
- writeCreatesParentDirectories
- writeMissingPathParameter
- writeMissingContentParameter
- writeEmptyContent
- relativePathResolvesAgainstWorkspaceRoot
- writeReportsBytesWritten

## 需要新增场景（至少 2 个）

建议补充：
- Unicode 内容写入后再读取内容相同（验证 UTF-8 编码正确）
- 写入包含换行符 `\r\n` 的内容时内容被原样保留（不被转换）
- path 含嵌套不存在目录时 parent dirs 被自动创建（已有 writeCreatesParentDirectories，
  可补充：三层嵌套路径）
- 写入后 FileAccessTracker 收到通知（如果 tracker 注入非 null）

直接在现有 `io.kairo.tools.file.WriteToolTest` 中追加测试方法，不新建文件。

### 约束
- 使用 @TempDir 隔离
- 不修改 kairo-api/
