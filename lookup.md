# Lumina — Codebase Index & Fast Lookup Reference (`lookup.md`)

> **Agent Notice**: Refer to this index before viewing or grepping large source files. This document details component boundaries, exact line locations, state models, database tables, and key architectural invariants.

---

## 🗺️ Master Component & File Index

| Component / Layer | Primary File | Approx Lines | Key Responsibilities |
| :--- | :--- | :--- | :--- |
| **Domain Models** | `android/app/src/main/java/io/github/tasmirz/lumina/model/Book.kt` | ~160 | `Book`, `Chapter`, `Bookmark`, `ReaderSettings`, `BookCharacter`, `BookLore`, `OrbSize`, `OrbMenuSize` |
| **Repository** | `android/app/src/main/java/io/github/tasmirz/lumina/data/BookRepository.kt` | ~450 | Singleton `StateFlow` store for books, settings, characters, lore, read-till positions. Asynchronous DB writes |
| **SQLite Database** | `android/app/src/main/java/io/github/tasmirz/lumina/data/db/LuminaDatabaseHelper.kt` | ~600 | Schema management, table CRUD, version migrations (Current: **v9**) |
| **Reader Canvas** | `android/app/src/main/java/io/github/tasmirz/lumina/ui/reader/ReaderScreen.kt` | ~3,500 | Core reader screen (Continuous & Paged), progress bar, bottom dock, in-book search, TTS, selection toolbar |
| **AI Assistant Service** | `android/app/src/main/java/io/github/tasmirz/lumina/data/AssistantService.kt` | ~1,400 | Gemini API client, incremental character & lore extraction, zero-token guard, anti-duplication, voice intents |
| **Character & Lore Guide**| `android/app/src/main/java/io/github/tasmirz/lumina/ui/components/CharacterGuideSheet.kt`| ~500 | Dramatis personae & world lore sheet, filter chips (`All`, `Characters`, `Lore`), spoiler shield, manual add |
| **Modals & Sheets** | `android/app/src/main/java/io/github/tasmirz/lumina/ui/components/Sheets.kt` | ~1,200 | Reading Settings, Table of Contents, Book Details, Appearance Sheet, Advanced AI settings |
| **Assistant Orb & Overlay**| `android/app/src/main/java/io/github/tasmirz/lumina/ui/reader/FloatingAssistantOrb.kt` | ~600 | Edge-docked floating orb (`OrbSize.NANO`), radial wheel menu (`OrbMenuSize.MEDIUM`), drag gestures, TTS status |
| **EPUB Streaming Parser**| `android/app/src/main/java/io/github/tasmirz/lumina/data/EpubParser.kt` | ~550 | Zero-dependency streaming XML/XHTML parser, spine & TOC resolution |
| **LRU Page Cache** | `android/app/src/main/java/io/github/tasmirz/lumina/util/PageCache.kt` | ~250 | LRU page layout cache and annotated string cache for 60/120 FPS render |
| **Library UI** | `android/app/src/main/java/io/github/tasmirz/lumina/ui/library/LibraryScreen.kt` | ~700 | Bookshelf grid, sorting, EPUB import flow, search, catalog download |

---

## 🔍 `ReaderScreen.kt` Line Section Map

Use this section map to target specific line ranges instead of viewing the entire 3,500-line file:

| Section | Line Range (Approx) | Description / Key Symbols |
| :--- | :--- | :--- |
| **Imports & Header** | 1 – 65 | Jetpack Compose, Material3, Foundation gestures, Coroutines, Repository imports |
| **Screen Signature & Props**| 70 – 120 | `ReaderScreen(bookId, repository, onBackToLibrary, ...)` |
| **Core State Declarations** | 350 – 460 | `showControls`, `selectedBookmarkForModal`, `activeChapterTitle`, `transientFontBadge`, `showReadTillFeedback`, `currentProgressPct`, `currentReadTillPct` |
| **TTS State & Auto-scroll** | 430 – 510 | `isTtsSpeaking`, `speakingChapterIdx`, `ttsSpeed`, `autoScrollJob`, `isAutoScrolling` |
| **Scroll / Paging Coordinators** | 520 – 720 | `LazyListState` (continuous), `PagerState` (paged), debounced reading position updates to DB |
| **Gesture Detectors (Tap/Drag)**| 730 – 950 | Reader area touch handling, single tap (toggle UI), edge tap (paging), selection handling |
| **Text Rendering & Pagination**| 960 – 1,400 | Page rendering loops, `PageCache` lookups, highlighted bookmark span injection |
| **Selection Toolbar & Modals** | 1,400 – 1,900 | Highlight colors, bookmark creation dialog, text copy, definition lookup |
| **In-Book Search Sheet** | 1,900 – 2,150 | Search query input, regex/plain search, match navigation arrows, result highlighting |
| **Bottom Progress & Nav Dock**| 2,230 – 2,420 | `Surface` dock (380.dp max), pure display progress rail, **long-press read-till trigger**, animated in-place feedback pill, expandable nav row |
| **TTS Controls Dock** | 2,420 – 2,600 | Play/pause button, speed stepper, rewind/forward sentence skip, voice state bars |
| **Floating Assistant Orb Integration**| 2,650 – 2,850 | `FloatingAssistantOrb` positioning, radial menu actions (`TTS`, `BOOKMARK`, `THEME`, `SETTINGS`) |
| **Gemini Assistant Overlay Sheet**| 2,850 – 3,200 | Transparent blurred overlay, chat bubbles with vanishing gradient, action chips, voice pulse waveform |
| **Lifecycle & Persistence** | 3,200 – 3,470 | `DisposableEffect`, saving progress on exit, TTS shutdown, cleanup |

---

## 🧠 `AssistantService.kt` Line Section Map

| Section | Line Range (Approx) | Description / Key Symbols |
| :--- | :--- | :--- |
| **Models & Enums** | 1 – 90 | `AiProvider`, `AssistantVoiceState`, `ExtractionResult(characters, lore)` |
| **API Endpoints & Clients** | 90 – 220 | Gemini v1beta / v1 API call implementations, JSON payload encoding, streaming/non-streaming |
| **System Prompts** | 220 – 420 | Assistant persona, reading comprehension, book context injection |
| **Incremental Character & Lore Extraction** | 420 – 780 | `extractCharactersAndLore(...)`: **Zero-token guard**, checkpoint checks, compact excerpt builder, structured JSON context |
| **Deduplication & Canonical Resolution** | 780 – 920 | `normalizeEntityName`, `isSameCharacter`, `isSameLore`, alias unification, summary refinement |
| **Legacy `extractCharacters` Wrapper** | 920 – 980 | Backward-compatibility shim forwarding to `extractCharactersAndLore` |
| **Chat & Question Answering** | 980 – 1,200 | Context-aware Q&A capped to spoiler threshold |
| **Voice & Speech-to-Text Parsing** | 1,200 – 1,450 | Audio recording buffer, Gemini Multimodal voice audio upload, intent extraction |

---

## 🗄️ Database Schema & Migrations (`LuminaDatabaseHelper.kt`)

Current Database Version: **`9`**

### Tables Overview
1. **`books`**:
   - Columns: `id (TEXT PK)`, `title (TEXT)`, `author (TEXT)`, `cover_path (TEXT)`, `file_path (TEXT)`, `total_chapters (INT)`, `current_chapter (INT)`, `current_page (INT)`, `progress (INT)`, `read_till_percent (INT)`, `character_checkpoint_chapter (INT)`, `character_checkpoint_page (INT)`, `read_time_left (TEXT)`, `last_read (INT)`.
2. **`bookmarks`**:
   - Columns: `id (INTEGER PK AUTOINCREMENT)`, `book_id (TEXT)`, `chapter (TEXT)`, `cfi (TEXT)`, `selected_text (TEXT)`, `note (TEXT)`, `color (TEXT)`, `created_at (INT)`.
3. **`reader_settings`**:
   - Columns: `id (INTEGER PK DEFAULT 1)`, `font_size (REAL)`, `font_family (TEXT)`, `line_spacing (REAL)`, `theme_mode (TEXT)`, `reading_mode (TEXT)`, `is_auto_scroll (INT)`, `auto_scroll_speed (REAL)`, `is_spoiler_shield (INT)`, `orb_size (TEXT)`, `orb_menu_size (TEXT)`, `gemini_api_key (TEXT)`, `ai_provider (TEXT)`, `ai_model (TEXT)`, `custom_endpoint (TEXT)`.
4. **`book_characters`**:
   - Columns: `id (INTEGER PK AUTOINCREMENT)`, `book_id (TEXT)`, `name (TEXT)`, `role (TEXT)`, `first_appearance_chapter (TEXT)`, `summary (TEXT)`, `key_events (TEXT)`, `is_spoiler (INT)`, `aliases (TEXT - JSON/CSV)`.
5. **`book_lore`** *(Added in v9)*:
   - Columns: `id (INTEGER PK AUTOINCREMENT)`, `book_id (TEXT)`, `title (TEXT)`, `category (TEXT)`, `first_appearance_chapter (TEXT)`, `description (TEXT)`, `key_facts (TEXT - JSON/CSV)`, `is_spoiler (INT)`, `created_at (INT)`.

---

## ⚡ Performance & Zero-Jank Rules

1. **LRU Caches**:
   - Paginated chapters and layout measurements MUST use `io.github.tasmirz.lumina.util.PageCache`.
   - Annotated text spans (with highlight colors) MUST use `AnnotatedTextCache`.
2. **Zero Composition Computations**:
   - No string splitting, chapter regex parsing, or heavy loops inside composable bodies.
3. **Database Threading**:
   - All database queries and writes MUST run on `Dispatchers.IO` via `BookRepository`.
   - Scroll updates are debounced before triggering SQLite writes.
4. **No Toasts in Reader UI**:
   - User interactions (such as setting read-till positions via progress rail long-press) must display sleek animated in-place feedback pills (`AnimatedVisibility`), **never** OS-level Toasts.
5. **AI Token Conservation**:
   - Characters and lore extraction checks checkpoints (`characterCheckpointChapter`, `characterCheckpointPage`).
   - If the user has not read past the checkpoint, extraction is a **0-token no-op**.
   - Incremental chapters are compacted to significant paragraphs, capped at 8,000 characters.

---

## 🛠️ Verification Commands

```bash
# Compile Kotlin (checks types, Compose compiler, and syntax)
cd android && ./gradlew compileDebugKotlin

# Run All Unit Tests
cd android && ./gradlew testDebugUnitTest

# Or using the repository justfile:
just test

# Deploy to connected Android device
just install && just run

# Capture UI verification screenshot
just ss <feature_name>
```
