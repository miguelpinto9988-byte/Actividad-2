#!/usr/bin/env bash
set +e
gradle --no-daemon connectedDebugAndroidTest
result=$?
adb pull /sdcard/Download/ dist/screenshots/
adb logcat -d > dist/logcat.txt
exit "$result"
