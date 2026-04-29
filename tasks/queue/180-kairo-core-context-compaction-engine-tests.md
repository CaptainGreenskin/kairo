状态: DONE
模块: kairo-core
标题: ContextCompactionEngine 集成测试

目标:
先读取完整源码，补充测试覆盖压缩引擎核心路径。

测试场景（按实际 API 确定）:
- needsCompaction() 基于 ContextState 返回 true/false
- compactMessages() 调用策略链
- 无策略触发时不压缩
- 策略阈值未达到时不压缩

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/ContextCompactionEngineTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
