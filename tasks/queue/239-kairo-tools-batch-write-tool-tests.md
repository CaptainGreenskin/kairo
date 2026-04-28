状态: TODO
模块: kairo-tools
标题: BatchWriteTool 单元测试

目标:
为 BatchWriteTool 补充单元测试，验证两阶段写入、路径遍历防护和回滚逻辑。

## 需要实现

`io.kairo.tools.file.BatchWriteToolTest`（10+ 个测试用例）

场景：
- 正常写入多文件，验证内容和字节数
- dryRun=true 验证路径但不写入
- createDirs=true 自动创建父目录
- createDirs=false 父目录不存在时报错
- 路径遍历攻击（"../etc/passwd"）→ isError=true，不写入任何文件
- 超过 50 个文件 → isError=true
- files 参数缺失 → isError=true
- 单个文件缺少 path 字段 → isError=true
- 单个文件缺少 content 字段 → isError=true
- 验证返回的 filesWritten 计数准确

### 约束
- 使用 @TempDir 作为 workspace root
- 不修改 kairo-api/
