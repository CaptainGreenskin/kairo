状态: DONE
创建时间: 2026-04-27
优先级: P2（M6：安全增强）

## 目标

在 `PiiPattern` 中新增 IPv4 地址和 IBAN 银行账号两种敏感模式，并补充测试。

## 背景

PiiPattern 目前覆盖 email、电话、信用卡、SSN、API Key、JWT、中国身份证/手机。
IPv4 地址（内网+公网）和 IBAN 是企业级脱敏场景中常见的两种 PII。

## 需要实现

### 1. PiiPattern.java 新增

```
IPV4("\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b", "<redacted:ipv4>"),
IBAN("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b", "<redacted:iban>"),
```

### 2. 新建测试文件

`kairo-security-pii/src/test/java/io/kairo/security/pii/PiiPatternNetworkTest.java`

测试用例：
- IPv4 公网地址被脱敏
- IPv4 私网地址（192.168.x.x）被脱敏
- 无效 IP（256.0.0.1）不匹配
- IBAN 地址被脱敏（如 GB82WEST12345698765432）
- 非 IBAN 格式不匹配
- IPv4 + IBAN 混合文本全部脱敏

用独立的 `PiiRedactionConfig.of(PiiPattern.IPV4)` 避免与 CREDIT_CARD 干扰。

## 验收标准

- [ ] 6+ 新测试通过
- [ ] `mvn test -pl kairo-security-pii` 通过

## Agent 可以自主完成

YES
