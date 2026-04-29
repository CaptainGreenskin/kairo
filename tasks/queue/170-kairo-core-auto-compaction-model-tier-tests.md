状态: DONE
模块: kairo-core
标题: AutoCompaction + ModelTier 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- AutoCompaction: name()、priority()、shouldTrigger 阈值（默认 0.95f）
- ModelTier: 枚举值存在（STANDARD/PREMIUM 等）、fromModel() 方法映射

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/compaction/AutoCompactionTest.java
- kairo-core/src/test/java/io/kairo/core/model/ModelTierTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
