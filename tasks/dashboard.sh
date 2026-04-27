#!/usr/bin/env bash
# 任务队列优先级 + 模块看板。被 review.sh 内嵌调用，也可独立跑。
# 输出 markdown 表格。
# 注意：兼容 bash 3.2（macOS 默认）—— 不用 declare -A，用临时文件/awk

REPO="${KAIRO_REPO_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
QUEUE="$REPO/tasks/queue"

[[ -d "$QUEUE" ]] || { echo "（queue 目录不存在）"; exit 0; }

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

# 单遍解析所有任务，输出 status\tprio\tmodule
for f in "$QUEUE"/*.md; do
    [[ -f "$f" ]] || continue
    status=$(awk -F': ' '/^状态:/{print $2; exit}' "$f" 2>/dev/null | tr -d ' \r')
    prio=$(awk -F': ' '/^优先级:/{print $2; exit}' "$f" 2>/dev/null | awk '{print $1}')
    module=$(awk -F': ' '/^模块:/{print $2; exit}' "$f" 2>/dev/null | tr -d ' \r')
    [[ -z "$prio" ]] && prio="P2"
    [[ -z "$module" ]] && module="(未标)"
    [[ -z "$status" ]] && continue
    printf '%s\t%s\t%s\n' "$status" "$prio" "$module" >> "$TMP"
done

count() { awk -F'\t' -v s="$1" -v p="${2:-}" -v m="${3:-}" '
    $1==s && (p=="" || $2==p) && (m=="" || $3==m) { n++ }
    END { print n+0 }
' "$TMP"; }

TODO=$(count TODO)
PROG=$(count IN_PROGRESS)
BLOCK=$(count BLOCKED)
DONE=$(count DONE)
TOTAL=$((TODO + PROG + BLOCK + DONE))

echo "## 📊 队列看板"
echo ""
echo "| 状态 | 数量 |"
echo "|---|---|"
echo "| TODO | $TODO |"
echo "| IN_PROGRESS | $PROG |"
echo "| BLOCKED | $BLOCK |"
echo "| DONE | $DONE |"
echo "| **TOTAL** | **$TOTAL** |"

if [[ "$TODO" -gt 0 ]]; then
    echo ""
    echo "### TODO 按优先级"
    echo "| P1 | P2 | P3 |"
    echo "|---|---|---|"
    echo "| $(count TODO P1) | $(count TODO P2) | $(count TODO P3) |"
fi

if [[ "$BLOCK" -gt 0 ]]; then
    echo ""
    echo "### BLOCKED 按优先级"
    echo "| P1 | P2 | P3 |"
    echo "|---|---|---|"
    echo "| $(count BLOCKED P1) | $(count BLOCKED P2) | $(count BLOCKED P3) |"
fi

# top-5 模块（TODO + DONE 合并）
if [[ "$TOTAL" -gt 0 ]]; then
    echo ""
    echo "### Top-5 模块（TODO / DONE）"
    echo "| 模块 | TODO | DONE |"
    echo "|---|---|---|"
    awk -F'\t' '
        $1=="TODO" || $1=="DONE" { mods[$3]++ }
        END { for (m in mods) print mods[m], m }
    ' "$TMP" | sort -rn | head -5 | while read -r _ mod; do
        td=$(count TODO "" "$mod")
        dn=$(count DONE "" "$mod")
        echo "| $mod | $td | $dn |"
    done
fi
