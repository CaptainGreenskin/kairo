# {{TypeName}} — API 参考

**Package:** `io.kairo.api.{{package}}`
**稳定性:** `@Stable`（自 v1.0.0 起）
**首发版本:** v{{firstShippedVersion}}
**源码:** [`{{path}}`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/{{package}}/{{TypeName}}.java)

> 一句话定位——类型解决什么问题、谁是消费者。

## 签名

```java
// 直接粘贴实际公开签名——尽量精简、不加散文。
```

## 稳定性承诺

- v1.x 内二进制兼容（见 ADR-023）。
- 接口可新增 default 方法。
- 枚举可新增成员（消费者应使用 `default` 分支）。
- 删除 / 签名变更需主版本 bump。

## 默认实现

| 实现 | 模块 | 备注 |
|------|------|------|
| ... | ... | ... |

## 用法

```java
// 最小可运行片段——能指向 kairo-examples 下的可执行 main 就指向。
```

## 配置

| 属性 / Builder | 默认值 | 用途 |
|----------------|--------|------|
| ... | ... | ... |

## 生命周期

1. SPI 何时实例化？
2. 何时被调用（per-request / per-session / per-agent）？
3. 线程安全预期。

## 迁移策略

`@Stable`。破坏式变更走 ADR + japicmp（`docs/governance/japicmp-policy.md`）；
弃用须先落地一个次版本并在 `CHANGELOG.md` 说明，再在主版本移除。

## 相关

- ADR：`docs/adr/ADR-xxx.md`
- Census：`docs/governance/spi-census-v1.0.md`
- 测试：`kairo-api/src/test/java/io/kairo/api/{{package}}/{{TypeName}}Test.java`
