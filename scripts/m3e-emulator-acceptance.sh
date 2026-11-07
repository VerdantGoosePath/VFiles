#!/usr/bin/env bash
# Device acceptance for real M3E views. Emulator runner invokes this through bash:
# YAML script lines otherwise run in separate /bin/sh processes.
set -uo pipefail
mkdir -p m3e-screenshots

capture_images() {
  local label="$1"
  local image
  for image in browser-light browser-dark settings ftp; do
    if adb exec-out run-as fr.vroot.vfiles.fork cat "files/m3e-screenshots/${image}.png" > "m3e-screenshots/${image}-${label}.png"; then
      if [[ ! -s "m3e-screenshots/${image}-${label}.png" ]]; then
        rm -f "m3e-screenshots/${image}-${label}.png"
      fi
    else
      rm -f "m3e-screenshots/${image}-${label}.png"
    fi
  done
}

# Phone-sized display at default font scale; Android17 target SDK.
./gradlew --no-daemon :app:connectedDebugAndroidTest
phone_result=$?
capture_images phone
adb logcat -d -t 1500 -v threadtime > m3e-screenshots/emulator-phone.log || true

# Exercise layout and readable controls at >= 840dp and 200% font.
wide_result=0
if [[ "$phone_result" -eq 0 ]]; then
  adb shell wm size 840x1200
  adb shell wm density 160
  adb shell settings put system font_scale 2.0
  adb shell am force-stop fr.vroot.vfiles.fork
  ./gradlew --no-daemon :app:connectedDebugAndroidTest
  wide_result=$?
  capture_images wide-200
  adb logcat -d -t 1500 -v threadtime > m3e-screenshots/emulator-wide.log || true
  adb shell wm size reset || true
  adb shell wm density reset || true
  adb shell settings put system font_scale 1.0 || true
fi
if [[ "$phone_result" -ne 0 || "$wide_result" -ne 0 ]]; then
  echo "M3E device acceptance failed: phone=${phone_result}, wide=${wide_result}" >&2
  exit 1
fi
echo "M3E phone and 840dp/200%-font emulator acceptance passed"
