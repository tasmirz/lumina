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

# Launch Lumina on the connected phone
run:
    adb shell am start -n org.protidhoni.lumina/.MainActivity

# Alias to launch the app
launch: run

# Stop the running application
stop:
    adb shell am force-stop org.protidhoni.lumina

# Restart the application on the phone
restart: stop run

# Capture a screenshot from the device (saved in debug/ directory)
ss name="screenshot.png":
    @mkdir -p debug
    adb shell screencap -p /sdcard/lumina_temp_ss.png
    adb pull /sdcard/lumina_temp_ss.png debug/{{name}}
    adb shell rm /sdcard/lumina_temp_ss.png
    @echo "Screenshot saved to debug/{{name}}"

# Full screenshot alias
screenshot name="screenshot.png": (ss name)

# Build, install and launch in one command
all: build install run

# Stream Logcat output for the Lumina app process
logs:
    adb logcat --pid="$$(adb shell pidof -s org.protidhoni.lumina)"
