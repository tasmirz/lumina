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

# Install release APK on the connected device
install-release:
    adb install -r android/app/build/outputs/apk/release/app-release.apk

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

# Initialize ADB port forwarding for Compose HotSwan (port 8600)
hotswan:
    adb forward tcp:8600 tcp:8600
    @echo "🔥 Compose HotSwan port forwarding active (tcp:8600 -> tcp:8600)"
    @echo "Instant Compose hot reload enabled on device without app restarts"

# Hot reload: incremental build, install, launch, and activate Compose HotSwan
hot: reload hotswan

# Hot reload alias
hot-reload: hot

# Fast incremental rebuild, install, and restart via Gradle & ADB (no Python)
reload:
    cd android && ./gradlew assembleDebug --build-cache --parallel
    adb install -r -d android/app/build/outputs/apk/debug/app-debug.apk
    adb shell am start -n io.github.tasmirz.lumina/.MainActivity -S

# Stream Logcat output for the Lumina app process
logs:
    adb logcat --pid="$$(adb shell pidof -s io.github.tasmirz.lumina)"


