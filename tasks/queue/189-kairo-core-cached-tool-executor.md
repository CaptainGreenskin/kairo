状态: DONE
模块: kairo-core
标题: CachingToolExecutor — 幂等工具调用缓存

目标:
实现 CachingToolExecutor 装饰器，对相同的工具调用（同名 + 同参数）返回缓存结果，
避免重复执行读取类工具（ReadTool、GrepTool 等）。

实现要求:
- 类路径: kairo-core/src/main/java/io/kairo/core/tool/CachingToolExecutor.java
- 实现 ToolExecutor SPI，装饰委托给另一个 ToolExecutor
- 缓存 key: toolName + 参数 JSON（排序后）
- 可配置最大缓存条目数和 TTL
- 提供 invalidate(toolName) 和 clear() 方法

测试文件:
- kairo-core/src/test/java/io/kairo/core/tool/CachingToolExecutorTest.java

测试场景:
- 首次调用委托执行
- 相同参数第二次返回缓存
- 参数不同不共享缓存
- TTL 过期后重新执行
- invalidate 使指定工具缓存失效

约束:
- 不修改 kairo-api/
- 不引入新的外部依赖
