#!/usr/bin/env bash
# Minimal stdio "MCP" stand-in for tests. Writes its argv + selected env to the
# file passed via $KAIRO_TEST_PROBE_FILE, then idles on stdin so the test can
# verify the subprocess actually started and is alive before being killed.
set -eu
probe="${KAIRO_TEST_PROBE_FILE:-/dev/null}"
{
  echo "argv:$*"
  echo "KAIRO_TEST_VAR=${KAIRO_TEST_VAR:-}"
  echo "PLUGIN_ROOT_ECHO=${PLUGIN_ROOT_ECHO:-}"
  echo "PID=$$"
} > "$probe"
# Block until stdin closes — emulates a long-lived MCP server.
exec cat
