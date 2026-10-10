#!/usr/bin/env bash
# Device gate for ProudVocab, run inside an Android 12 (API 31) emulator.
#
# Unlike the earlier smoke script this one has NO `|| true` escape hatches:
# every step either succeeds or the job fails, and every stage is checked for
#   * a FATAL EXCEPTION or ANR in our process (logcat),
#   * the process being alive,
#   * monkey reporting `// CRASH` / `// NOT RESPONDING`.
#
# The sequence reproduces what a real user with an existing install does:
#   1. install the PREVIOUS public release and use it (writes settings, the
#      last video/subtitle, the database),
#   2. install THIS build over it (an in-place upgrade),
#   3. launch, open video + subtitles, and monkey-test the upgraded app.
#
# Environment (set by .github/workflows/emulator-smoke.yml):
#   ABI               arm64-v8a | x86_64        the emulator's ABI
#   PV_LOCALE         "" (device default) | fa  UI language for the UI tests
#   PREVIOUS_RELEASE  e.g. v1.0.3               the release to upgrade from
#   GH_TOKEN          for `gh release download`
set -uo pipefail

PKG="com.proudvocab.android"
ABI="${ABI:?ABI is required}"
PV_LOCALE="${PV_LOCALE:-}"
PREVIOUS_RELEASE="${PREVIOUS_RELEASE:-v1.0.3}"
ROOT="$(pwd)"
RUN_TAG="${RUN_TAG:-$ABI}"
OUT="$ROOT/ci-artifacts/$RUN_TAG"
MEDIA_DIR="/sdcard/Download/pv-smoke"
APK_DIR="${APK_DIR:-apks}"   # built once by the `build` job and downloaded here
mkdir -p "$OUT/screens" "$OUT/previous"

FAILURES=0
fail() { FAILURES=$((FAILURES + 1)); echo "::error::$*"; echo "FAIL: $*" >> "$OUT/summary.txt"; }
pass() { echo "PASS: $*" >> "$OUT/summary.txt"; }
step() { echo; echo "================ $* ================"; }

: > "$OUT/summary.txt"

# ------------------------------------------------------------------ helpers
wait_for_boot() {
  adb wait-for-device
  local tries=0
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    tries=$((tries + 1))
    if [ "$tries" -gt 240 ]; then
      fail "emulator never finished booting"
      return 1
    fi
    sleep 2
  done
  adb shell input keyevent 82 >/dev/null 2>&1 || true   # dismiss keyguard
  return 0
}

app_pid() { adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' | tr -s ' ' | sed 's/ *$//'; }

# Returns 0 when logcat shows a crash or ANR for OUR package.
app_crashed_in() {
  local log="$1"
  if grep -A2 "FATAL EXCEPTION" "$log" | grep -q "Process: $PKG,"; then return 0; fi
  if grep -q "ANR in $PKG" "$log"; then return 0; fi
  return 1
}

# Checks one stage: logcat for a crash/ANR, the process is alive, and a screenshot.
check_stage() {
  local name="$1"
  sleep 4
  local log="$OUT/logcat-$name.txt"
  adb logcat -d -v threadtime > "$log" 2>&1
  adb shell screencap -p "/sdcard/pv-$name.png" >/dev/null 2>&1
  adb pull "/sdcard/pv-$name.png" "$OUT/screens/$name.png" >/dev/null 2>&1 || true
  if app_crashed_in "$log"; then
    fail "[$name] ProudVocab crashed or hung — see logcat-$name.txt"
    grep -B2 -A12 "FATAL EXCEPTION" "$log" | head -80 >&2 || true
    return 1
  fi
  if [ -z "$(app_pid)" ]; then
    fail "[$name] ProudVocab process is not running"
    return 1
  fi
  pass "[$name] alive, no crash, no ANR"
  return 0
}

launch_app() {
  adb logcat -c 2>/dev/null || true
  local out
  out="$(adb shell am start -W -n "$PKG/.MainActivity" 2>&1)"
  echo "$out"
  if ! echo "$out" | grep -q "Status: ok"; then
    fail "am start did not report Status: ok"
  fi
}

# Pushes a generated video and two subtitle files, then opens them through the
# same VIEW intents a file manager would send.
open_samples() {
  local tag="$1"
  adb shell mkdir -p "$MEDIA_DIR" >/dev/null 2>&1
  adb shell am start -a android.intent.action.VIEW -d "file://$MEDIA_DIR/sample.mp4" -t video/mp4 -n "$PKG/.MainActivity" 2>&1 | tee -a "$OUT/intents.log"
  sleep 8
  check_stage "$tag-video"
  adb shell am start -a android.intent.action.VIEW -d "file://$MEDIA_DIR/sample.srt" -t application/x-subrip -n "$PKG/.MainActivity" 2>&1 | tee -a "$OUT/intents.log"
  sleep 5
  adb shell am start -a android.intent.action.VIEW -d "file://$MEDIA_DIR/sample-fa.srt" -t application/x-subrip -n "$PKG/.MainActivity" 2>&1 | tee -a "$OUT/intents.log"
  sleep 5
  check_stage "$tag-subtitles"
}

# Monkey run; fails on any CRASH/NOT RESPONDING line that monkey itself prints.
run_monkey() {
  local tag="$1" events="$2"
  local log="$OUT/monkey-$tag.txt"
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER \
    --throttle 150 --pct-syskeys 0 -v "$events" > "$log" 2>&1
  local rc=$?
  tail -3 "$log"
  if grep -qE "// CRASH|// NOT RESPONDING" "$log"; then
    fail "[monkey-$tag] monkey reported a crash or ANR — see monkey-$tag.txt"
  fi
  if [ "$rc" -ne 0 ]; then
    fail "[monkey-$tag] monkey exited with $rc"
  fi
  check_stage "monkey-$tag"
}

installed_version_code() {
  adb shell dumpsys package "$PKG" 2>/dev/null | tr -d '\r' \
    | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n1
}

install_apk() {
  local apk="$1" tag="$2"
  local out
  out="$(adb install -r "$apk" 2>&1)"
  echo "$out"
  if ! echo "$out" | grep -q "Success"; then
    fail "[$tag] adb install failed for $(basename "$apk"): $(echo "$out" | tail -n1)"
    return 1
  fi
  pass "[$tag] installed $(basename "$apk")"
  return 0
}

# ----------------------------------------------------------- media fixtures
make_fixtures() {
  step "Generating sample media and subtitles"
  if ! command -v ffmpeg >/dev/null 2>&1; then
    fail "ffmpeg is not installed on this runner"
    return
  fi
  ffmpeg -y -loglevel error -f lavfi -i testsrc=duration=10:size=320x240:rate=15 \
    -f lavfi -i sine=frequency=440:duration=10 -pix_fmt yuv420p -shortest \
    "$OUT/sample.mp4" || fail "could not generate sample.mp4"
  printf '1\n00:00:01,000 --> 00:00:03,000\nHello world, this is a beautiful day\n\n2\n00:00:04,000 --> 00:00:06,500\nThe quick brown fox jumps over the lazy dog\n\n3\n00:00:06,800 --> 00:00:08,000\nThank you very much\n' > "$OUT/sample.srt"
  # Persian subtitles encoded the way most Persian subtitle files are
  # (windows-1256). Only characters that code page can represent are used.
  python3 -c "open('$OUT/sample-fa.srt','wb').write('1\n00:00:01,000 --> 00:00:03,000\nسلام بر شما\n\n2\n00:00:04,000 --> 00:00:06,500\nممنون از شما\n'.encode('cp1256'))" \
    || fail "could not encode the Persian subtitle"
  adb shell mkdir -p "$MEDIA_DIR" >/dev/null 2>&1
  adb push "$OUT/sample.mp4" "$MEDIA_DIR/sample.mp4" >/dev/null || fail "push sample.mp4"
  adb push "$OUT/sample.srt" "$MEDIA_DIR/sample.srt" >/dev/null || fail "push sample.srt"
  adb push "$OUT/sample-fa.srt" "$MEDIA_DIR/sample-fa.srt" >/dev/null || fail "push sample-fa.srt"
}

# ------------------------------------------------------------ 1. device tests
# Installs the pre-built debug app and its instrumentation APK, then runs them
# with `am instrument`. No Gradle here: the arm64 runner cannot run AGP's aapt2.
run_device_tests() {
  step "1/4 instrumented tests (UI language: '${PV_LOCALE:-device default}')"
  local app_apk="$ROOT/$APK_DIR/app-debug.apk"
  local test_apk="$ROOT/$APK_DIR/app-debug-androidTest.apk"
  for f in "$app_apk" "$test_apk"; do
    [ -f "$f" ] || { fail "missing $f"; return 1; }
  done
  adb uninstall "$PKG.debug" >/dev/null 2>&1 || true
  adb uninstall "$PKG.debug.test" >/dev/null 2>&1 || true
  install_apk "$app_apk" "tests-app" || return 1
  install_apk "$test_apk" "tests-apk" || return 1

  local component
  component="$(adb shell pm list instrumentation 2>/dev/null | tr -d '\r' \
    | sed -n 's/^instrumentation:\([^ ]*\) .*/\1/p' | grep "^$PKG" | head -n1)"
  if [ -z "$component" ]; then
    fail "no instrumentation for $PKG.debug was registered"
    return 1
  fi
  echo "instrumentation: $component"

  local log="$OUT/instrumented-tests.txt"
  adb logcat -c 2>/dev/null || true
  adb shell "am instrument -w -e pvLocale '$PV_LOCALE' $component" 2>&1 | tr -d '\r' > "$log"
  adb logcat -d -v threadtime > "$OUT/logcat-tests.txt" 2>&1 || true
  tail -n 30 "$log"

  if grep -q "^OK (" "$log" && ! grep -q "FAILURES!!!\|INSTRUMENTATION_FAILED\|Process crashed" "$log"; then
    pass "instrumented tests passed (${PV_LOCALE:-device default} UI): $(grep '^OK (' "$log" | head -n1)"
  else
    fail "instrumented tests failed (${PV_LOCALE:-device default} UI) — see instrumented-tests.txt and logcat-tests.txt"
    # Surface the failing lines in the check annotations as well, so the
    # cause is visible without downloading the artifact.
    grep -E "FAILURES|Tests run|Failures:|INSTRUMENTATION_FAILED|Process crashed|Error|Exception|at com\.proudvocab|Failed|expected" "$log" \
      | head -n 40 | cut -c1-400 | while IFS= read -r line; do echo "::error title=instrumented-tests::$line"; done
    grep -E "FATAL EXCEPTION|Process: com\.proudvocab" -A4 "$OUT/logcat-tests.txt" 2>/dev/null \
      | head -n 30 | cut -c1-400 | while IFS= read -r line; do echo "::error title=logcat::$line"; done
    return 1
  fi
}

# -------------------------------------------------- 2. previous release first
run_previous_release() {
  step "2/4 install and use the previous public release ($PREVIOUS_RELEASE)"
  local prev_apk
  prev_apk="$(gh release download "$PREVIOUS_RELEASE" --pattern "*-$ABI.apk" \
    --dir "$OUT/previous" --clobber >/dev/null 2>&1; ls "$OUT"/previous/*.apk 2>/dev/null | head -n1)"
  if [ -z "$prev_apk" ]; then
    fail "could not download $PREVIOUS_RELEASE for $ABI from GitHub"
    return 1
  fi
  echo "previous APK: $prev_apk"
  adb uninstall "$PKG" >/dev/null 2>&1 || true
  install_apk "$prev_apk" "previous" || return 1
  PREV_CODE="$(installed_version_code)"
  echo "previous versionCode: ${PREV_CODE:-?}"
  launch_app
  check_stage "previous-launch"
  open_samples "previous"
  run_monkey "previous" 300
}

# ------------------------------------------------------- 3. upgrade in place
run_upgrade() {
  step "3/4 upgrade this build over the previous install"
  local new_apk="$ROOT/$APK_DIR/app-$ABI-release.apk"
  if [ ! -f "$new_apk" ]; then
    fail "release APK missing: $new_apk"
    return 1
  fi
  install_apk "$new_apk" "upgrade" || return 1
  NEW_CODE="$(installed_version_code)"
  echo "new versionCode: ${NEW_CODE:-?} (previous: ${PREV_CODE:-?})"
  if [ -n "${PREV_CODE:-}" ] && [ -n "${NEW_CODE:-}" ] && [ "$NEW_CODE" -le "$PREV_CODE" ]; then
    fail "upgrade did not change versionCode ($PREV_CODE -> $NEW_CODE)"
  fi
  launch_app
  check_stage "upgraded-launch"
  open_samples "upgraded"
}

# ------------------------------------------------------------- 4. monkey
run_final_monkey() {
  step "4/4 monkey on the upgraded app"
  run_monkey "upgraded" 800
  adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adb logcat -d -v threadtime > "$OUT/logcat-final.txt" 2>&1 || true
}

# ------------------------------------------------------------------- main
wait_for_boot || true
make_fixtures
run_device_tests
run_previous_release || true
run_upgrade || true
run_final_monkey

echo
echo "================ summary ================"
cat "$OUT/summary.txt"
if [ "$FAILURES" -gt 0 ]; then
  echo "::error::$FAILURES device check(s) failed for $ABI"
  exit 1
fi
echo "All device checks passed for $ABI."
