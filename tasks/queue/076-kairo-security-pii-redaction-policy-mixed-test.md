状态: DONE
创建时间: 2026-04-27
优先级: P2（M6：安全增强）

## 目标

为 `PiiRedactionPolicy` 添加混合 PII 类型的集成测试，验证多种模式组合正确工作。

## 背景

现有测试在 `PiiRedactionPolicyTest` 中，但混合多种 PII（email+phone+API_KEY+JWT）的
全量脱敏场景缺乏系统性覆盖。

## 需要实现

`kairo-security-pii/src/test/java/io/kairo/security/pii/PiiRedactionPolicyMixedTest.java`

测试用例：
- email + US 电话同时被脱敏
- SSN + email + API key 同时被脱敏
- JWT token 被脱敏（不与其他 token 混淆）
- API key（sk-xxx 格式）被脱敏
- 纯净文本（无 PII）的 matchCount=0
- 多行文本（含换行符）中的 PII 被正确脱敏
- stock() 策略的默认顺序验证（所有 PiiPattern.values() 都在其中）

使用 `PiiRedactionPolicy.stock()` 全量策略测试。

## 验收标准

- [ ] 7+ 测试通过
- [ ] `mvn test -pl kairo-security-pii` 通过

## Agent 可以自主完成

YES
