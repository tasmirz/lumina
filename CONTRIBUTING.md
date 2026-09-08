# Contributing to Lumina

Thank you for your interest in contributing to **Lumina**! Lumina is an open-source, minimalist, typography-focused Android EPUB reader crafted with Jetpack Compose, Material 3, and a zero-bloat offline-first architecture.

We hold our engineering and design to high standards: buttery-smooth 60/120 FPS rendering, distraction-free reading typography, and privacy-first local storage. This guide will help you set up your development environment, understand our design philosophy, and submit high-quality contributions.

---

## 🧭 Table of Contents

1. [Code of Conduct & Philosophy](#-code-of-conduct--philosophy)
2. [Development Setup](#-development-setup)
3. [Automation & Tooling (`justfile`)](#-automation--tooling-justfile)
4. [Architecture Overview](#-architecture-overview)
5. [Coding & Design Standards](#-coding--design-standards)
   - [Jetpack Compose & UI Performance](#jetpack-compose--ui-performance)
   - [Theming & Design Tokens](#theming--design-tokens)
   - [Threading & Storage](#threading--storage)
   - [Kotlin Conventions](#kotlin-conventions)
6. [Testing Standards](#-testing-standards)
7. [Submitting a Pull Request](#-submitting-a-pull-request)

---

## 🌟 Code of Conduct & Philosophy

Lumina is built upon four core principles:

1. **Distraction-Free Craftsmanship**: The reader exists to serve the prose. UI chrome must recede, typography must feel editorial and justified, and animations must be subtle and purposeful.
2. **Offline-First & Privacy Sovereign**: Books, annotations, reading progress, and settings reside on the user's device. No trackers, no mandatory accounts, and no unsolicited telemetry.
3. **Buttery Performance**: Frame drops break immersion. Large EPUBs with thousands of paragraphs must scroll and paginate smoothly without stutter or memory spikes.
4. **FOSS & Clean Modular Design**: We strive for F-Droid compatibility, lean APK sizes (<15-20 MB core), and clean separation of concerns.

Please be kind, constructive, and respectful in all discussions, issues, and pull request reviews.

---

## 🛠️ Development Setup

### Prerequisites

- **JDK**: Java 17 or Java 21 (configured in your `JAVA_HOME`).
- **Android SDK**:
  - Compile SDK: `36`
  - Target SDK: `36`
  - Minimum SDK: `24` (Android 7.0+)
- **Android Studio**: Ladybug / Meerkat or command-line SDK tools.
- **Task Runner (Recommended)**: [`just`](https://github.com/casey/just) for automation recipes.
- **ADB**: Enabled with an Android device or emulator running.

### Cloning & Initial Verification

```bash
git clone https://github.com/tasmirz/lumina.git
cd lumina

# Run automated tests to verify your environment
just test
# or directly via Gradle:
cd android && ./gradlew testDebugUnitTest
```

---

## ⚡ Automation & Tooling (`justfile`)

We provide a comprehensive `justfile` in the project root to streamline development workflows:

| Command | Description |
| :--- | :--- |
| `just build` | Assembles the debug APK (`app/build/outputs/apk/debug/app-debug.apk`) |
| `just build-release` | Assembles the release APK |
| `just test` | Executes local JVM unit tests (`testDebugUnitTest`) |
| `just install` | Installs the latest debug APK onto a connected ADB device |
| `just run` / `just launch` | Starts `io.github.tasmirz.lumina/.MainActivity` on the connected device |
| `just stop` | Force-stops the Lumina application process |
| `just restart` | Stops and restarts the application on the device |
| `just all` | Builds, installs, and launches in one sequence |
| `just reload` | Fast incremental rebuild, installs, and relaunches activity with state preserved |
| `just hot` / `just hotswan` | Forwards port 8600 for Compose HotSwan (skydoves) for instant on-device UI updates without app restarts |
| `just ss <name>` | Takes a screenshot from the connected device and pulls it into `debug/<name>.png` |
| `just logs` | Attaches to `adb logcat` filtered specifically to the Lumina process PID |

---

## 🏛️ Architecture Overview

Lumina follows a clean Unidirectional Data Flow (UDF) pattern:

```
┌─────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI                    │
│   (LibraryScreen, ReaderScreen, FloatingAssistantOrb)   │
└───────────────────────────▲─────────────────────────────┘
                            │ StateFlow / Actions
┌───────────────────────────┴─────────────────────────────┐
│                     BookRepository                      │
│   (Single source of truth for books, state & settings)  │
└─────────────▲─────────────────────────────▲─────────────┘
              │                             │
┌─────────────┴─────────────┐ ┌─────────────┴─────────────┐
│    LuminaDatabaseHelper   │ │     PageCache & Caching   │
│ (SQLite: Books/Bookmarks) │ │  (LRU Pagination / Spans) │
└─────────────▲─────────────┘ └───────────────────────────┘
              │
┌─────────────┴─────────────┐
│  context.filesDir/epubs/  │
│  (Persistent EPUB files)  │
└───────────────────────────┘
```

### Key Components

- **`io.github.tasmirz.lumina.data.BookRepository`**: Central coordinator exposing `StateFlow<List<Book>>`, `StateFlow<ReaderSettings>`, and `StateFlow<List<Bookmark>>`.
- **`io.github.tasmirz.lumina.data.db.LuminaDatabaseHelper`**: SQLite helper managing `TABLE_BOOKS`, `TABLE_SETTINGS`, and `TABLE_BOOKMARKS`.
- **`io.github.tasmirz.lumina.data.EpubParser`**: Lightweight, streaming XML/XHTML parser extracting metadata, covers, chapters, and spine without third-party dependencies.
- **`io.github.tasmirz.lumina.util.PageCache`**: Thread-safe LRU cache caching paginated chapter pages and formatted annotated strings to eliminate scroll and pagination hitching.
- **`io.github.tasmirz.lumina.ui.reader.FloatingAssistantOrb`**: Draggable, edge-docking AI and quick-action overlay supporting dual sizing (`OrbSize` for dock, `OrbMenuSize` for radial palette).

---

## 🎨 Coding & Design Standards

### Jetpack Compose & UI Performance

1. **Zero Heavy Work in Composition**:
   - Never perform string regex splitting, complex sentence tokenization, or database queries inside a composable function body or `remember` during scroll.
   - Use `PageCache.getOrCompute(...)` for pagination and `AnnotatedTextCache` for highlights.
2. **Stable Keys & ContentTypes**:
   - Always supply stable keys and explicit `contentType` to `LazyColumn`, `LazyRow`, and `HorizontalPager`:
     ```kotlin
     items(
         items = chapters,
         key = { it.href },
         contentType = { "toc_item" }
     ) { chapter ->
         TocRow(chapter)
     }
     ```
3. **No Composable Invocations in `LazyListScope` Iterators**:
   - Do not call `@Composable` functions (e.g. `remember(...)`) inside the iteration logic of `LazyListScope`. Composable invocations must reside strictly inside `item { ... }` or `items { ... }`.
4. **Beyond Viewport Paging**:
   - Always configure `HorizontalPager` with `beyondViewportPageCount = 1` to preload adjacent pages smoothly without pop-in during swipe gestures.

### Theming & Design Tokens

- **Dynamic & Semantic Colors**:
  - Every UI element must resolve colors from `MaterialTheme.colorScheme` (e.g., `colorScheme.surface`, `colorScheme.primary`, `colorScheme.onSurfaceVariant`).
  - Do NOT hardcode arbitrary hex colors in UI composables. The app supports Light, Dark, OLED Black, and Warm Parchment paper modes; hardcoded colors break contrast in these modes.
- **Two-Font Hierarchy**:
  - **Editorial Serif** for prose reading text (`serif` / `Playfair Display` styling).
  - **Modern Sans** for all UI controls, headers, buttons, and navigation sheets.

### Threading & Storage

- **Never Block `Dispatchers.Main`**:
  - All disk I/O (SQLite queries, EPUB unzipping, reading files from `filesDir/epubs/`) must be dispatched to `Dispatchers.IO`.
- **State Persistence**:
  - Reading progress, font size, theme mode, and orb settings must immediately persist to SQLite via `BookRepository` and notify listeners via `StateFlow`.
  - Guard against no-op updates to avoid redundant SQLite writes.

### Kotlin Conventions

- Maintain idiomatic Kotlin: use `val` by default, data classes for state modeling, and immutable collections (`List`, `Map`, `Set`).
- Explicitly name boolean parameters in function calls when the meaning is not obvious from context (e.g., `updateReadingPosition(bookId, chap, prog, isCompleted = true)`).
- Document complex algorithms, EPUB schema workarounds, and concurrency assumptions.

---

## 🧪 Testing Standards

All business logic, database migrations, parser routines, and view-model operations must have unit test coverage.

### Running Tests

```bash
# Via just
just test

# Via Gradle
cd android && ./gradlew testDebugUnitTest
```

### Writing New Tests

- Unit tests reside under `android/app/src/test/java/io/github/tasmirz/lumina/`.
- Ensure tests verify:
  1. Parsing edge cases (malformed NCX, missing cover image, nested nav items).
  2. Data persistence across database upgrades.
  3. LRU eviction in `PageCache`.
  4. Model serialization and action item invariants (e.g., `OrbActionItem`).

---

## 🚀 Submitting a Pull Request

Lumina uses `dev` as its primary integration and development branch, while `main` tracks tagged stable releases.

1. **Create a Feature Branch (off `dev`)**:
   ```bash
   git checkout dev
   git pull origin dev
   git checkout -b feature/my-new-feature
   # or for bug fixes:
   git checkout -b fix/toc-scroll-jump
   ```

2. **Commit Messages**:
   We follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat: add dual-speed TTS control`
   - `fix: resolve page cache eviction on font scale change`
   - `perf: optimize TOC lazy list recomposition`
   - `docs: update plugin architecture specification`

3. **Verify Locally**:
   Before opening a PR, ensure all checks pass:
   ```bash
   cd android
   ./gradlew compileDebugKotlin
   ./gradlew testDebugUnitTest
   ```

4. **Visual Verification**:
   If your change affects UI or layouts, take a screenshot via `just ss <name>` and attach it to your PR description.

5. **Submit PR**:
   - Provide a concise summary of the change.
   - Link related issue(s).
   - Confirm tests pass and no regression in scrolling or memory consumption.

Thank you for helping make Lumina the best reading experience on Android! 📚✨
