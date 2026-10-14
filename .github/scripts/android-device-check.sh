#!/usr/bin/env bash
# One shell: retain the test exit status while collecting screenshots after failures.
set -u

device_test_status=0
./gradlew :app:connectedDebugAndroidTest --console=plain || device_test_status=$?
adb pull /sdcard/Pictures/StoxSimReview native-review || true
exit "$device_test_status"
