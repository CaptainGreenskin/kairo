状态: DONE
模块: kairo-core
标题: SessionSnapshot 单元测试

目标:
先读取 SessionSnapshot 源码（kairo-core/.../session/SessionSnapshot.java），再补充测试。

测试场景（按实际 API 确定）:
- 构造并访问各字段
- record/class 类型的 equals 和 hashCode
- toString 包含关键字段

新增文件:
- kairo-core/src/test/java/io/kairo/core/session/SessionSnapshotTest.java

约束:
- 不修改 kairo-api/
- 先读取完整源码确认字段
