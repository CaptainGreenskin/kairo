状态: IN_PROGRESS
模块: kairo-tools
标题: TreeTool 单元测试

目标:
为 TreeTool 补充全面单元测试，覆盖所有参数组合和边界条件。

## 需要实现

`io.kairo.tools.file.TreeToolTest`（10+ 个测试用例）

场景：
- 空目录只输出根目录名
- 单层文件正确缩进（├──/└──）
- maxDepth=0 不展开任何子目录（只显示根）
- maxDepth=1 只展开一层
- includeFiles=false 只显示目录
- pattern="*.java" 只显示 .java 文件，目录始终显示
- excludePatterns="target,.git" 排除指定目录
- 路径不存在 → isError=true
- 路径是文件（非目录）→ isError=true
- 超过 1000 条目时截断并附提示
- totalFiles/totalDirs 元数据正确

### 约束
- 使用 JUnit 5 + @TempDir 创建临时目录
- 不修改 kairo-api/
