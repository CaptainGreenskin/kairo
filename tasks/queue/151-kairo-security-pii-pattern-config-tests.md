状态: DONE
模块: kairo-security-pii
标题: PiiPattern + PiiRedactionConfig 单元测试

目标:
先读取两个类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- PiiPattern: 模式名、正则匹配、常见 PII 类型（EMAIL/PHONE/SSN）检测
- PiiRedactionConfig: 构建配置，enabled/disabled，getPatterns()

新增文件:
- kairo-security-pii/src/test/java/io/kairo/security/pii/PiiPatternTest.java
- kairo-security-pii/src/test/java/io/kairo/security/pii/PiiRedactionConfigTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
