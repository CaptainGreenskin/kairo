#!/usr/bin/env bash
# 每周一摘要：聚合最近 7 天 AUTO_DECIDE_LOG + 完成任务 + PR/release
# 用法：bash tasks/weekly-digest.sh [--days 7]
# 输出到 stdout（可重定向到文件 / 钉钉 webhook / 邮件）

REPO="${KAIRO_REPO_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
DAYS=7
[[ "$1" == "--days" && -n "$2" ]] && DAYS="$2"

SINCE=$(date -v-${DAYS}d +%Y-%m-%d 2>/dev/null || date -d "$DAYS days ago" +%Y-%m-%d)
TODAY=$(date +%Y-%m-%d)

echo "# Kairo 周报 ($SINCE → $TODAY)"
echo ""

# === 任务统计 ===
DONE=$(grep -l "^状态: DONE" "$REPO/tasks/queue"/*.md 2>/dev/null | wc -l | tr -d ' ')
TODO=$(grep -l "^状态: TODO" "$REPO/tasks/queue"/*.md 2>/dev/null | wc -l | tr -d ' ')
BLOCKED=$(grep -l "^状态: BLOCKED" "$REPO/tasks/queue"/*.md 2>/dev/null | wc -l | tr -d ' ')

echo "## 📈 任务进度"
echo "- DONE 累计: $DONE"
echo "- TODO 待办: $TODO"
echo "- BLOCKED: $BLOCKED"

# 完成率
TOTAL=$((DONE + TODO + BLOCKED))
if [[ "$TOTAL" -gt 0 ]]; then
    PCT=$((DONE * 100 / TOTAL))
    echo "- 完成率: ${PCT}%"
fi
echo ""

# === 本周 git 活动 ===
echo "## 🚀 本周提交"
COMMITS=$(git -C "$REPO" log --oneline --since="$SINCE" 2>/dev/null | wc -l | tr -d ' ')
echo "$COMMITS commits since $SINCE"
echo ""
git -C "$REPO" log --oneline --since="$SINCE" 2>/dev/null | head -20
echo ""

# === 本周 PR 活动 ===
if command -v gh &>/dev/null; then
    echo "## 📦 本周 PR"
    MERGED=$(gh pr list --state merged --search "merged:>=$SINCE" --limit 50 \
        --json number,title -q '.[] | "  - #\(.number) \(.title)"' 2>/dev/null || echo "")
    if [[ -n "$MERGED" ]]; then
        echo "已 merge:"
        echo "$MERGED"
    fi
    OPEN=$(gh pr list --state open --limit 20 \
        --json number,title,statusCheckRollup -q '.[] | "  - #\(.number) \(.title) [\([.statusCheckRollup[].conclusion // "PENDING"] | unique | join(","))]"' 2>/dev/null || echo "")
    if [[ -n "$OPEN" ]]; then
        echo ""
        echo "待 merge:"
        echo "$OPEN"
    fi
    echo ""
fi

# === 本周 release ===
echo "## 🏷  本周 release"
TAGS=$(git -C "$REPO" log --tags --simplify-by-decoration --pretty="format:%ai %d" --since="$SINCE" 2>/dev/null | grep -E "tag:" || echo "")
if [[ -n "$TAGS" ]]; then
    echo "$TAGS"
else
    echo "（本周无 release）"
fi
echo ""

# === 自主决策聚合 ===
LOG="$REPO/tasks/AUTO_DECIDE_LOG.md"
if [[ -f "$LOG" ]]; then
    echo "## 🤖 自主决策（auto-decide）"
    # 抽取本周内的章节
    awk -v cutoff="$SINCE" '
        /^## [0-9]+-[0-9]+-[0-9]+/ {
            d = substr($2, 1, 10)
            keep = (d >= cutoff)
        }
        keep { print }
    ' "$LOG" | head -80
    echo ""

    # 分类统计
    SPI_BREAK=$(awk -v cutoff="$SINCE" '/^## [0-9]+-[0-9]+-[0-9]+/{d=substr($2,1,10); keep=(d>=cutoff)} keep && /SPI BREAKING/{n++} END{print n+0}' "$LOG")
    PR_FAIL=$(awk -v cutoff="$SINCE" '/^## [0-9]+-[0-9]+-[0-9]+/{d=substr($2,1,10); keep=(d>=cutoff)} keep && /CI 红/{n++} END{print n+0}' "$LOG")
    RELEASE=$(awk -v cutoff="$SINCE" '/^## [0-9]+-[0-9]+-[0-9]+/{d=substr($2,1,10); keep=(d>=cutoff)} keep && /Release v/{n++} END{print n+0}' "$LOG")
    echo "本周决策分类："
    echo "- SPI BREAKING change: $SPI_BREAK"
    echo "- PR CI 失败: $PR_FAIL"
    echo "- 自动 release: $RELEASE"
    echo ""
fi

# === 路径校正报告 ===
LATEST_REVIEW=$(ls -t "$REPO/tasks/"REVIEW_*.md 2>/dev/null | head -1)
if [[ -n "$LATEST_REVIEW" ]]; then
    REVIEW_DATE=$(awk -F': ' '/^review_date:/{print $2; exit}' "$LATEST_REVIEW" 2>/dev/null)
    if [[ "$REVIEW_DATE" >= "$SINCE" ]]; then
        echo "## 📋 本周路径校正"
        echo "$(basename "$LATEST_REVIEW")"
        # Q5 结论
        awk '/^## Q5/{found=1} found' "$LATEST_REVIEW" 2>/dev/null | head -10
        echo ""
    fi
fi

# === Token 成本周累计 ===
USAGE="$HOME/.kairo-code/usage.jsonl"
if [[ -f "$USAGE" ]]; then
    WEEK_COST=$(awk -v cutoff="$SINCE" -F'"' '$0 >= cutoff { for(i=1;i<NF;i++) if($i=="cost") { gsub(/[:, ]/,"",$(i+1)); s+=$(i+1) } } END { printf "%.2f", s }' "$USAGE" 2>/dev/null)
    if [[ -n "$WEEK_COST" && "$WEEK_COST" != "0.00" ]]; then
        echo "## 💰 本周 token 成本"
        echo "\$$WEEK_COST"
        echo ""
    fi
fi

echo "---"
echo "_自动生成于 $(date +%Y-%m-%dT%H:%M:%S)_"
