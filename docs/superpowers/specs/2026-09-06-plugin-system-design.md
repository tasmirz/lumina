# Lumina Plugin System Design

Date: 2026-09-06
Status: Approved (grilled, Round 2 settled)
Goal: Keep core <15-20 MB, F-Droid compatible, 3 optional plugins.

## Decisions (settled)

- Delivery: Separate plugin APKs via explicit Intents + FileProvider. No Play Dynamic Features (incompatible with F-Droid).
- Discovery: In-app Plugins screen with `fdroid.app://details?id=<pkg>` deep-link + GitHub APK fallback + PackageManager auto-detect on resume.
- Gemma 2B: MediaPipe LLM Inference plugin, gated 6GB+ RAM + Android 12+ (API 31+) + WiFi-only 1.5GB `.task` download. Core keeps online Gemini Flash path untouched.
- PDF Reader: as-is bitmap viewer like other readers, using framework `PdfRenderer`. Themes + progress sync only. No TTS/orb/highlight parity in v1.
- Converter: Separate from reader. Host `ACTION_CONVERT_PDF` + Tesseract (FOSS) text+OCR from start, lang data downloaded on-demand by plugin. Output EPUB to `Download/Lumina/converted/`, re-import via existing `EpubParser`.
- Repo: Monorepo with Gradle modules `:app :plugin-pdf :plugin-convert :plugin-gemma`, F-Droid multi-APK builds (4 entries, same source).

## Architecture

Core (`:app`, `io.github.tasmirz.lumina`) adds `data/plugins/`:
- `PluginAction.kt` — `ACTION_VIEW_PDF`, `ACTION_CONVERT_PDF`, `ACTION_AI_CHAT`, `EXTRA_CONTRACT_V=1`
- `PluginManager.kt` — `isInstalled(pkg)`, `launch(intent)`, `getContractVersion()` via `queryIntentActivities()`, signer check (warn, not hard-fail for F-Droid rebuilds)
- `PluginsScreen.kt` — new `SettingsSubScreen.PLUGINS` entry in `AdvancedSettingsScreen.kt`

Contract v1 extras:
- PDF view: `EXTRA_FILE_URI (FileProvider)`, `EXTRA_THEME_FAMILY`, `EXTRA_THEME_VARIANT`, `EXTRA_FONT_SCALE` → returns `EXTRA_LAST_PAGE`, `EXTRA_PROGRESS`
- Convert: `EXTRA_FILE_URI` → returns `EXTRA_EPUB_URI`
- AI chat: `EXTRA_CONTEXT_TEXT (capped 8k tokens, spoiler-proof)`, `EXTRA_MODEL` → returns `EXTRA_REPLY_TEXT`

Security: `FLAG_GRANT_READ_URI_PERMISSION`, no new core permissions, no `INTERNET` addition.

## Size budget

- Core release: 13-15 MB (0 new deps, verified by CI)
- plugin-pdf: 2-3 MB (PdfRenderer only)
- plugin-convert-text+ocr: 8-20 MB (Tesseract + data download, not bundled)
- plugin-gemma code: ~30 MB + 1.5GB data opt-in

## Out of scope

- PDF TTS/orb/highlights, scanned-PDF perfect layout, Play/MLKit OCR, DexClassLoader in-app code loading.
