状态: DONE
优先级: P0
模块: kairo-security-pii
标题: 恢复 PiiPattern 缺失的 IPV4 / IBAN 枚举值
Start: 2026-04-27 19:14
End: 2026-04-27 19:18
Duration: 4 min
Executor: qodercli
Commit: d618eb8
Merge: e51f79f
Score: 98/100

## 背景

PR #71（commit `00db78e`）声称添加 IPV4 + IBAN PII 模式，但实际 diff 只提交了测试文件，源码改动丢失。
导致 main 上 `kairo-security-pii` 测试编译失败：

```
PiiPatternNetworkTest.java:[31,68] cannot find symbol IPV4
PiiPatternNetworkTest.java:[33,68] cannot find symbol IBAN
PiiPatternNetworkTest.java:[97,72] cannot find symbol IPV4
PiiPatternNetworkTest.java:[97,89] cannot find symbol IBAN
```

reactor build 卡住，**当前 20 个 open PR 全部 CI red**。修这一个 enum，全部解放。

## 目标

在 `kairo-security-pii/src/main/java/io/kairo/security/pii/PiiPattern.java` 的 enum 中追加两个值，使现有的 `PiiPatternNetworkTest`（102 行，8 个 @Test）全部通过。

## 实现

文件：`kairo-security-pii/src/main/java/io/kairo/security/pii/PiiPattern.java`

在 `CHINESE_PHONE("\\b1[3-9]\\d{9}\\b", "<redacted:cn-phone>")` 后面追加（注意把 `;` 移到最后一项）：

```java
IPV4(
        "\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\b",
        "<redacted:ipv4>"),
IBAN("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b", "<redacted:iban>");
```

## 测试期待行为（来自现有 PiiPatternNetworkTest）

IPv4：
- `203.0.113.42` 命中 `<redacted:ipv4>`
- `192.168.1.100` 命中
- `10.0.0.1` + `172.16.254.1` 同串命中 2 次
- `256.0.0.1` 不命中（octet 越界）

IBAN：
- `GB82WEST12345698765432` 命中 `<redacted:iban>`
- 小写 `gb82west12345698765432` 不命中
- `GB82WEST`（< 15 字符）不命中

组合：
- `ip=10.0.0.5 iban=DE89370400440532013000` 同时命中 IPv4 + IBAN，matchCount=2

## 验收标准

- [ ] `mvn -pl kairo-security-pii -am test` 全绿
- [ ] `mvn spotless:check` 通过（如不通过先 `mvn spotless:apply`）
- [ ] PR 描述里说明这是修复 PR #71 的不完整提交
- [ ] PR 标题：`fix(kairo-security-pii): restore IPV4 and IBAN enum values dropped from #71`

## 不要做

- 不要扩大改动到测试文件以外的其他模块
- 不要改 `PiiPattern` enum 的现有值
- 不要新增 `kairo-api` SPI 改动（这是纯实现层修复）
- 不要 `--no-verify` 或 `-DskipTests`

## 影响

合并后 main CI 应自动转绿。20 个 open PR rebase 后会全部通过 —— 这是高优先级阻塞修复。
