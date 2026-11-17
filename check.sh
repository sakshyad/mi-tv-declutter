#!/bin/sh
# Checks your TV is still cleaned up, and with --fix puts back whatever an update undid.
#
#   sh check.sh <TV_IP>                   check the home screen
#   sh check.sh <TV_IP> --fix             restore Home when the installed variant is unambiguous
#   sh check.sh <TV_IP> --fix --home v3 my-tv.txt   restore Home v3 and your saved settings
#
# The list has one change per line (# starts a comment):
#   off com.example.app                        the app stays turned off
#   stock_home com.example.launcher            turn off a stock Home app when Declutter is available
#   global window_animation_scale 0            a setting (global, secure or system)
#   device_config activity_manager max_cached_processes 4
set -e

usage() { echo "Usage: sh check.sh <TV_IP> [--fix] [--home v1|v2|v3] [list.txt]" >&2; exit 1; }
TARGET="$1"; FIX=; LIST=; EXPECTED=
shift 2>/dev/null || true
while [ "$#" -gt 0 ]; do
  case "$1" in
    --fix) FIX=1 ;;
    --home)
      case "$2" in v1|v2|v3) EXPECTED=$2 ;; *) usage ;; esac
      shift ;;
    -*) usage ;;
    *) [ -z "$LIST" ] || usage; LIST=$1 ;;
  esac
  shift
done
[ -n "$TARGET" ] || usage
[ -z "$LIST" ] || { [ -f "$LIST" ] && [ -r "$LIST" ]; } || { echo "Can't read $LIST." >&2; exit 2; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV." >&2; exit 2; }

sh_tv() {
  # Keep adb from consuming the settings list on stdin, and preserve its exit status.
  _output=$(adb -s "$TV" shell "$@" </dev/null) || return "$?"
  printf '%s\n' "$_output" | tr -d '\r'
}
bad=0
# Prints a result line. With --fix, runs the fix and checks again.
check() { # name, test command, fix command
  if eval "$2"; then echo "  ok    $1"; return; fi
  if [ -n "$FIX" ] && eval "$3" >/dev/null 2>&1 && eval "$2"; then echo "  fixed $1"; return; fi
  echo "  WRONG $1"; bad=$((bad + 1))
}
is_off() { sh_tv pm list packages -d "$1" | grep -Fqx "package:$1"; }
installed() { sh_tv pm list packages "$1" | grep -Fqx "package:$1"; }

V1=com.ansub.tvhome/.MainActivity
V2=com.ansub.tvhome.v2/com.ansub.tvhome.MainActivity
V3=com.ansub.tvhome.v3/com.ansub.tvhome.MainActivity
home_activities() {
  sh_tv cmd package "$1" --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
    sed 's/^[[:space:]]*//; s|^com\.ansub\.tvhome/com\.ansub\.tvhome\.MainActivity$|com.ansub.tvhome/.MainActivity|'
}
home() { home_activities resolve-activity | tail -1; }
home_is_selected() { [ "$(home)" = "$ME" ]; }
select_home() {
  sh_tv cmd role add-role-holder --user 0 android.app.role.HOME "${ME%%/*}" || true
  sh_tv cmd package set-home-activity "$ME"
}

echo "Home screen"
current=$(home)
ME=
case "$EXPECTED" in
  v1) ME=$V1 ;;
  v2) ME=$V2 ;;
  v3) ME=$V3 ;;
  '') case "$current" in "$V1"|"$V2"|"$V3") ME=$current ;; esac ;;
esac
if [ -z "$ME" ] || [ "$ME" != "$current" ]; then
  available=$(home_activities query-activities | grep -Fx -e "$V1" -e "$V2" -e "$V3" | sort -u)
  if [ -n "$ME" ]; then
    if ! printf '%s\n' "$available" | grep -Fxq "$ME"; then
      echo "  WRONG Home $EXPECTED is unavailable. Install or enable it first."
      ME=
    fi
  else
    case "$available" in
      "$V1"|"$V2"|"$V3") ME=$available ;;
      '') echo "  WRONG No Mi TV Declutter launcher is available." ;;
      *) echo "  WRONG Home opens ${current:-an unknown activity}. Select your variant with --home v1, v2 or v3." ;;
    esac
  fi
fi
stock_launchers() {
  sh_tv cat /data/local/tmp/tvhome-disabled.txt 2>/dev/null || true
  if [ -n "$LIST" ]; then
    awk '$1 == "stock_home" && $2 ~ /^[[:alnum:]_.]+$/ { print $2 }' "$LIST"
  fi
}

disable_stock_launcher() {
  [ -n "$ME" ] || return 1
  sh_tv pm disable-user --user 0 "$1" >/dev/null 2>&1 || return 1
  disabled_now="$disabled_now $1"
  is_off "$1"
}

disabled_now=
for p in $(stock_launchers | sort -u); do
  if installed "$p"; then
    check "$p stays off" "is_off $p" "disable_stock_launcher $p"
  fi
done
if [ -n "$ME" ]; then
  check "Home opens ${ME%%/*}" home_is_selected select_home
  if [ -n "$disabled_now" ] && ! home_is_selected; then
    for p in $disabled_now; do
      if sh_tv pm enable --user 0 "$p" >/dev/null 2>&1 && ! is_off "$p"; then
        echo "  Re-enabled $p because the Home repair failed."
      else
        echo "  WRONG Could not re-enable $p after the Home repair failed."
      fi
    done
  fi
else
  bad=$((bad + 1))
fi

if [ -n "$LIST" ]; then
  echo "Your changes ($LIST)"
  grep -v '^[[:space:]]*#' "$LIST" | grep -v '^[[:space:]]*$' | while read -r kind a b c; do
    case "$kind" in
      off)
        if installed "$a"; then check "$a stays off" "is_off $a" "sh_tv pm disable-user --user 0 $a"
        else echo "  gone  $a (not installed)"; fi ;;
      stock_home) ;; # Already checked with the Home apps.
      global|secure|system)
        check "$kind $a = $b" "[ \"\$(sh_tv settings get $kind $a)\" = \"$b\" ]" "sh_tv settings put $kind $a $b" ;;
      device_config)
        check "$a/$b = $c" "[ \"\$(sh_tv device_config get $a $b)\" = \"$c\" ]" "sh_tv device_config put $a $b $c" ;;
      *) echo "  ?     can't read: $kind $a $b $c" ;;
    esac
  done > "${TMPDIR:-/tmp}/tvcheck.$$" || true
  cat "${TMPDIR:-/tmp}/tvcheck.$$"
  n=$(grep -c "WRONG" "${TMPDIR:-/tmp}/tvcheck.$$" || true); bad=$((bad + n))
  rm -f "${TMPDIR:-/tmp}/tvcheck.$$"
fi

if [ "$bad" -gt 0 ]; then
  [ -n "$FIX" ] && echo "$bad thing(s) could not be fixed." || echo "$bad thing(s) changed back. Run again with --fix to put them right."
  exit 1
fi
echo "All good."
