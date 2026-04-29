状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：测试补全）

## 目标

为 `OpenApiHttpTool` 添加更完整的单元测试，覆盖参数注入、路径模板替换、
header 处理等核心逻辑。

## 背景

`OpenApiToolRegistrar` 的 `OpenApiToolRegistrarTest.java` 已有测试，
但 `OpenApiHttpTool` 的 HTTP 执行逻辑和参数替换缺乏测试。

## 需要实现

先读取 `OpenApiHttpTool.java` 理解接口，然后编写：

### 测试：OpenApiHttpToolTest.java

`kairo-tools/src/test/java/io/kairo/tools/openapi/OpenApiHttpToolTest.java`

使用 `com.sun.net.httpserver.HttpServer` 启动本地测试服务器，验证：
- 路径参数 `{userId}` 被正确替换
- query 参数被附加到 URL
- POST body 被正确发送
- 非 2xx 响应时 isError=true
- 超时场景返回错误
- Content-Type: application/json 时结果为 JSON 字符串

## 验收标准

- [ ] 6+ 测试通过
- [ ] `mvn test -pl kairo-tools` 通过

## Agent 可以自主完成

YES
