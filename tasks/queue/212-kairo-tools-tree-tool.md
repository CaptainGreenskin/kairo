状态: DONE
模块: kairo-tools
标题: TreeTool — 目录树展示工具

目标:
实现 TreeTool，让 Agent 能快速了解目录结构，类似 `tree` 命令。

背景:
Agent 在探索新代码库时，需要快速了解目录结构。目前只有 GlobTool 可以列文件，
但没有树形展示功能。TreeTool 提供 ASCII 树形输出。

## 需要实现

### TreeTool
`kairo-tools/src/main/java/io/kairo/tools/file/TreeTool.java`

参数:
- `path`（required）：目标目录路径
- `maxDepth`（optional，默认 3）：最大展示深度
- `showFiles`（optional，默认 true）：是否显示文件（false 只显示目录）
- `pattern`（optional）：过滤 glob 模式（如 "*.java"）

输出格式（标准 tree ASCII 格式）：
```
src/
├── main/
│   ├── java/
│   │   └── io/
│   └── resources/
└── test/
    └── java/
```

### 测试
`kairo-tools/src/test/java/io/kairo/tools/file/TreeToolTest.java`

测试场景（共 10+ 个）：
- 空目录输出只有根节点
- 单层目录正确缩进
- 深层嵌套正确缩进
- maxDepth=1 只显示一层
- showFiles=false 只显示目录
- pattern 过滤只显示匹配文件
- 路径不存在 → error
- workspace 根目录正确解析相对路径
- 末尾使用 └── 最后一项使用 ├── 之前所有项

约束:
- 不修改 kairo-api/
- 不新增外部依赖
