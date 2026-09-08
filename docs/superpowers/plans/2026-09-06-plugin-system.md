# Plugin System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add F-Droid-compatible plugin support to Lumina keeping core <20 MB with PDF reader, PDF-to-EPUB (Tesseract), and Gemma 2B offline as separate APKs.

**Architecture:** Core discovers plugins via PackageManager Intents (contract v1). Monorepo Gradle modules `:app :plugin-pdf :plugin-convert :plugin-gemma`. File sharing via FileProvider, no new core deps.

**Tech Stack:** Kotlin 2.0+, Jetpack Compose M3, PdfRenderer (framework), Tesseract4Android (FOSS), MediaPipe Tasks-GenAI, F-Droid metadata.

**Spec:** `docs/superpowers/specs/2026-09-06-plugin-system-design.md`

## Global Constraints

- Core release APK <20 MB — 0 new `implementation()` deps in `:app`.
- minSdk 24, targetSdk 36, Java 17 — copied verbatim from spec.
- F-Droid compatible only — no Play Core, no MLKit, no binary blobs in git.
- Contract version `EXTRA_CONTRACT_V=1` on every plugin Intent.
- File sharing via FileProvider + `FLAG_GRANT_READ_URI_PERMISSION` only.

---

### Task 1: Core plugin contract

**Files:**
- Create: `android/app/src/main/java/io/github/tasmirz/lumina/data/plugins/PluginAction.kt`
- Create: `android/app/src/main/java/io/github/tasmirz/lumina/data/plugins/PluginManager.kt`
- Test: `android/app/src/test/java/io/github/tasmirz/lumina/PluginManagerTest.kt`

**Interfaces:**
- Consumes: `android.content.pm.PackageManager`
- Produces: `PluginAction.ACTION_VIEW_PDF: String`, `PluginAction.ACTION_CONVERT_PDF: String`, `PluginAction.ACTION_AI_CHAT: String`, `PluginAction.EXTRA_CONTRACT_V: String`, `PluginManager.isInstalled(pkg: String): Boolean`, `PluginManager.buildViewPdfIntent(pkg: String, uri: Uri): Intent`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tasmirz.lumina

import io.github.tasmirz.lumina.data.plugins.PluginAction
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginManagerTest {
    @Test fun contractVersionIsOne() {
        assertEquals(1, PluginAction.CONTRACT_V)
    }
    @Test fun actionsHaveNamespace() {
        assertEquals("io.github.tasmirz.lumina.plugin.VIEW_PDF", PluginAction.ACTION_VIEW_PDF)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "io.github.tasmirz.lumina.PluginManagerTest"` in `android/`
Expected: FAIL with "unresolved reference PluginAction"

- [ ] **Step 3: Write minimal implementation**

```kotlin
package io.github.tasmirz.lumina.data.plugins

import android.content.Context
import android.content.Intent
import android.net.Uri

object PluginAction {
    const val CONTRACT_V = 1
    const val ACTION_VIEW_PDF = "io.github.tasmirz.lumina.plugin.VIEW_PDF"
    const val ACTION_CONVERT_PDF = "io.github.tasmirz.lumina.plugin.CONVERT_PDF"
    const val ACTION_AI_CHAT = "io.github.tasmirz.lumina.plugin.AI_CHAT"
    const val EXTRA_CONTRACT_V = "extra_contract_v"
    const val EXTRA_FILE_URI = "extra_file_uri"
    const val PKG_PDF = "io.github.tasmirz.lumina.plugin.pdf"
    const val PKG_CONVERT = "io.github.tasmirz.lumina.plugin.convert"
    const val PKG_GEMMA = "io.github.tasmirz.lumina.plugin.gemma"
}

class PluginManager(private val ctx: Context) {
    fun isInstalled(pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: Exception) { false }

    fun buildViewPdfIntent(pkg: String, uri: Uri): Intent =
        Intent(PluginAction.ACTION_VIEW_PDF).setPackage(pkg)
            .putExtra(PluginAction.EXTRA_CONTRACT_V, PluginAction.CONTRACT_V)
            .putExtra(PluginAction.EXTRA_FILE_URI, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "io.github.tasmirz.lumina.PluginManagerTest"` in `android/`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/io/github/tasmirz/lumina/data/plugins/ android/app/src/test/java/io/github/tasmirz/lumina/PluginManagerTest.kt
git commit -m "feat(plugins): add v1 intent contract and PluginManager"
```

### Task 2: Manifest queries + FileProvider

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/xml/filepaths.xml`

**Interfaces:**
- Consumes: Task 1 `PluginAction.*`
- Produces: `<queries>` for 3 plugin packages, `FileProvider` authority `io.github.tasmirz.lumina.fileprovider`

- [ ] **Step 1: Create filepaths xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths><cache-path name="shared" path="shared/" /><external-files-path name="books" path="." /></paths>
```

- [ ] **Step 2: Patch AndroidManifest.xml** — add inside `<manifest>` (before `<application>`):

```xml
<queries>
    <package android:name="io.github.tasmirz.lumina.plugin.pdf" />
    <package android:name="io.github.tasmirz.lumina.plugin.convert" />
    <package android:name="io.github.tasmirz.lumina.plugin.gemma" />
</queries>
```

And inside `<application>`:

```xml
<provider android:name="androidx.core.content.FileProvider" android:authorities="io.github.tasmirz.lumina.fileprovider" android:exported="false" android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/filepaths" />
</provider>
```

Note: `androidx.core:core-ktx` already in catalog — no new dep.

- [ ] **Step 3: Verify manifest merges**

Run: `./gradlew :app:processDebugMainManifest --dry-run` in `android/`
Expected: SUCCESS

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/AndroidManifest.xml android/app/src/main/res/xml/filepaths.xml
git commit -m "feat(plugins): add queries and FileProvider for plugin sharing"
```

### Task 3: Plugins settings screen

**Files:**
- Create: `android/app/src/main/java/io/github/tasmirz/lumina/ui/settings/PluginsScreen.kt`
- Modify: `android/app/src/main/java/io/github/tasmirz/lumina/ui/settings/AdvancedSettingsScreen.kt:60-67` (add `PLUGINS` to `SettingsSubScreen` enum)

**Interfaces:**
- Consumes: Task 1 `PluginManager.isInstalled()`
- Produces: `PluginsScreen(manager: PluginManager, onInstall: (pkg: String) -> Unit)` composable

- [ ] **Step 1: Write composable**

```kotlin
package io.github.tasmirz.lumina.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tasmirz.lumina.data.plugins.PluginAction
import io.github.tasmirz.lumina.data.plugins.PluginManager

@Composable
fun PluginsScreen(manager: PluginManager, onInstall: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PluginRow("PDF Reader", PluginAction.PKG_PDF, manager, onInstall)
        PluginRow("PDF to EPUB", PluginAction.PKG_CONVERT, manager, onInstall)
        PluginRow("Gemma 2B Offline", PluginAction.PKG_GEMMA, manager, onInstall)
    }
}

@Composable
private fun PluginRow(name: String, pkg: String, manager: PluginManager, onInstall: (String) -> Unit) {
    val installed = remember(pkg) { manager.isInstalled(pkg) }
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(if (installed) "Installed" else pkg) },
        trailingContent = {
            if (!installed) Button(onClick = { onInstall(pkg) }) { Text("Install") }
        }
    )
}
```

`onInstall` implementation (in caller): open `fdroid.app://details?id=pkg`, fallback to `https://f-droid.org/packages/pkg` then GitHub release APK.

- [ ] **Step 2: Wire into AdvancedSettingsScreen** — add `PLUGINS("Plugins", "PDF, converter, offline AI", Icons.Outlined.Extension)` to enum, navigate to `PluginsScreen`.

- [ ] **Step 3: Manual test** — `./gradlew :app:assembleDebug` in `android/`, open Advanced Settings → Plugins, verify 3 rows show Not Installed.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/io/github/tasmirz/lumina/ui/settings/
git commit -m "feat(plugins): add Plugins settings screen with F-Droid deep-link"
```

### Task 4: Monorepo modules scaffold

**Files:**
- Modify: `android/settings.gradle.kts` (add `include(":plugin-pdf", ":plugin-convert", ":plugin-gemma")`)
- Create: `android/plugin-pdf/build.gradle.kts`, `android/plugin-convert/build.gradle.kts`, `android/plugin-gemma/build.gradle.kts` (each `com.android.application`, same `minSdk 24 targetSdk 36`, unique `applicationId`)
- Create: `metadata/io.github.tasmirz.lumina.plugin.pdf.yml` (copy from `metadata/io.github.tasmirz.lumina.yml`, adjust id)

**Interfaces:**
- Consumes: Task 1 contract (copy `PluginAction.kt` into each plugin, no `:app` dependency)
- Produces: 4 APK outputs

- [ ] **Step 1: Add includes to settings.gradle.kts**

```kotlin
include(":app", ":plugin-pdf", ":plugin-convert", ":plugin-gemma")
```

- [ ] **Step 2: Create plugin-pdf/build.gradle.kts**

```kotlin
plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.serialization) }
android { namespace = "io.github.tasmirz.lumina.plugin.pdf"; compileSdk = 36
    defaultConfig { applicationId = "io.github.tasmirz.lumina.plugin.pdf"; minSdk = 24; targetSdk = 36; versionCode = 1; versionName = "1.0" } }
```

Repeat for convert + gemma with their namespaces.

- [ ] **Step 3: Verify all assemble**

Run: `./gradlew assembleDebug` in `android/`
Expected: 4 APKs under `plugin-*/build/outputs/apk/debug/`

- [ ] **Step 4: Commit**

```bash
git add android/settings.gradle.kts android/plugin-*/ metadata/
git commit -m "chore(plugins): scaffold monorepo modules and F-Droid metadata"
```

### Task 5: plugin-pdf as-is viewer

**Files:**
- Create: `android/plugin-pdf/src/main/java/io/github/tasmirz/lumina/plugin/pdf/PdfViewerActivity.kt`
- Modify: `android/plugin-pdf/src/main/AndroidManifest.xml` (intent-filter for `VIEW_PDF`, `exported=true`)

**Interfaces:**
- Consumes: Task 1 extras `EXTRA_FILE_URI`, `EXTRA_CONTRACT_V`
- Produces: renders via `android.graphics.pdf.PdfRenderer`, returns `EXTRA_LAST_PAGE`

- [ ] **Step 1: Implement viewer with PdfRenderer**

```kotlin
// PdfViewerActivity: open ParcelFileDescriptor from EXTRA_FILE_URI,
// PdfRenderer(page).render(bitmap), LazyColumn of bitmaps.
// On pause: setResult(RESULT_OK, Intent().putExtra("extra_last_page", lastPage))
```

Reject if `intent.getIntExtra(EXTRA_CONTRACT_V, 0) != 1` with Toast "Update plugin".

- [ ] **Step 2: Manual test** — install both APKs, open PDF from core via `PluginManager.buildViewPdfIntent()`, verify render + progress return.

- [ ] **Step 3: Commit**

```bash
git add android/plugin-pdf/
git commit -m "feat(plugin-pdf): as-is PdfRenderer viewer with v1 contract"
```

### Task 6: plugin-convert Tesseract to EPUB

**Files:**
- Create: `android/plugin-convert/src/main/java/.../ConvertActivity.kt`, `PdfTextExtractor.kt`, `EpubWriter.kt`
- Modify: `android/plugin-convert/build.gradle.kts` (add `cz.adaptech.tesseract4android:tesseract4android:4.x`, download `eng.traineddata` at runtime to `files/tessdata/`, NOT bundled)

**Interfaces:**
- Consumes: `EXTRA_FILE_URI`
- Produces: `EXTRA_EPUB_URI` pointing at `Download/Lumina/converted/<name>.epub`

- [ ] **Step 1: Extract + OCR fallback** — try text extraction per page, if blank run Tesseract, concatenate to chapters.

- [ ] **Step 2: Write EPUB** — reuse OPF structure mirroring core `EpubParser` expectations (container.xml, content.opf, toc.ncx), zip to output.

- [ ] **Step 3: Manual test** — convert text PDF + scanned PDF, re-import output in core via `EpubParser.parseEpub()`.

- [ ] **Step 4: Commit**

```bash
git add android/plugin-convert/
git commit -m "feat(plugin-convert): Tesseract text+OCR to EPUB with runtime lang download"
```

### Task 7: plugin-gemma MediaPipe offline

**Files:**
- Create: `android/plugin-gemma/src/main/java/.../GemmaActivity.kt`, `ModelDownloader.kt`
- Modify: `android/plugin-gemma/build.gradle.kts` (add `com.google.mediapipe:tasks-genai:0.10.14`)

**Interfaces:**
- Consumes: `EXTRA_CONTEXT_TEXT` (cap 8000 chars in core before send), `EXTRA_MODEL`
- Produces: `EXTRA_REPLY_TEXT`

Gates (in plugin, before download): `ActivityManager.memoryClass >= 6144 MB ram`, `Build.VERSION.SDK_INT >= 31`, WiFi-only via `ConnectivityManager`, storage check `>2GB free`. Else show "Device not supported, use online Gemini in Lumina settings".

- [ ] **Step 1: Downloader with progress** — download `.task` to `files/models/gemma2b.task` with checksum, resumable.

- [ ] **Step 2: Inference via LlmInference** — `LlmInference.createFromOptions()`, session with spoiler-proof context only.

- [ ] **Step 3: Manual test** — on 6GB+ device, download over WiFi, ask "summarize up to chapter 3", verify no spoilers beyond cursor.

- [ ] **Step 4: Commit**

```bash
git add android/plugin-gemma/
git commit -m "feat(plugin-gemma): MediaPipe Gemma2B with flagship gate and model download"
```

### Task 8: Size gate + docs

**Files:**
- Create: `.github/workflows/size.yml` (or `justfile` recipe `size-check`)
- Modify: `README.md` (Plugins section)

- [ ] **Step 1: Add size check**

```bash
./gradlew :app:assembleRelease
stat -c%s android/app/build/outputs/apk/release/app-release.apk
# fail if > 20971520 (20 MB)
```

- [ ] **Step 2: Run**

Run: `./gradlew :app:assembleRelease` in `android/`
Expected: core APK <20 MB

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/size.yml README.md
git commit -m "chore: enforce core <20MB size gate and document plugins"
```
