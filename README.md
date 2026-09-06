# Lumina

> **Minimalist, typography-focused Android EPUB reader built with Jetpack Compose & Material 3.**

Lumina is designed for readers who value aesthetics, distraction-free typography, and a seamless reading experience. It features zero-clutter reading canvases, continuous vertical scrolling and paginated swipe modes, a native Text-to-Speech (TTS) floating reader, justified typography, instant dictionary lookups, quote citations, and a robust offline-first EPUB parsing engine with full cover and inline illustration support.

---

## ✨ Features

- **📖 Dual Reading Engines**:
  - **Continuous Scroll Mode**: Smooth vertical scroll across all chapters with dynamic header/footer auto-hide.
  - **Paged Mode**: Horizontal swipe pagination with touch navigation zones and page transitions.

- **🎧 Text-to-Speech (TTS) Floating Reader**:
  - Native Android TTS engine with auto-advancing paragraph narration.
  - Interactive, draggable/collapsible floating player pill with Play, Pause, Stop, and Skip Previous/Next controls.
  - Active spoken paragraph highlighting.
  - Dismissible ("drop-off") floating action button that can be re-summoned from the top bar at any time.

- **🎨 Typography & Aesthetics**:
  - **Justified Text Formatting**: Books format with clean, justified margins replicating physical book layouts.
  - **Drop Caps**: Elegant opening paragraph typography for every section.
  - **Curated Typefaces**: Focused two-font architecture (Editorial Serif for prose, Modern Sans for UI controls).
  - **Adjustable Display**: Dynamic font size scaling and line-height tuning.
  - **Themes**: Warm Paper (Parchment), Pure White, and OLED Night mode.

- **📑 Table of Contents (TOC) & Navigation**:
  - Comprehensive EPUB 2 (`toc.ncx`) and EPUB 3 (`nav.xhtml`) navigation extraction.
  - Dedicated Table of Contents modal sheet with reading progress and instant chapter jumps.

- **🖼️ Cover & Inline Image Support**:
  - Multi-tier cover extraction (EPUB 3 `cover-image`, OPF metadata, `<guide>` references, cover XHTML pages, and SVG images).
  - Inline chapter illustrations rendered with asynchronous decoding and out-of-memory safe downsampling.

- **🌐 Curated Public Catalogs & EPUB Import**:
  - Direct EPUB import from device storage.
  - Search and download classic public-domain literature from **Standard Ebooks**, **Project Gutenberg**, **Internet Archive**, and **Open Library**.

- **✍️ Highlights, Citations & Dictionary**:
  - Multi-color quote highlighting (Gold, Rose, Sage).
  - Formatted scholarly citation generator (author, book title, chapter, timestamp).
  - Instant word definition lookup with phonetic pronunciations and offline fallback.

---

## 🛠️ Architecture & Tech Stack

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose with Material 3
- **Design System**: Strict two-font hierarchy, fluid animations, custom gesture listeners, edge-to-edge support
- **State Management**: Kotlin Coroutines & `StateFlow`
- **Audio/Speech**: Android `TextToSpeech` API with `UtteranceProgressListener`
- **EPUB Engine**: Custom zero-dependency streaming zip/XML/XHTML parser (`EpubParser`)
- **Image Pipeline**: Memory-efficient asynchronous bitmap decoder with LRU caching and safe bounds downsampling
- **Build System**: Gradle Version Catalogs (`libs.versions.toml`)

---

## 📁 Project Structure

```
.
├── android/
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/org/protidhoni/lumina/
│   │   │   │   │   ├── data/            # EPUB parser, repositories, dictionary & online services
│   │   │   │   │   ├── model/           # Book, Chapter, Bookmark, and Theme data models
│   │   │   │   │   ├── theme/           # Color schemes, typography tokens, Material 3 theme
│   │   │   │   │   ├── ui/
│   │   │   │   │   │   ├── components/  # Sheets, TOC, BookCoverImage, AsyncImageBitmap
│   │   │   │   │   │   ├── library/     # Library home grid & book cards
│   │   │   │   │   │   └── reader/      # Reader screen, TTS widget, gestures, header & dock
│   │   │   │   │   └── MainActivity.kt  # Root activity & navigation coordinator
│   │   │   │   ├── res/                 # App launcher icons, strings, styles
│   │   │   │   └── AndroidManifest.xml
│   │   │   └── test/                    # Unit tests for EPUB parser and citations
│   │   └── build.gradle.kts
│   ├── gradle/
│   │   └── libs.versions.toml
│   └── settings.gradle.kts
├── stich-design/                        # Web design prototypes & interactive mockups
├── .gitignore
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- **JDK**: Java 17 or higher
- **Android SDK**: Compile SDK 36, Target SDK 36, Min SDK 24 (Android 7.0+)
- **Android Studio**: Ladybug / Meerkat or compatible command-line tools

### Building & Running

1. **Clone the repository**:
   ```bash
   git clone https://github.com/tasmirz/lumina.git
   cd lumina/android
   ```

2. **Assemble Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   The built APK will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`

3. **Run Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

4. **Install onto a connected device or emulator**:
   ```bash
   ./gradlew installDebug
   ```

---

## 📄 Application Metadata

- **Application ID**: `org.protidhoni.lumina`
- **Target Platform**: Android (Phone & Tablet)
- **Permissions**:
  - `android.permission.INTERNET` (for dictionary queries, online catalog downloads, and cover thumbnails)

---

## 📜 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
