# Lumina EPUB Reader - Automation Tasks

set shell := ["bash", "-uc"]

# Show all available commands
default:
    @just --list

# Build debug APK
build:
    cd android && ./gradlew assembleDebug

# Build release APK
build-release:
    cd android && ./gradlew assembleRelease

# Run unit tests
test:
    cd android && ./gradlew test

# Install debug APK on the connected device
install:
    adb install -r android/app/build/outputs/apk/debug/app-debug.apk
    sleep 2

# Install release APK on the connected device
install-release:
    adb install -r android/app/build/outputs/apk/release/app-release.apk
    sleep 2

# Launch Lumina on the connected phone
run:
    adb shell am start -n io.github.tasmirz.lumina/.MainActivity

# Alias to launch the app
launch: run

# Install and launch release APK on the phone
run-release: install-release run
launch-release: run-release

# Stop the running application
stop:
    adb shell am force-stop io.github.tasmirz.lumina

# Restart the application on the phone
restart: stop run

# Capture a screenshot from the device (saved in debug/ directory)
# Supports multi-word names without quotes e.g. `just ss settings reading controls`
ss +args="screenshot":
    #!/usr/bin/env bash
    set -e
    mkdir -p debug
    raw="{{args}}"
    clean="${raw// /_}"
    if [[ "$clean" != *.png ]]; then
        clean="${clean}.png"
    fi
    adb shell screencap -p /sdcard/lumina_temp_ss.png
    adb pull /sdcard/lumina_temp_ss.png "debug/$clean"
    adb shell rm /sdcard/lumina_temp_ss.png
    echo "Screenshot saved to debug/$clean"

# Full screenshot alias
screenshot +args="screenshot": (ss args)

# Build, install and launch debug APK in one command
all: build install run

# Build, install and launch release APK in one command
release: build-release install-release run
all-release: release
all-rloease: release

# Fast incremental rebuild, install, and restart
hot: reload
hot-reload: reload

# Fast incremental rebuild, install, and restart via Gradle & ADB (no Python)
reload:
    cd android && ./gradlew assembleDebug --build-cache --parallel
    adb install -r -d android/app/build/outputs/apk/debug/app-debug.apk
    sleep 2
    adb shell am start -n io.github.tasmirz.lumina/.MainActivity -S

# Stream Logcat output for the Lumina app process
logs:
    adb logcat --pid="$$(adb shell pidof -s io.github.tasmirz.lumina)"

# Stream Lumina internal logs via ADB (clean filtered view)
log:
    adb logcat -v time -s Lumina:V LuminaPerf:V AndroidRuntime:E Choreographer:I

# Pull and inspect internal disk log file
pull-log:
    mkdir -p debug
    adb shell "cat /sdcard/Lumina/logs/lumina.log 2>/dev/null || cat /data/data/io.github.tasmirz.lumina/files/logs/lumina.log 2>/dev/null" > debug/lumina.log
    tail -n 60 debug/lumina.log

# Clear logcat and persistent log file on device
clear-log:
    adb logcat -c
    adb shell "rm -f /sdcard/Lumina/logs/lumina.log /data/data/io.github.tasmirz.lumina/files/logs/lumina.log"
    echo "Logs cleared."


