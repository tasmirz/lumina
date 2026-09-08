<div align="center">

<img src="assets/lumina_logo.svg" width="120" height="120" alt="Lumina Logo" /><br/>

# Lumina

**Minimalist, typography-first EPUB reader for Android crafted with Jetpack Compose & Material 3.**

[![Latest Release](https://img.shields.io/github/v/release/tasmirz/lumina?color=blue&label=Latest%20Release)](https://github.com/tasmirz/lumina/releases/latest)
[![Download APK](https://img.shields.io/badge/Download-Release%20APK%20(13%20MB)-brightgreen.svg?logo=android)](https://github.com/tasmirz/lumina/releases/latest)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_7.0%2B_(API_24%2B)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![F-Droid](https://img.shields.io/badge/F--Droid-Compliant-3DDC84.svg)](metadata/io.github.tasmirz.lumina.yml)

<p align="center">
  <a href="#-download--releases"><b>Download</b></a> •
  <a href="#-features">Features</a> •
  <a href="#-architecture--tech-stack">Architecture</a> •
  <a href="#-getting-started">Getting Started</a> •
  <a href="#-automation--tooling">Tooling</a> •
  <a href="#-contributing">Contributing</a> •
  <a href="#-license">License</a>
</p>

</div>

---

## 📥 Download & Releases

Get the latest production-ready release of Lumina for Android:

| Distribution Channel | Target / File | Status |
| :--- | :--- | :--- |
| **GitHub Releases** | [**Download `app-release.apk`**](https://github.com/tasmirz/lumina/releases/latest) | 🟢 **v1.0.0 Latest** (~13 MB) |
| **F-Droid Store** | *Submission pending* | 🟡 [Metadata Ready](metadata/io.github.tasmirz.lumina.yml) |
| **Active Development** | [`dev` branch](https://github.com/tasmirz/lumina/tree/dev) | 🧪 Bleeding-Edge Commits |
| **Stable Releases** | [`main` branch](https://github.com/tasmirz/lumina/tree/main) | 🏷️ Release Tags |

### Quick Installation:
1. Grab the latest signed APK: [**Download `app-release.apk`**](https://github.com/tasmirz/lumina/releases/latest).
2. Open the file on your device (Android 7.0+ / API 24+).
3. If prompted by Android, grant permission to *"Install from unknown sources"* for your browser or file manager.
4. Enjoy a fast, private, distraction-free reading experience!

---

## 🚀 What's New in Recent Updates

- **🎙️ Human-Like Edge Neural TTS**: High-definition, human-cadence text-to-speech engine powered by `EdgeTtsService`. Streams crystal-clear narration with curated natural voices (Jenny, Guy, Aria, Sonia, Christopher) over lightweight WebSockets without heavy third-party SDKs, with seamless automatic fallback to system TTS when offline.
- **🛡️ Bulletproof Audio Concurrency & Teardown**: Introduced monotonic audio session tokens (`currentAudioSessionId`) and managed coroutine jobs that completely eliminate dual-stream audio bugs during rapid paragraph skipping. Lifecycle-aware listeners (`ON_STOP`/`ON_DESTROY`) and BackHandler integration prevent lingering "zombie" playback after dismissing the player.
- **📖 Pure Distraction-Free Auto-Scroll**: When auto-scroll starts, all reader chrome (headers, footers, orb, docks) smoothly recedes for total visual immersion. The floating pill features minimal `11.sp` speed text (e.g. `1.0x`), and tapping the banner directly cycles through speeds (0.5x → 3.0x) without bulky buttons or toggles.
- **📚 Instant Pronunciation & Dictionary Service**: Look up any highlighted word instantly with word definitions, phonetic pronunciations, audio pronunciation previews, part-of-speech tags, and usage examples with in-memory caching and offline fallbacks.
- **⚙️ Centralized Reading & Audio Controls**: Dedicated "Reading Controls & Audio" hub in Advanced Settings for configuring TTS engines, voice selections, speech rate multipliers, and STT voice commands, keeping reader sheets streamlined and typography-focused.
- **🖼️ Hardened EPUB Parser & Cover Extraction**: Tolerant manifest resolution, URL decoding, and path normalization ensuring flawless image and cover extraction across complex standard EPUBs (Standard Ebooks, Project Gutenberg).
- **🏛️ Complete Architecture Documentation**: Comprehensive technical guides added in `docs/architecture/` covering the reader layout engine, LRU caching pipeline, SQLite FTS5 search, floating orb physics, and the design token system.

---

## 🌟 Overview

**Lumina** is an artisanal digital reading space designed for book lovers who value typography, fluid 60/120 FPS performance, and distraction-free immersion. 

Built from the ground up in modern **Kotlin** and **Jetpack Compose**, Lumina pairs a zero-dependency streaming EPUB engine with hardware-accelerated text rendering, an embedded SQLite FTS5 full-text search index, rich custom theming, and an optional spoiler-proof AI companion.

Lumina is strictly **100% free, open source, and offline-first**—zero ads, zero tracking, zero mandatory accounts, and zero proprietary analytics.

---

## ✨ Features

### 📖 Dual Reading Engines
- **Continuous Scroll Mode**: Seamless vertical scroll across full book spine with dynamic header/footer auto-hide.
- **Paged Horizontal Mode**: Physical-book style swipe pagination with page turn animations, notch/edge-inset safe margins, and touch-zone navigation.
- **Distraction-Free Auto-Scroll**: Hands-free continuous vertical scrolling that automatically recedes all chrome, featuring a minimal `11.sp` capsule pill with direct tap-to-cycle speed control (0.5x to 3.0x).
- **Interactive Scrubbing**: Bottom progress scrubber and chapter markers with instant persistent location saves.

### 🎨 Typography & Custom Themes
- **Curated Dual-Font Hierarchy**: Editorial Serif for literary immersion paired with a modern Sans-Serif for interface chrome.
- **Justified Book Layouts**: Fully justified text rendering with hyphenation and balanced paragraph margins.
- **Drop Caps**: Elegant opening paragraph typography on chapter beginnings.
- **Theme Builder & CSS Variables**: Built-in Light, Warm Parchment, Dark, and OLED Black modes. Create custom color palettes or import/export standard CSS (`:root`) theme variables and custom background wallpapers.
- **Granular Formatting**: Full control over font size, line spacing, letter spacing, paragraph spacing, and horizontal/vertical margins.

### 🔍 Lightning-Fast Offline Search (FTS5)
- **Embedded SQLite FTS5 Engine**: Instant full-text search across entire books and full library catalogs.
- **Scene & Dialogue Indexing**: Locate passages, quotes, or character names in milliseconds with zero network connectivity.
- **Context Snippets**: Keyword-matched results display surround context with instant jump-to-paragraph navigation.

### 🔮 Floating Assistant Orb & Action Palette
- **Adaptive Docking**: Non-intrusive floating orb with configurable edge dock sizing (`NANO`, `MINI`, `COMPACT`, `DEFAULT`, `LARGE`), edge snapping, and drag-to-dismiss drop zone.
- **Radial Action Wheel**: Single-tap quick wheel to toggle Text-to-Speech, create bookmarks/notes, switch themes, or open reading settings.
- **Voice Control**: Powered by Android's `SpeechRecognizer` for hands-free chapter navigation and playback commands.

### 🤖 Spoiler-Proof AI Story Assistant (Optional)
- **Contextual Book Intelligence**: Connect your own Gemini or OpenAI API key for character summaries, scene recaps, and literary explanations.
- **Anti-Spoiler Shield**: Context windows are strictly constrained to text up to your current reading position—guaranteeing zero future plot spoilers.
- **Zero-Telephony Isolation**: Completely disablable via a single master switch for 100% offline isolation.

### 🎧 Natural & Neural Text-to-Speech (TTS)
- **Edge Neural Voices**: High-fidelity, human-like streaming speech with natural inflection (Jenny, Guy, Aria, Sonia, Christopher) via `EdgeTtsService`.
- **System TTS Engine**: Built-in Android `TextToSpeech` engine fallback ensuring 100% offline narration reliability.
- **Concurrency & Lifecycle Guard**: Monotonic session tokens and hardware teardown prevent overlapping audio streams or background leaks.
- **Spoken Paragraph Highlighting**: Dynamic visual focus tracking on active paragraphs with automatic paragraph progression.

### ✍️ Precision Selection, Notes, Citations & Dictionary
- **Sub-Paragraph Selection**: Word-level and sentence-level drag handles for smooth, granular text selection.
- **Instant Word Dictionary**: Pronunciation phonetics, audio playback, part of speech tags, and contextual definitions with offline caching.
- **Multi-Color Highlighting**: Color-coded annotations (Gold, Rose, Sage) with personal marginalia notes.
- **Scholarly Citation Generator**: One-tap formatted bibliographic citations (author, book title, chapter title, and timestamp).

### 📚 Library Management & Curated Catalogs
- **Offline Library**: Import any DRM-free `.epub` file directly from local storage with robust cover extraction.
- **Curated Public Catalogs**: Search and download classic public-domain literature directly from **Standard Ebooks**, **Project Gutenberg**, **Internet Archive**, and **Open Library**.
- **Context Sheets**: Long-press any book card to inspect reading statistics, share EPUBs, reset progress, or manage storage.
- **Unified Backup & Restore**: Export and import your entire library database, reading positions, highlights, bookmarks, notes, and custom themes into a single JSON file.

---

## 🛠️ Architecture & Tech Stack

Lumina adheres to clean architecture principles, strict Unidirectional Data Flow (UDF), and modular design:

```
┌─────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI                    │
│   (LibraryScreen, ReaderScreen, FloatingAssistantOrb)   │
└───────────────────────────▲─────────────────────────────┘
                            │ StateFlow / User Actions
┌───────────────────────────┴─────────────────────────────┐
│                     BookRepository                      │
│   (Central coordinator for library, state & settings)   │
└─────────────▲─────────────────────────────▲─────────────┘
              │                             │
┌─────────────┴─────────────┐ ┌─────────────┴─────────────┐
│    LuminaDatabaseHelper   │ │  EpubParser & AudioEngine │
│  (SQLite + FTS5 Storage)  │ │ (Streaming XML / EdgeTTS) │
└───────────────────────────┘ └───────────────────────────┘
```

| Layer | Technologies / Implementation |
| :--- | :--- |
| **Language** | Kotlin 2.0+ with Kotlin Coroutines & `StateFlow` |
| **UI Framework** | Jetpack Compose + Material 3 (Design Tokens & Custom Palettes) |
| **EPUB Engine** | Zero-dependency streaming XML / XHTML ZIP parser (`EpubParser`) |
| **Search Engine** | Android embedded SQLite **FTS5** (Full-Text Search) virtual tables |
| **Caching Pipeline**| Two-tier LRU memory cache (`PageCache` & `AnnotatedTextCache`) |
| **Audio & TTS** | Edge Neural TTS (`EdgeTtsService`) + System `TextToSpeech` with session guards |
| **Dictionary** | In-memory cached dictionary service (`DictionaryService`) with offline fallbacks |
| **Storage / DB** | SQLite (`LuminaDatabaseHelper`) + Private app storage (`context.filesDir/epubs/`) |
| **Documentation**| Modular subsystem architecture guides in [`docs/architecture/`](docs/architecture/) |
| **Build System** | Gradle Version Catalogs (`libs.versions.toml`) + ProGuard/R8 13 MB release APK |

---

## 📁 Project Structure

```
.
├── android/
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/io/github/tasmirz/lumina/
│   │   │   │   │   ├── data/            # EPUB parser, repositories, database & online catalogs
│   │   │   │   │   │   └── db/          # SQLite schema, FTS5 virtual tables, migrations
│   │   │   │   │   ├── model/           # Book, Chapter, Bookmark, Theme & Settings data models
│   │   │   │   │   ├── theme/           # Color schemes, typography tokens, Material 3 theme
│   │   │   │   │   ├── ui/
│   │   │   │   │   │   ├── components/  # Sheets, TOC, dialogs, book cover decoders
│   │   │   │   │   │   ├── library/     # Library home grid, catalogs & book cards
│   │   │   │   │   │   ├── reader/      # Reader canvas, TTS engine, gestures, floating orb
│   │   │   │   │   │   └── settings/    # Theme builder, appearance & advanced settings
│   │   │   │   │   ├── util/            # PageCache, text pagination, citation generator
│   │   │   │   │   └── MainActivity.kt  # Root activity & navigation coordinator
│   │   │   │   ├── res/                 # Vector drawables, fonts, launcher icons, strings
│   │   │   │   └── AndroidManifest.xml
│   │   │   └── test/                    # Comprehensive JVM unit tests
│   │   └── build.gradle.kts
│   ├── gradle/
│   │   └── libs.versions.toml
│   └── settings.gradle.kts
├── metadata/                            # F-Droid submission metadata
├── docs/                                # Technical specifications and documentation
├── fastlane/                            # Fastlane store metadata and changelogs
├── justfile                             # Automation command runner
├── CONTRIBUTING.md                      # Developer contribution guidelines
├── LICENSE                              # GNU Affero General Public License v3.0
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- **JDK**: Java 17 or Java 21
- **Android SDK**: Compile SDK 36, Target SDK 36, Min SDK 24 (Android 7.0+)
- **Android Studio**: Ladybug / Meerkat or compatible CLI tools
- **Task Runner (Optional)**: [`just`](https://github.com/casey/just) for automation recipes

### Building from Source

1. **Clone the repository**:
   ```bash
   git clone https://github.com/tasmirz/lumina.git
   cd lumina
   ```

2. **Assemble Debug APK**:
   ```bash
   cd android && ./gradlew assembleDebug
   ```
   The generated APK will be located at:
   `android/app/build/outputs/apk/debug/app-debug.apk`

3. **Run Unit Tests**:
   ```bash
   cd android && ./gradlew testDebugUnitTest
   ```

4. **Install onto connected device**:
   ```bash
   cd android && ./gradlew installDebug
   ```

---

## ⚡ Automation & Tooling (`justfile`)

For streamlined development, a `justfile` is included at the repository root:

| Command | Description |
| :--- | :--- |
| `just build` | Assembles the debug APK |
| `just build-release` | Assembles the signed release APK |
| `just release` | Builds signed release APK, installs, and launches on connected device |
| `just test` | Runs all JVM unit tests |
| `just install` | Installs the latest debug APK onto a connected ADB device |
| `just run` / `just launch` | Launches Lumina on the active device |
| `just all` | Builds, installs, and launches in a single step |
| `just reload` | Fast incremental rebuild, install, and restart with state preserved |
| `just hot` | Activates Compose HotSwan port forwarding for live UI reloads |
| `just ss <name>` | Takes a high-resolution screenshot and saves it to `debug/<name>.png` |
| `just logs` | Attaches to `adb logcat` filtered to the Lumina process |

---

## 🔒 Privacy & Offline-First Guarantee

- **Zero Trackers**: No third-party analytics, crash beacons, or advertising SDKs.
- **Local Storage**: All EPUBs, reading statistics, annotations, bookmarks, and search indices remain on your device in private app storage.
- **No Mandatory Online Services**: All features (including dictionary lookups and full-text search) operate completely offline. AI features require an explicitly provided user API key and can be completely turned off.
- **Permission Transparency**:
  - `android.permission.INTERNET`: Required only for downloading public-domain books from open catalogs, dictionary queries, and user-initiated AI API requests.

---

## 🤝 Contributing

We welcome contributions from the open-source community! Please review our [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on code style, Jetpack Compose performance standards, commit formatting, and pull request procedures.

---

## 📜 License

Lumina is free software: you can redistribute it and/or modify it under the terms of the **GNU Affero General Public License** as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

See the [LICENSE](LICENSE) file for the full license text.
