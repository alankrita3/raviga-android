#!/bin/bash
# One session at a time on a test phone (the PM, 2026-11-03; iOS has the same for its devices).
#   tools/device_lock.sh take <serial> <session-name>   # fails (exit 1) and names the holder if someone has it
#   tools/device_lock.sh give <serial> <session-name>   # releases it (only the holder can)
#   tools/device_lock.sh who  <serial>                  # prints the holder, or "free"
# The lock is a directory (mkdir is atomic) under ~/.raviga-device-locks, with the holder and the time inside.
# A lock older than 6 hours is treated as abandoned and can be taken.
set -u
dir="$HOME/.raviga-device-locks"
mkdir -p "$dir"
cmd=${1:-}; serial=${2:-}; who=${3:-}
[ -z "$cmd" ] || [ -z "$serial" ] && { echo "usage: $0 take|give|who <serial> [session]"; exit 2; }
lock="$dir/$(echo "$serial" | tr ':/' '__').lock"
holder() { cat "$lock/holder" 2>/dev/null || echo "unknown"; }
age_s() { echo $(( $(date +%s) - $(stat -f %m "$lock" 2>/dev/null || date +%s) )); }
case $cmd in
  take)
    [ -z "$who" ] && { echo "name the session"; exit 2; }
    if mkdir "$lock" 2>/dev/null; then
      echo "$who $(date '+%Y-%m-%d %H:%M')" > "$lock/holder"; echo "taken by $who"
    elif [ "$(holder | awk '{print $1}')" = "$who" ]; then
      echo "already held by $who"
    elif [ "$(age_s)" -gt 21600 ]; then
      echo "$who $(date '+%Y-%m-%d %H:%M')" > "$lock/holder"; touch "$lock"; echo "taken by $who (old lock from $(holder) dropped)"
    else
      echo "busy: held by $(holder)"; exit 1
    fi ;;
  give)
    if [ ! -d "$lock" ]; then echo "free"
    elif [ "$(holder | awk '{print $1}')" = "$who" ]; then rm -rf "$lock"; echo "released"
    else echo "not yours: held by $(holder)"; exit 1
    fi ;;
  who) [ -d "$lock" ] && holder || echo "free" ;;
  *) echo "usage: $0 take|give|who <serial> [session]"; exit 2 ;;
esac
