状态: DONE
优先级: P2
模块: kairo-tools
标题: PatchApplyTool 单元测试

目标:
为 PatchApplyTool 编写单元测试。PatchApplyTool 应用 unified diff（patch）到工作区文件，
支持 1 行上下文偏移容忍，任意 hunk 失败时全量回滚。

## 需要实现

`io.kairo.tools.file.PatchApplyToolTest`（10+ 测试用例）

场景（使用 @TempDir 创建临时文件）：
- 单 hunk patch 成功应用，文件内容正确
- 多 hunk patch 成功应用
- patch 内容为空或 null → isError=true
- target 文件不存在时 patch 应用失败 → isError=true
- hunk 内容与实际文件不匹配（偏移超出容忍范围）→ isError=true，文件回滚
- 第一个 hunk 成功、第二个 hunk 失败时：文件回滚到原始内容（原子性验证）
- 1 行偏移容忍：实际行号偏移 1 行时仍成功应用
- patch 路径 `---`/`+++` header 正确解析
- 相对路径解析到 workspace root
- metadata 包含 patchedFiles 列表

### 约束
- 使用 @TempDir，不依赖外部工具（纯 Java 实现测试）
- 不修改 kairo-api/
