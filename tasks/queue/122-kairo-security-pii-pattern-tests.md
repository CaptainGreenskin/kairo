状态: DONE
模块: kairo-security-pii
标题: PiiPattern + PiiRedactionConfig 单元测试

目标:
先读取源码，为 PII 模式和配置类补充测试。

测试场景（按实际 API 确定）:
- 读取源码确认类结构
- PiiPattern: 基本属性 / enum 常量（如适用）
- PiiRedactionConfig: 构造不抛异常，属性可读

新增文件:
- kairo-security-pii/src/test/java/io/kairo/security/pii/PiiPatternTest.java
  或合并为一个测试文件

约束:
- 不修改 kairo-api/
- 先读完整源码
