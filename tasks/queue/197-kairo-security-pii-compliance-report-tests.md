状态: DONE
模块: kairo-security-pii
标题: ComplianceReport 和完整 PII 审计管道单元测试

目标:
先读取完整源码，为 ComplianceReport 以及整个 PII 审计流程补充测试。

背景:
PII 脱敏是生产就绪 Agent 的关键安全特性。ComplianceReport 是审计输出对象，
目前无专属测试。

测试场景:
- ComplianceReport 包含正确的 violations 列表和 summary
- 多个 PII 类型被识别时 report 包含所有类型
- ComplianceReport.empty() 时 violations 为空
- PII 审计管道对干净文本返回空报告
- 脱敏后的文本不再包含 PII 原始值

新增文件:
- kairo-security-pii/src/test/java/io/kairo/security/pii/ComplianceReportTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
