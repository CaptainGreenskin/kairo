#!/usr/bin/env bash
# 列出即将执行的 TODO 任务（按 P1→P2→P3 + 文件名编号排序）
# 用法：
#   bash tasks/next.sh           前 20 个
#   bash tasks/next.sh 50        前 50 个
#   bash tasks/next.sh --all     全部

REPO="${KAIRO_REPO_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
QUEUE="$REPO/tasks/queue"

LIMIT=20
case "$1" in
    --all|all) LIMIT=99999 ;;
    [0-9]*)    LIMIT="$1" ;;
esac

[[ -d "$QUEUE" ]] || { echo "（queue 目录不存在）"; exit 0; }

# 收集所有 TODO 任务：prio<TAB>filename<TAB>title<TAB>module<TAB>depends_on
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

for f in "$QUEUE"/*.md; do
    [[ -f "$f" ]] || continue
    status=$(awk -F': ' '/^状态:/{print $2; exit}' "$f" 2>/dev/null | tr -d ' \r')
    [[ "$status" == "TODO" ]] || continue

    prio=$(awk -F': ' '/^优先级:/{print $2; exit}' "$f" 2>/dev/null | awk '{print $1}')
    [[ -z "$prio" ]] && prio="P2"

    module=$(awk -F': ' '/^模块:/{print $2; exit}' "$f" 2>/dev/null | tr -d ' \r')
    [[ -z "$module" ]] && module="-"

    depends=$(awk -F': ' '/^depends_on:/{print $2; exit}' "$f" 2>/dev/null | tr -d ' \r')
    [[ -z "$depends" ]] && depends="-"

    # 任务标题：第一个 # 标题，去掉前缀
    title=$(awk '/^# /{sub(/^# +/, ""); print; exit}' "$f" 2>/dev/null)
    [[ -z "$title" ]] && title=$(basename "$f" .md)

    name=$(basename "$f" .md)
    printf '%s\t%s\t%s\t%s\t%s\n' "$prio" "$name" "$title" "$module" "$depends" >> "$TMP"
done

TOTAL=$(wc -l < "$TMP" | tr -d ' ')

if [[ "$TOTAL" -eq 0 ]]; then
    echo "✨ 没有 TODO 任务"
    exit 0
fi

echo "# 待执行任务（共 ${TOTAL}，显示前 ${LIMIT}）"
echo ""
echo "| # | 优先级 | 任务 | 模块 | depends |"
echo "|---|---|---|---|---|"

# 按优先级 + 文件名排序，截取 LIMIT
sort -t$'\t' -k1,1 -k2,2 "$TMP" | head -n "$LIMIT" | awk -F'\t' '
    BEGIN { i = 0 }
    {
        i++
        printf("| %d | %s | **%s** — %s | %s | %s |\n", i, $1, $2, $3, $4, $5)
    }
'
