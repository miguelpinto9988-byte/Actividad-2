#!/usr/bin/env bash
set +e
gradle --no-daemon connectedDebugAndroidTest
result=$?
adb pull /sdcard/Android/data/com.hunter.training/files/screenshots/ dist/screenshots/
adb logcat -d > dist/logcat.txt
exit "$result"
