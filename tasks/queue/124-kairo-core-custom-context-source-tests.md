状态: DONE
模块: kairo-core
标题: CustomContextSource 单元测试

目标:
先读取 CustomContextSource 完整源码，补充测试。

测试场景（按实际 API 确定）:
- 读取源码确认类结构
- 实现 ContextSource 接口
- name/priority/isActive 等属性可读
- collect() 不抛异常

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/source/CustomContextSourceTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
