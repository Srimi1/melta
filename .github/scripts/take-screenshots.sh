#!/usr/bin/env bash
# Drives a running emulator through Melta and saves real screenshots to ./screenshots.
# Used by .github/workflows/screenshots.yml. Relies on the debug-only launch extras in MainActivity.
set -euo pipefail

PKG=com.melta.app.debug
COMP="$PKG/com.melta.app.MainActivity"
OUT=screenshots
mkdir -p "$OUT"

shot() {
  sleep "${2:-5}"
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "captured $1 ($(stat -c %s "$OUT/$1.png") bytes)"
}

demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }

open_screen() {
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$COMP" "$@" > /dev/null
}

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell svc power stayon true

adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true

# Clean status bar: fixed clock, full signal, no notification icons.
adb shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 1230
demo network -e mobile show -e level 4 -e datatype 5g -e wifi show -e level 4
demo notifications -e visible false

# Pretend the phone is charging at 72%.
adb shell dumpsys battery set ac 1
adb shell dumpsys battery set status 2
adb shell dumpsys battery set level 72
demo battery -e level 72 -e plugged true

open_screen
shot 01-home

open_screen --es melta.screen battery
shot 02-battery-settings 7

open_screen --es melta.screen picker
shot 03-picker 7

open_screen --es melta.screen editor
shot 04-editor

# Turn the real overlay on, then go to the launcher to see it on top of everything.
open_screen --es melta.screen battery --ez melta.demo true
sleep 6
adb shell input keyevent KEYCODE_HOME
shot 05-bar-charging 6

# Unplug and drain to 12% for the low battery look.
adb shell dumpsys battery set ac 0
adb shell dumpsys battery set status 3
adb shell dumpsys battery set level 12
demo battery -e level 12 -e plugged false
shot 06-bar-low 6

# Without the overlay permission the bar moves to the notification panel.
adb shell dumpsys battery set ac 1
adb shell dumpsys battery set status 2
adb shell dumpsys battery set level 72
demo battery -e level 72 -e plugged true
demo notifications -e visible true
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW deny
open_screen --es melta.screen battery --ez melta.demo true
sleep 6
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell cmd statusbar expand-notifications
shot 07-notification-panel 5
adb shell cmd statusbar collapse

# Stage 2: the Shade screen, then a themed card in the real shade.
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
open_screen --es melta.screen shade
shot 08-shade-screen 7
open_screen --es melta.screen shade --es melta.theme pokedex --ez melta.demo true
sleep 6
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell cmd statusbar expand-notifications
shot 09-themed-card 5
adb shell cmd statusbar collapse

adb shell dumpsys battery reset
ls -la "$OUT"
