---
description: 查看待执行任务列表（按优先级排序）
argument-hint: [count, e.g. 20 or --all]
allowed-tools: Bash
---

跑 `bash tasks/next.sh $1` 显示队列中即将执行的 TODO 任务。

不带参数默认前 20 个。`--all` 显示全部。

执行完直接把脚本输出原样呈现给用户，不要解读或加额外评论。
