状态: DONE
模块: kairo-tools
标题: TreeTool — 目录树 ASCII 展示工具

目标:
实现 TreeTool，让 Agent 能快速了解目录结构（类似 tree 命令）。

背景:
Agent 探索新代码库时需要快速了解目录结构。GlobTool 只列文件路径，
没有层级视觉。TreeTool 提供标准 ASCII 树形输出。

## 需要实现

### TreeTool
`kairo-tools/src/main/java/io/kairo/tools/file/TreeTool.java`

参数:
- `path`（required）：目标目录路径
- `maxDepth`（optional，默认 3）：最大展示深度（0=只显示根）
- `showFiles`（optional，默认 true）：false 时只显示目录
- `pattern`（optional）：文件名 glob 过滤（如 "*.java"，仅对文件生效）

输出格式示例：
```
src/
├── main/
│   └── java/
│       └── Foo.java
└── test/
    └── java/
        └── FooTest.java
```
- 使用 `├── ` 表示非末尾项，`└── ` 表示末尾项
- 目录名后加 `/`，文件不加
- maxDepth 达到时停止展开子目录

### 测试（TreeToolTest）
`kairo-tools/src/test/java/io/kairo/tools/file/TreeToolTest.java`

场景（共 10+ 个）：
- 空目录只输出根目录名
- 单层文件正确缩进和符号
- 末尾项用 └──，非末尾用 ├──
- maxDepth=0 不展开任何子目录
- maxDepth=1 只展开一层
- showFiles=false 只显示目录
- pattern="*.java" 只显示 .java 文件（目录始终显示）
- 路径不存在 → error
- 目录名后有 /
- workspace 模式相对路径解析

约束:
- 不修改 kairo-api/
- 不新增外部依赖
