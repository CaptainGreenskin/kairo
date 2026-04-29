状态: DONE
创建时间: 2026-04-26
优先级: P2（M4 UX：配置文件支持）

## 目标

支持 `~/.kairo-code/config.properties` 配置文件，让用户不必每次都传 --api-key 等参数。

## 背景

每次运行 kairo-code 都要传 --api-key、--model、--base-url 很繁琐。
配置文件提供持久化默认值，CLI 参数仍可覆盖。

## 需要实现

### 1. 配置文件格式（Properties）

```
api-key=sk-xxx
model=qwen-max
base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
provider=qianwen
```

### 2. ConfigLoader.java

```java
public final class ConfigLoader {
    public static Properties load() {
        Path configFile = Path.of(System.getProperty("user.home"), ".kairo-code", "config.properties");
        // 读取 configFile，如果不存在返回空 Properties
    }
}
```

### 3. 在 KairoCodeMain.call() 优先级

CLI arg > 环境变量 > 配置文件 > 默认值

### 4. 测试：ConfigLoaderTest.java（至少 4 个用例）

- 文件不存在时返回空 Properties
- 有效文件正确解析
- 注释行被忽略
- 优先级验证

## 验收标准

- [ ] ~./kairo-code/config.properties 自动加载
- [ ] CLI 参数优先于配置文件
- [ ] 4+ 测试通过

## Agent 可以自主完成

YES
