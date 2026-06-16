#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-/workspaces/vps}"
PLAYIT_DIR="$ROOT_DIR/.playit"
RUN_DIR="$ROOT_DIR/tmp"
LOG_DIR="$ROOT_DIR/logs"
SECRET_PATH="${PLAYIT_SECRET_PATH:-/home/codespace/.config/playit_gg/playit.toml}"
SOCKET_PATH="$RUN_DIR/playitd.sock"
LOG_PATH="$LOG_DIR/playit.log"
PID_PATH="$RUN_DIR/playitd.pid"

mkdir -p "$PLAYIT_DIR" "$RUN_DIR" "$LOG_DIR"

if [[ -S "$SOCKET_PATH" ]] && playit --socket-path "$SOCKET_PATH" status 2>/dev/null | grep -q "Phase: running"; then
  echo "playitd is already running."
  exit 0
fi

if [[ -f "$PID_PATH" ]]; then
  OLD_PID="$(cat "$PID_PATH" 2>/dev/null || true)"
  if [[ -n "$OLD_PID" ]] && kill -0 "$OLD_PID" >/dev/null 2>&1; then
    echo "playitd process $OLD_PID is still running."
    exit 0
  fi
fi

rm -f "$SOCKET_PATH" "$PID_PATH"

setsid playitd \
  --secret-path "$SECRET_PATH" \
  --socket-path "$SOCKET_PATH" \
  -l "$LOG_PATH" \
  >/dev/null 2>&1 < /dev/null &

echo "$!" > "$PID_PATH"
echo "playitd started with pid $(cat "$PID_PATH")."
