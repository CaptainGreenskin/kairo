#!/usr/bin/env bash
# gh issue → tasks/queue 自动转换器
# 用法：bash tasks/issue-sync.sh [--label bug,enhancement] [--dry-run]
# 已转换的 issue 在任务文件 frontmatter 留 `gh_issue: <num>` 防止重复

REPO="${KAIRO_REPO_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || echo '.')}"
QUEUE="$REPO/tasks/queue"
LABELS="bug,enhancement,autopilot"
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --label) LABELS="$2"; shift 2 ;;
        --dry-run) DRY_RUN=true; shift ;;
        *) shift ;;
    esac
done

if ! command -v gh &>/dev/null; then
    echo "ERROR: 缺 gh CLI" >&2
    exit 1
fi

mkdir -p "$QUEUE"

# 已转换的 issue 编号集合
EXISTING=$(grep -h "^gh_issue:" "$QUEUE"/*.md 2>/dev/null | awk '{print $2}' | tr -d ' ' | sort -u)

# 拉 open issue（按 created 升序）
ISSUES=$(gh issue list --state open --limit 50 --label "$LABELS" \
    --json number,title,labels,body,createdAt \
    -q '.[] | "\(.number)|\(.title)|\([.labels[].name] | join(","))|\(.createdAt)"' 2>/dev/null || true)

if [[ -z "$ISSUES" ]]; then
    echo "（没找到 open issue with labels: $LABELS）"
    exit 0
fi

# 下一个空闲编号（max(*.md 编号) + 1）
NEXT_ID=$(ls "$QUEUE"/*.md 2>/dev/null | xargs -n1 basename | grep -oE '^[0-9]+' | sort -n | tail -1)
NEXT_ID=$((${NEXT_ID:-0} + 1))

CREATED=0
SKIPPED=0

while IFS='|' read -r num title labels created; do
    [[ -z "$num" ]] && continue

    # 已存在 → 跳过
    if echo "$EXISTING" | grep -qx "$num"; then
        SKIPPED=$((SKIPPED+1))
        continue
    fi

    # 优先级：bug=P1, security=P1, enhancement=P2, 其他 P3
    PRIO="P3"
    [[ "$labels" == *bug* ]] && PRIO="P1"
    [[ "$labels" == *security* ]] && PRIO="P1"
    [[ "$labels" == *enhancement* ]] && PRIO="P2"

    # 模块猜测：title 里出现 kairo-xxx
    MODULE=$(echo "$title" | grep -oE 'kairo-[a-z-]+' | head -1)
    [[ -z "$MODULE" ]] && MODULE="(待定)"

    SLUG=$(echo "$title" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/-/g; s/--*/-/g; s/^-\|-$//g' | head -c 40)
    PADDED=$(printf "%03d" "$NEXT_ID")
    TASK_FILE="$QUEUE/${PADDED}-issue-${num}-${SLUG}.md"

    if [[ "$DRY_RUN" == true ]]; then
        echo "[DRY] 会创建 $TASK_FILE"
    else
        cat > "$TASK_FILE" <<EOF
---
状态: TODO
创建时间: $(date +%Y-%m-%d)
优先级: $PRIO
模块: $MODULE
预计耗时: 30m
gh_issue: $num
里程碑: backlog
---

# Issue #$num: $title

## 目标

完成 GitHub Issue #$num 的诉求。

## 上下文

- Issue: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/issues/$num
- Labels: $labels
- 创建时间: $created

## 验收标准

- [ ] Issue body 中描述的问题已解决
- [ ] 编译通过 \`mvn compile\`
- [ ] 单元测试通过 \`mvn test\`
- [ ] PR 关联 Issue（commit msg 含 \`Fixes #$num\`）

## 备注

- agent 可自主重写本任务
- 完成后 PR 描述需 \`Fixes #$num\` 自动关闭 issue
EOF
        echo "✅ 创建 $(basename "$TASK_FILE") (P=$PRIO, mod=$MODULE)"
        CREATED=$((CREATED+1))
        NEXT_ID=$((NEXT_ID+1))
    fi
done <<< "$ISSUES"

echo ""
echo "新建：$CREATED  跳过（已转）：$SKIPPED"
