#!/usr/bin/env bash
#
# Kairo Spring Boot Demo — Endpoint Test Suite
#
# Usage:
#   ./test-endpoints.sh [OPTIONS]
#
# Options:
#   --skip-llm    Skip LLM-dependent endpoint tests
#   --help        Show this help message
#
# Environment Variables:
#   BASE_URL      Base URL of the demo app (default: http://localhost:8080)
#
# Examples:
#   ./test-endpoints.sh                          # Test all endpoints
#   ./test-endpoints.sh --skip-llm               # Non-LLM only
#   BASE_URL=http://192.168.1.100:8080 ./test-endpoints.sh
#

set -euo pipefail

# ─── Configuration ───────────────────────────────────────────────────────────
BASE_URL="${BASE_URL:-http://localhost:8080}"
SKIP_LLM=false
TIMEOUT_NORMAL=10
TIMEOUT_LLM=30

# ─── Counters ────────────────────────────────────────────────────────────────
TOTAL=0
PASS=0
FAIL=0
SKIP=0

# ─── Colors ──────────────────────────────────────────────────────────────────
if [[ -t 1 ]]; then
  GREEN='\033[0;32m'
  RED='\033[0;31m'
  YELLOW='\033[0;33m'
  CYAN='\033[0;36m'
  BOLD='\033[1m'
  RESET='\033[0m'
else
  GREEN='' RED='' YELLOW='' CYAN='' BOLD='' RESET=''
fi

# ─── Helpers ─────────────────────────────────────────────────────────────────

print_pass()  { echo -e "  ${GREEN}✔ PASS${RESET} $1"; }
print_fail()  { echo -e "  ${RED}✘ FAIL${RESET} $1"; }
print_skip()  { echo -e "  ${YELLOW}⊘ SKIP${RESET} $1"; }
print_info()  { echo -e "  ${YELLOW}ℹ INFO${RESET} $1"; }
print_header(){ echo -e "\n${BOLD}${CYAN}── $1 ──${RESET}\n"; }

snippet() {
  local body="$1"
  local trimmed
  trimmed="$(echo "$body" | tr -d '\n' | head -c 100)"
  if [[ ${#body} -gt 100 ]]; then
    trimmed="${trimmed}…"
  fi
  echo "$trimmed"
}

# Execute a test against an endpoint.
#   test_endpoint METHOD PATH [BODY] [TIMEOUT]
# METHOD  : GET or POST
# PATH    : e.g. /tools/list
# BODY    : JSON body (empty string for none)
# TIMEOUT : curl timeout in seconds (default TIMEOUT_NORMAL)
test_endpoint() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local timeout="${4:-$TIMEOUT_NORMAL}"
  local show_snippet="${5:-false}"

  local url="${BASE_URL}${path}"
  local label="${method} ${path}"

  TOTAL=$((TOTAL + 1))

  local tmp_file
  tmp_file="$(mktemp)"

  local http_code
  if [[ "$method" == "GET" ]]; then
    http_code=$(curl -s -o "$tmp_file" -w '%{http_code}' \
      --max-time "$timeout" "$url" 2>/dev/null) || http_code="000"
  else
    http_code=$(curl -s -o "$tmp_file" -w '%{http_code}' \
      --max-time "$timeout" \
      -X POST \
      -H 'Content-Type: application/json' \
      -d "$body" \
      "$url" 2>/dev/null) || http_code="000"
  fi

  local response_body
  response_body="$(cat "$tmp_file" 2>/dev/null || true)"
  rm -f "$tmp_file"

  if [[ "$http_code" == "200" ]]; then
    PASS=$((PASS + 1))
    print_pass "${label}  (HTTP ${http_code})"
  else
    FAIL=$((FAIL + 1))
    print_fail "${label}  (HTTP ${http_code})"
  fi

  if [[ "$show_snippet" == "true" && -n "$response_body" ]]; then
    print_info "Response: $(snippet "$response_body")"
  fi
}

show_help() {
  cat <<'EOF'
Usage: ./test-endpoints.sh [OPTIONS]

Options:
  --skip-llm    Skip LLM-dependent endpoint tests
  --help        Show this help message

Environment Variables:
  BASE_URL      Base URL of the demo app (default: http://localhost:8080)

Example:
  # Test all endpoints
  ./test-endpoints.sh

  # Test only non-LLM endpoints
  ./test-endpoints.sh --skip-llm

  # Test against a different host
  BASE_URL=http://192.168.1.100:8080 ./test-endpoints.sh
EOF
  exit 0
}

# ─── Parse Arguments ─────────────────────────────────────────────────────────
for arg in "$@"; do
  case "$arg" in
    --skip-llm) SKIP_LLM=true ;;
    --help)     show_help ;;
    *)          echo "Unknown option: $arg"; show_help ;;
  esac
done

# ─── Banner ──────────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════╗"
echo "║  Kairo Spring Boot Demo Test Suite   ║"
echo "╚══════════════════════════════════════╝"
echo ""
echo -e "  Target: ${BOLD}${BASE_URL}${RESET}"
echo -e "  Skip LLM: ${BOLD}${SKIP_LLM}${RESET}"
echo ""

# ═════════════════════════════════════════════════════════════════════════════
# Section 1: Non-LLM Endpoints
# ═════════════════════════════════════════════════════════════════════════════
print_header "Section 1: Non-LLM Endpoints (no API key required)"

test_endpoint GET  "/tools/list"
test_endpoint GET  "/hooks/metrics"
test_endpoint GET  "/multi-agent/tasks"
test_endpoint POST "/multi-agent/message" \
  '{"from":"agent-1","to":"agent-2","content":"hello"}'
test_endpoint GET  "/multi-agent/inbox/agent-2"
test_endpoint POST "/multi-agent/reset" ""
test_endpoint GET  "/secure/policy"
test_endpoint POST "/secure/test-tool" \
  '{"toolName":"readFile","action":"READ"}'
test_endpoint GET  "/models/available"

# ═════════════════════════════════════════════════════════════════════════════
# Section 2: LLM-Dependent Endpoints
# ═════════════════════════════════════════════════════════════════════════════
print_header "Section 2: LLM-Dependent Endpoints (require valid API key)"

if [[ "$SKIP_LLM" == "true" ]]; then
  SKIP=$((SKIP + 10))
  print_skip "All LLM endpoints skipped (--skip-llm)"
else
  test_endpoint POST "/chat" \
    '{"message":"Say hello in one word"}' \
    "$TIMEOUT_LLM" true

  test_endpoint GET  "/stream/chat?message=Say+hello+in+one+word" \
    "" "$TIMEOUT_LLM" true

  test_endpoint POST "/session/chat" \
    '{"sessionId":"test-1","message":"Say hello in one word"}' \
    "$TIMEOUT_LLM" true

  # Verify session history (now that session "test-1" exists)
  test_endpoint GET  "/session/test-1/history" \
    "" "$TIMEOUT_NORMAL" true

  test_endpoint POST "/tools/chat" \
    '{"message":"What is the weather in Beijing"}' \
    "$TIMEOUT_LLM" true

  test_endpoint POST "/hooks/chat" \
    '{"message":"Say hello in one word"}' \
    "$TIMEOUT_LLM" true

  test_endpoint POST "/models/chat" \
    '{"message":"Say hello in one word","provider":"openai"}' \
    "$TIMEOUT_LLM" true

  test_endpoint POST "/secure/chat" \
    '{"message":"Say hello in one word"}' \
    "$TIMEOUT_LLM" true

  test_endpoint POST "/extract" \
    '{"message":"My name is Alice and I am 30 years old"}' \
    "$TIMEOUT_LLM" true

  test_endpoint POST "/multi-agent/plan" \
    '{"task":"Write a hello world program"}' \
    "$TIMEOUT_LLM" true
fi

# ─── Summary ─────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "  ${BOLD}Results${RESET}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "  Total : ${BOLD}${TOTAL}${RESET}"
echo -e "  ${GREEN}Pass${RESET}  : ${PASS}"
echo -e "  ${RED}Fail${RESET}  : ${FAIL}"
if [[ $SKIP -gt 0 ]]; then
  echo -e "  ${YELLOW}Skip${RESET}  : ${SKIP}"
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [[ $FAIL -gt 0 ]]; then
  echo -e "\n  ${RED}${BOLD}Some tests failed.${RESET}\n"
  exit 1
else
  echo -e "\n  ${GREEN}${BOLD}All tests passed!${RESET}\n"
  exit 0
fi
