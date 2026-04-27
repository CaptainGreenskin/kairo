状态: DONE
模块: kairo-tools
标题: TreeTool — 目录树可视化工具

目标:
让 Agent 能以树状格式查看项目目录结构，加快代码库导航效率。

## 需要实现

`io.kairo.tools.file.TreeTool`
- @Tool(name="tree", sideEffect=READ_ONLY)
- 参数:
  - path（optional, 默认 "."）: 起始目录（相对于 workspace root 或绝对路径）
  - maxDepth（optional, 默认 3）: 最大递归深度
  - includeFiles（optional, 默认 true）: 是否显示文件（false 则只显示目录）
  - pattern（optional）: glob 过滤，只显示匹配的文件（如 "*.java"）
  - excludePatterns（optional）: 逗号分隔的排除 glob（如 "target,*.class,.git"）
- 输出格式：类 Unix tree 命令（├── / └── / │）
- 元数据返回：totalFiles, totalDirs

### 约束
- 不修改 kairo-api/
- 不依赖系统 `tree` 命令，纯 Java NIO 实现
- 超过 1000 个条目时截断并提示
