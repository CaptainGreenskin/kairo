状态: DONE
模块: kairo-core
标题: BoundaryMarkerManager 单元测试

目标:
先读取 BoundaryMarkerManager 完整源码，补充测试。

测试场景（按实际 API 确定）:
- 读取源码确认类结构和公开 API
- 构造不抛异常
- 标记插入/查询/清除基本操作
- 边界条件：空状态查询

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/BoundaryMarkerManagerTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
