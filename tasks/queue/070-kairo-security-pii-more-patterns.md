状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：安全增强）

## 目标

扩展 `kairo-security-pii` 中的 PII 模式，添加 email、电话号码、SSN 等常见
敏感数据模式，并补充测试覆盖。

## 背景

当前 `PiiPattern` 只有基础模式（信用卡、API Key 等）。企业级 PII 脱敏
需要覆盖 email、电话、社会安全号等。PiiRedactionPolicyTest 也需要更多测试用例。

## 需要实现

先读取 `PiiPattern.java` 和 `PiiRedactionPolicy.java` 理解现有实现，然后：

### 1. 扩展 PiiPattern.java

新增模式（正则表达式）：
- `EMAIL`：标准 email 格式 `[\w.+-]+@[\w-]+\.[\w.]+`
- `PHONE_US`：美国电话 `(\+1[-.\s]?)?\(?[0-9]{3}\)?[-.\s][0-9]{3}[-.\s][0-9]{4}`
- `SSN`：美国 SSN `\d{3}-\d{2}-\d{4}`
- `CHINESE_ID`：中国身份证 `\d{17}[\dX]`
- `CHINESE_PHONE`：中国手机 `1[3-9]\d{9}`

### 2. 补充测试

在 `PiiRedactionPolicyTest.java` 中（或新建 `PiiPatternTest.java`）添加：
- email 被脱敏
- 美国电话被脱敏
- SSN 被脱敏
- 中国身份证被脱敏
- 中国手机号被脱敏
- 多种 PII 混合文本全部脱敏

## 验收标准

- [ ] 5+ 新测试通过
- [ ] `mvn test -pl kairo-security-pii` 通过
- [ ] 不修改 kairo-api/ 任何文件

## Agent 可以自主完成

YES
