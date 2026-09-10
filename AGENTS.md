# AGENTS.md — Operational Principles & Guidelines for Lumina

This document defines the foundational principles, architectural invariants, performance rules, and verification protocols for AI coding agents and autonomous workflows operating in the **Lumina** repository.

Agents modifying this codebase MUST strictly adhere to the guidelines outlined below.

---

## 🏛️ Core Principles

### 1. Aesthetic & Craftsmanship Sovereignty
- **Typography-First Experience**: Lumina is not a generic utility app; it is an artisanal digital reading space. Visual polish, typography margins, micro-animations, and subtle haptic feedback are first-class requirements.
- **Strict Theme Token Adherence**:
  - Every UI element, border, icon, and surface must resolve through `MaterialTheme.colorScheme` (or reader-specific `ThemeMode` palette definitions).
  - **Never** introduce raw, hardcoded hex colors (e.g. `#FFFFFF`, `#121212`) into UI composables. They break readability across OLED Black, Warm Parchment, and Dark modes.
- **Zero Visual Clutter**: Reader chrome and floating elements must recede during reading. Edge clamps, drag-to-dismiss drop zones, and touch targets must feel fluid and natural.

### 2. Offline-First & Real Persistence
- **No Mock or Demo Data in Production Paths**:
  - Never reintroduce hardcoded dummy books or mock catalogs into production code.
  - The book library is strictly user-driven.
- **Physical EPUB Storage**:
  - Imported and downloaded EPUB files are saved to private storage: `context.filesDir/epubs/<id>.epub`.
- **Relational Integrity in SQLite**:
  - All metadata, reading positions (`currentChapter`, `currentProgress`), reader settings, and user bookmarks/highlights persist via `LuminaDatabaseHelper`.
  - Never allow UI state to drift from database state; all state modifications must flow through `BookRepository`.

### 3. Frame Budget & Zero-Jank Mandate (60 / 120 FPS)
- **Zero Heavy Computations in Composition**:
  - Never execute regex matching, heavy string splits (e.g. splitting chapters into sentences or paragraphs), or tokenization inside a Composable body or `remember` block during active scroll.
  - Paginated chapters and layout calculations MUST be retrieved from `io.github.tasmirz.lumina.util.PageCache`.
  - Formatted highlighted spans MUST use `AnnotatedTextCache`.
- **Pre-Index List Lookups**:
  - Never perform repeated linear searches (e.g., `bookmarks.filter { it.chapter == chapter }`) inside lazy list item rendering. Pre-group collections (e.g. `bookmarks.groupBy { it.chapter.trim().lowercase() }`) once when chapter state changes.
- **Compose Layout Performance**:
  - Always provide stable `key` and explicit `contentType` to `LazyColumn`, `LazyRow`, and `HorizontalPager`.
  - Use `beyondViewportPageCount = 1` on `HorizontalPager` to avoid blank frames during swipe gestures.

### 4. Asynchronous & Threading Discipline
- **Main Thread Purity**:
  - Never execute SQLite database queries, EPUB unzipping, XML parsing, or bitmap decoding on `Dispatchers.Main`.
  - Offload all disk and database interactions to `Dispatchers.IO` using coroutines:
    ```kotlin
    withContext(Dispatchers.IO) {
        dbHelper.updateReadingPosition(...)
    }
    ```
- **Debounced Updates & State Isolation**:
  - Debounce high-frequency reading progress updates (e.g. 800ms) before updating `_books.value` and SQLite to prevent database lock contention and UI recomposition storms.
  - Reader position state is streamed immediately through `_readingPosition` (`StateFlow<ReadingPosition>`), decoupled from full library updates.
- **Asynchronous & Lazy Chapter Loading**:
  - Chapter lists are retrieved and cached on-demand (`getChaptersForBook`, `getCachedChapters`, `prefetchChapters`).
  - Books displayed in library screens carry lightweight metadata; full chapter content is stored in normalized SQLite table `book_chapters` and cached via `SimpleLruCache` to keep initial startup sub-100ms.
  - When switching books or launching the reader, show an explicit loading indicator while chapters resolve asynchronously instead of presenting an empty "No Book Selected" state.

### 5. Architectural Invariants

#### A. Dual-Orb Architecture
The floating assistant orb has two distinct sizing configurations:
1. **Edge Dock Size (`OrbSize`)**: Controls the resting size of the docked orb on the screen margin (`NANO` [0.55x], `MINI` [0.70x], `COMPACT` [0.85x], `DEFAULT` [1.0x], `LARGE` [1.25x]). Default is **`NANO`**.
2. **Menu Palette Size (`OrbMenuSize`)**: Controls the expanded radial action wheel button scale (`COMPACT` [0.85x], `MEDIUM` [1.0x], `LARGE` [1.2x]). Default is **`MEDIUM`**.
- Radial menu actions are modeled via `OrbActionItem`. The default actions are:
  - `TTS_PLAY_PAUSE` (Audio narration)
  - `ADD_BOOKMARK` (Bookmark current section)
  - `THEME_MODE` (Quick toggle between Light / Dark themes)
  - `SETTINGS` (Open reading controls)
- Table of Contents (TOC) is intentionally omitted from the orb menu to keep the palette focused; TOC is accessed from reader top bars and sheets.

#### B. Reader Architecture & Modular UI Components
The reading screen is modularized into specialized components:
- `ReaderTopBar`: Minimalist header with book/chapter titles, navigation, and assistant triggers.
- `ReaderBottomDock`: Bottom navigation dock with chapter scrubbers, reading progress, and quick controls.
- `ReaderTtsDock`: Dedicated playback controls for Text-to-Speech narration.
- `ReaderAutoScrollPill`: Subtle floating control pill for hands-free continuous scrolling.
- `ReaderSelectionMenu`: In-text context toolbar for highlighting, dictionary lookups, note annotations, and copy actions.
- `InBookSearchDialog`: In-book full-text search supporting both plain substring and semantic search.
- `BookmarkDetailModal`: Bottom sheet modal for inspecting, updating notes/colors, and deleting saved bookmarks.

#### C. Progressive Pagination Precomputation
- Reader rendering prioritizes the active chapter immediately (<100ms) while queuing background precomputation for remaining spine chapters via `Dispatchers.Default`.
- Paginated results are cached in the SQLite `page_cache` table to guarantee instant resumption.

#### D. Jetpack Compose Compiler Constraints
- **Scope Integrity**: In `LazyListScope`, do NOT invoke `@Composable` functions (such as `remember(...)`) outside of an `item { ... }` or `items { ... }` block. Doing so causes Compose compiler errors.
- **State Hoisting**: Hoist mutable state to coordinators or `BookRepository`. Do not manage duplicated local state across disparate composable trees.

### 6. FOSS & F-Droid Compliance
- Keep the core application lean and self-contained (<15-20 MB release APK).
- Zero third-party proprietary tracking, ads, or closed-source analytical SDKs.
- Features requiring heavy weights (like offline Gemma 2B LLM or Tesseract OCR) are isolated into external plugin APKs via Intent contracts (see `docs/superpowers/specs/2026-09-06-plugin-system-design.md`).

---

## 🗺️ Codebase Map & Key References

| Path | Responsibility |
| :--- | :--- |
| `android/app/src/main/java/io/github/tasmirz/lumina/data/BookRepository.kt` | Central repository singleton managing `StateFlow` for books, settings, bookmarks, and caching |
| `android/app/src/main/java/io/github/tasmirz/lumina/data/db/LuminaDatabaseHelper.kt` | SQLite database manager (`lumina_reader.db`) for books, normalized chapters, page cache, lore, and settings |
| `android/app/src/main/java/io/github/tasmirz/lumina/data/EpubParser.kt` | Zero-dependency streaming XML/XHTML EPUB parser |
| `android/app/src/main/java/io/github/tasmirz/lumina/data/DictionaryService.kt` | Offline/online dictionary lookup service with in-memory caching |
| `android/app/src/main/java/io/github/tasmirz/lumina/data/AssistantService.kt` | Multi-provider AI assistant orchestration (Gemini & OpenAI-compatible) and character extraction |
| `android/app/src/main/java/io/github/tasmirz/lumina/util/PageCache.kt` | Line-budgeted pagination engine and LRU caching for pages & chapter layouts |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/reader/ReaderScreen.kt` | Core reader canvas (Continuous vertical scroll & Paged swipe modes) |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/reader/ReaderTopBar.kt` | Reader header bar with title and quick navigation |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/reader/ReaderBottomDock.kt` | Reader footer dock with chapter scrubber, progress indicator, and controls |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/reader/ReaderTtsDock.kt` | Floating Text-to-Speech audio narration dock |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/reader/ReaderAutoScrollPill.kt` | Quick floating controls for auto-scroll mode |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/reader/ReaderSelectionMenu.kt` | Highlight, note, dictionary, and copy action overlay menu |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/reader/InBookSearchDialog.kt` | In-book search modal dialog (plain and semantic matching) |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/reader/BookmarkDetailModal.kt` | Modal bottom sheet for viewing and editing bookmark notes/highlights |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/reader/ReaderTextAnnotator.kt` | Text layout formatting, drop caps, bookmarks, and search highlights |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/components/FloatingAssistantOrb.kt` | Draggable floating orb, edge-snapping, radial action menu, and speech/AI triggers |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/library/LibraryScreen.kt` | Book gallery, sorting, import, and online public catalogs |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/components/Sheets.kt` | Reading settings modal, Table of Contents, and Book Details sheets |
| `android/app/src/main/java/io/github/tasmirz/lumina/ui/settings/AdvancedSettingsScreen.kt` | Comprehensive configuration hub for themes, fonts, AI, TTS, gestures, and indexing |
| `android/app/src/main/java/io/github/tasmirz/lumina/model/Book.kt` | Domain models: `Book`, `Chapter`, `Bookmark`, `ReaderSettings`, `OrbSize`, `OrbMenuSize` |
| `justfile` | Automation command runner (`just build`, `just release`, `just test`, `just ss`, `just install`, `just run`, `just hot`, `just reload`) |

---

## 🚦 Agent Verification Protocol

Before completing any task or signaling readiness to the user, an agent MUST execute the following verification steps:

1. **Static Analysis & Compilation**:
   ```bash
   cd android && ./gradlew compileDebugKotlin
   ```
   Ensure there are zero compilation errors and no deprecation breaks.

2. **Automated Unit Tests**:
   ```bash
   cd android && ./gradlew testDebugUnitTest
   ```
   Or via the root command:
   ```bash
   just test
   ```
   All tests in `android/app/src/test/` must pass. If new behavior or schema changes were added, add or update corresponding unit tests.

3. **Database Schema Sanity**:
   - If adding columns to `LuminaDatabaseHelper`, increment `DATABASE_VERSION`.
   - Ensure an `onUpgrade` migration path handles existing user databases gracefully.

4. **Visual & UI Verification (When Device/Emulator is Connected)**:
   - Deploy: `just install && just run` (or `just release` for release APK)
   - Capture Screenshot: `just ss <feature_name>`
   - Inspect the captured screenshot in `debug/<feature_name>.png` to verify proper spacing, contrast, alignment, and lack of visual artifacts.

---

## 🚫 Anti-Patterns to Avoid

- ❌ **Do NOT use web or hybrid abstractions**: This is a pure native Kotlin + Jetpack Compose app. Do not suggest HTML, Tailwind, or WebView-based reading engines.
- ❌ **Do NOT read entire multi-megabyte EPUBs into memory at once**: Parse chapter contents on-demand from the spine and cache through `SimpleLruCache` / `book_chapters`.
- ❌ **Do NOT bypass `PageCache`**: Never compute line wrapping or character splits on each frame render.
- ❌ **Do NOT mutate state directly**: Always copy data classes and update state via `BookRepository`.
- ❌ **Do NOT ignore edge-to-edge window insets**: Always handle `WindowInsets.systemBars` or `WindowInsets.statusBars` properly so content does not collide with notches or navigation pills.

