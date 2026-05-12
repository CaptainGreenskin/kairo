状态: DONE
优先级: P2
模块: kairo-tools
标题: OpenApiHttpToolTest 补充测试用例（当前 7 个，目标 10+）

目标:
OpenApiHttpToolTest 当前有 7 个测试用例，不满足 M57 Wave 2 门控（≥10 用例）。
补充 3 个以上缺失场景。

## 当前请先查看 OpenApiHttpToolTest 已覆盖的场景，不要重复。

## 建议新增场景（至少 3 个）

- path 参数替换：URL 模板 `/users/{id}` 中的 `{id}` 被实际值替换
- query 参数自动附加到 URL
- request body（JSON）在 POST 请求中正确发送
- 响应体非 JSON 时仍返回文本内容，isError=false（如 text/plain）
- operationId 不存在于 spec 中 → isError=true
- OpenAPI spec 格式非法（解析失败）→ isError=true

直接在现有 `io.kairo.tools.openapi.OpenApiHttpToolTest` 中追加测试方法，不新建文件。

### 约束
- 使用 JDK com.sun.net.httpserver.HttpServer（随机端口）
- 不修改 kairo-api/
