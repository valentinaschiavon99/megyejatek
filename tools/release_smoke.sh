#!/usr/bin/env bash
# Installs the minified release APK on the running emulator, starts it and pokes it with random
# taps. Fails if the app crashes, which is how missing R8 keep rules usually show up.
set -euo pipefail
APK="${1:-apk/megyejatek.apk}"
PKG=io.github.valentinaschiavon99.megyejatek
if [ ! -f "$APK" ]; then
  echo "No release APK ($APK): skipping the release smoke test."
  exit 0
fi
adb uninstall "$PKG" >/dev/null 2>&1 || true # the debug build has a different signature
adb install -r "$APK"
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 6
adb shell monkey -p "$PKG" --pct-syskeys 0 --pct-appswitch 0 --throttle 150 -s 42 -v 400 >/dev/null || true
sleep 2
if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  echo "::error title=Release build crashed::see the stack trace below"
  adb logcat -d | grep -A 40 "FATAL EXCEPTION" | head -80
  exit 1
fi
echo "Release build started and survived 400 random events."
