#!/bin/sh
# Home v2 (com.ansub.tvhome.v2) and Home v3 (com.ansub.tvhome.v3, v2 plus the wallpaper tinted in the
# focused app's colour) install next to the original home screen (com.ansub.tvhome). This builds and
# installs them and picks which one the Home button opens.
#
#   sh v2.sh <TV_IP> install v2|v3   build that one, put it on the TV and make it the home screen
#   sh v2.sh <TV_IP> use v1|v2|v3    Home opens that one (v1 is the original home screen)
#   sh v2.sh <TV_IP> remove          go back to v1, then uninstall v2 and v3
#
# install.sh and undo.sh are written for the original app's ID, so don't use them for v2 or v3.
set -e
cd "$(dirname "$0")"

TARGET="$1"; CMD="$2"; WHICH="$3"
[ -n "$TARGET" ] && [ -n "$CMD" ] || { sed -n '6,8p' "$0" | sed 's/^# *//' >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV. Is the TV on, with network debugging allowed?" >&2; exit 1; }

V1=com.ansub.tvhome/.MainActivity
V2=com.ansub.tvhome.v2/com.ansub.tvhome.MainActivity
V3=com.ansub.tvhome.v3/com.ansub.tvhome.MainActivity
# The dock's apps, in the order the original home screen showed them on 2026-10-23.
APPS=${APPS:-com.netflix.ninja,in.startv.hotstar,com.amazon.amazonvideo.livingroom,com.graymatrix.did,com.sonyliv,com.apple.atve.androidtv.appletv,com.google.android.youtube.tv,com.sand.aircasttv,com.xiaomi.mitv.smartshare,com.android.vending,com.jio.media.stb.ondemand}

sh_tv() { adb -s "$TV" shell "$@" | tr -d '\r'; }
current_home() { sh_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1; }

# Makes one home screen the default: the Home role (Android 10+) and the classic way, then checks it.
use() {
  pkg=${1%%/*}
  sh_tv cmd role add-role-holder --user 0 android.app.role.HOME "$pkg" >/dev/null 2>&1 || true
  sh_tv cmd package set-home-activity "$1" >/dev/null 2>&1 || true
  adb -s "$TV" shell input keyevent KEYCODE_HOME
  if [ "$(current_home)" = "$1" ]; then echo "Home now opens $pkg."
  else echo "Home still opens $(current_home). If the TV asks which home screen to use, pick the one you want and choose Always." >&2; exit 1; fi
}

case "$CMD" in
  install)
    case "$WHICH" in v2) ME=$V2 ;; v3) ME=$V3 ;; *) echo "Say which: install v2 or install v3" >&2; exit 1 ;; esac
    # The JDK and SDK set up inside this folder, unless JAVA_HOME and ANDROID_HOME are already set.
    [ -n "$JAVA_HOME" ] || [ ! -d .jdk17 ] || JAVA_HOME=$(echo "$PWD"/.jdk17/*/Contents/Home)
    [ -n "$ANDROID_HOME" ] || [ ! -d .android-sdk ] || ANDROID_HOME=$PWD/.android-sdk
    JAVA_HOME=$JAVA_HOME ANDROID_HOME=$ANDROID_HOME VARIANT=$WHICH sh build.sh
    pkg=${ME%%/*}
    fresh=y; sh_tv pm list packages "$pkg" | grep -Fqx "package:$pkg" && fresh=
    adb -s "$TV" install -r out/tvhome.apk
    # A first install gets the app list; an update keeps what was arranged on the TV. The stamp makes
    # it apply the list once, even if Android later replays this start.
    [ -z "$fresh" ] || sh_tv am start -n "$ME" --es stamp "$(date +%s)" --es apps "$APPS" >/dev/null
    use "$ME" ;;
  use)
    case "$WHICH" in v1) use "$V1" ;; v2) use "$V2" ;; v3) use "$V3" ;; *) echo "Say which: use v1, v2 or v3" >&2; exit 1 ;; esac ;;
  remove)
    use "$V1"
    for p in com.ansub.tvhome.v2 com.ansub.tvhome.v3; do adb -s "$TV" uninstall "$p" >/dev/null 2>&1 && echo "Removed $p" || true; done ;;
  *) echo "Unknown command: $CMD (install v2|v3, use v1|v2|v3, remove)" >&2; exit 1 ;;
esac
