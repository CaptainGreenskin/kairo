状态: DONE
模块: kairo-core
标题: DateContextSource + CoordinatorConfig 单元测试

目标:
先读取两个类源码，补充测试。

测试场景（按实际 API 确定）:
- DateContextSource: 实现 ContextSource 接口，name/priority 可读，collect() 返回包含日期的内容
- CoordinatorConfig: record 属性可读，equals/hashCode

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/source/DateContextSourceTest.java
- kairo-core/src/test/java/io/kairo/core/agent/CoordinatorConfigTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
