状态: DONE
模块: kairo-security-pii
标题: PiiPattern + PiiRedactionConfig 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- PiiPattern: 枚举值、pattern() 返回正则、name() 正确
- PiiRedactionConfig: 构建、enabledPatterns()、redactionChar()

新增文件:
- kairo-security-pii/src/test/java/io/kairo/security/pii/PiiPatternTest.java
- kairo-security-pii/src/test/java/io/kairo/security/pii/PiiRedactionConfigTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
