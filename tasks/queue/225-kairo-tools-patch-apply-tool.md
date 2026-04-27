状态: DONE
模块: kairo-tools
标题: PatchApplyTool — 将 unified diff 应用到文件

目标:
实现 PatchApplyTool，配合已有 DiffTool 形成完整的"查看差异-应用补丁"工作流。
kairo-code 生成代码修改时可直接输出 diff 并调用此工具应用。

## 需要实现

`io.kairo.tools.file.PatchApplyTool`
- @Tool(name="patch_apply", sideEffect=WRITE)
- 参数：
  - patchContent（required）: unified diff 字符串（--- / +++ / @@ 格式）
  - targetPath（optional）: 覆盖 diff 中的目标文件路径（相对 workspace 根）
  - dryRun（optional，默认 false）: 验证 patch 可应用但不写入
- 行为：
  1. 解析 patchContent，提取 hunk
  2. 对每个文件：读取原始内容，逐 hunk 应用
  3. context 行验证（容忍 1 行偏移）
  4. dryRun=true 时只返回是否可以成功应用
  5. 成功时写入文件，返回修改的文件列表
- 返回：修改的文件路径列表、applied hunk 数、skipped hunk 数

### 约束
- 不修改 kairo-api/
- 不依赖外部 patch 工具（纯 Java 实现）
- hunk 应用失败时整体回滚（不部分写入）
- context 行不匹配时报告具体位置
