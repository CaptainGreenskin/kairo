#!/usr/bin/env bash
# 构建时间基线追踪。每周跑一次，记录到 tasks/perf-history.csv
# 用法：bash tasks/perf-baseline.sh
# 检测：本次 vs 过去 4 周中位数，超 +20% 写 AUTO_DECIDE_LOG 告警

set -e

REPO="${KAIRO_REPO_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || echo '.')}"
HISTORY="$REPO/tasks/perf-history.csv"
LOG="$REPO/tasks/AUTO_DECIDE_LOG.md"

cd "$REPO"

# init csv
if [[ ! -f "$HISTORY" ]]; then
    echo "date,commit,build_seconds,test_seconds,total_seconds" > "$HISTORY"
fi

COMMIT=$(git rev-parse --short HEAD)
DATE=$(date +%Y-%m-%dT%H:%M:%S)

echo "→ 跑 mvn clean compile（build 时间）..."
BUILD_START=$(date +%s)
timeout 30m mvn -q clean compile -B 2>&1 | tail -5
BUILD_END=$(date +%s)
BUILD_SEC=$((BUILD_END - BUILD_START))

echo "→ 跑 mvn test（test 时间）..."
TEST_START=$(date +%s)
timeout 30m mvn -q test -B -Dspotless.check.skip=true 2>&1 | tail -5 || true
TEST_END=$(date +%s)
TEST_SEC=$((TEST_END - TEST_START))

TOTAL_SEC=$((BUILD_SEC + TEST_SEC))

echo "$DATE,$COMMIT,$BUILD_SEC,$TEST_SEC,$TOTAL_SEC" >> "$HISTORY"

echo ""
echo "本次：build=${BUILD_SEC}s test=${TEST_SEC}s total=${TOTAL_SEC}s"

# 与过去 4 周中位数比较（取 history 倒数 4-12 行）
HIST_COUNT=$(wc -l < "$HISTORY" | tr -d ' ')
if [[ "$HIST_COUNT" -ge 5 ]]; then
    MEDIAN=$(tail -n +2 "$HISTORY" | head -n -1 | awk -F',' '{print $5}' | sort -n | awk '
        { a[NR] = $1 }
        END {
            if (NR % 2 == 1) print a[(NR+1)/2]
            else print (a[NR/2] + a[NR/2+1]) / 2
        }
    ')
    if [[ -n "$MEDIAN" && "$MEDIAN" -gt 0 ]]; then
        DELTA_PCT=$(( (TOTAL_SEC - MEDIAN) * 100 / MEDIAN ))
        echo "过去中位数：${MEDIAN}s，变化：${DELTA_PCT}%"

        if [[ "$DELTA_PCT" -gt 20 ]]; then
            echo "⚠️  构建时间 +${DELTA_PCT}%，记录告警"
            mkdir -p "$(dirname "$LOG")"
            {
                echo ""
                echo "## $DATE — 构建时间退化告警"
                echo "本次：${TOTAL_SEC}s（中位数 ${MEDIAN}s，+${DELTA_PCT}%）"
                echo "commit: $COMMIT"
            } >> "$LOG"
        fi
    fi
fi
