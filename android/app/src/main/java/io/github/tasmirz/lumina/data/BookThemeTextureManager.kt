package io.github.tasmirz.lumina.data

import android.net.Uri
import io.github.tasmirz.lumina.model.BackgroundTexture
import io.github.tasmirz.lumina.model.CustomTextureData
import io.github.tasmirz.lumina.model.CustomThemeData
import io.github.tasmirz.lumina.model.ThemeFamily
import java.io.File

/**
 * Handles custom theme generation and user-imported background texture management.
 */
object BookThemeTextureManager {

    fun saveCustomTheme(repository: BookRepository, name: String, bg: Long, text: Long, accent: Long) {
        val theme = CustomThemeData(
            name = name,
            bgColor = bg,
            textColor = text,
            accentColor = accent
        )
        repository.dbHelper.saveCustomTheme(theme)
        repository._customThemes.value = repository.dbHelper.getAllCustomThemes()
    }

    fun renameCustomTheme(repository: BookRepository, themeId: String, newName: String) {
        val existing = repository._customThemes.value.find { it.id == themeId } ?: return
        val updated = existing.copy(name = newName)
        repository.dbHelper.saveCustomTheme(updated)
        repository._customThemes.value = repository.dbHelper.getAllCustomThemes()
    }

    fun applyCustomTheme(repository: BookRepository, theme: CustomThemeData) {
        repository.setCustomThemeColors(theme.bgColor, theme.textColor, theme.accentColor)
        repository.setThemeFamily(ThemeFamily.CUSTOM)
    }

    fun createAndApplyCustomTheme(repository: BookRepository, name: String, bg: Long, text: Long, accent: Long): CustomThemeData {
        val theme = CustomThemeData(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            bgColor = bg,
            textColor = text,
            accentColor = accent
        )
        repository.dbHelper.saveCustomTheme(theme)
        repository._customThemes.value = repository.dbHelper.getAllCustomThemes()
        applyCustomTheme(repository, theme)
        return theme
    }

    fun updateAndApplyCustomTheme(
        repository: BookRepository,
        name: String,
        bg: Long? = null,
        text: Long? = null,
        accent: Long? = null
    ): CustomThemeData? {
        val existing = repository._customThemes.value.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
            ?: repository._customThemes.value.firstOrNull { it.name.lowercase().contains(name.trim().lowercase()) }
            ?: return null
        val updated = existing.copy(
            bgColor = bg ?: existing.bgColor,
            textColor = text ?: existing.textColor,
            accentColor = accent ?: existing.accentColor
        )
        repository.dbHelper.saveCustomTheme(updated)
        repository._customThemes.value = repository.dbHelper.getAllCustomThemes()
        applyCustomTheme(repository, updated)
        return updated
    }

    fun deleteCustomTheme(repository: BookRepository, themeId: String) {
        repository.dbHelper.deleteCustomTheme(themeId)
        repository._customThemes.value = repository.dbHelper.getAllCustomThemes()
    }

    fun importCustomTexture(
        repository: BookRepository,
        name: String,
        sourceUri: Uri,
        isTiled: Boolean = true,
        opacity: Float = 0.5f
    ): CustomTextureData? {
        return try {
            val textureId = java.util.UUID.randomUUID().toString()
            val texturesDir = File(repository.context.filesDir, "textures").apply { if (!exists()) mkdirs() }
            val targetFile = File(texturesDir, "$textureId.png")

            repository.context.contentResolver.openInputStream(sourceUri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            val texture = CustomTextureData(
                id = textureId,
                name = name.ifBlank { "Texture ${repository._customTextures.value.size + 1}" },
                imagePath = targetFile.absolutePath,
                isTiled = isTiled,
                opacity = opacity.coerceIn(0.05f, 1.0f),
                createdAt = System.currentTimeMillis()
            )
            repository.dbHelper.saveCustomTexture(texture)
            repository._customTextures.value = repository.dbHelper.getAllCustomTextures()
            selectCustomTexture(repository, texture.id)
            texture
        } catch (e: Exception) {
            io.github.tasmirz.lumina.util.LuminaLog.w("BookRepository", "importCustomTexture error", e)
            null
        }
    }

    fun selectCustomTexture(repository: BookRepository, textureId: String) {
        repository._selectedCustomTextureId.value = textureId
        repository._backgroundTexture.value = BackgroundTexture.CUSTOM
        repository.updateReaderSettings { it.copy(backgroundTexture = BackgroundTexture.CUSTOM, selectedCustomTextureId = textureId) }
        repository.prefs.edit()
            .putString("background_texture", BackgroundTexture.CUSTOM.name)
            .putString("selected_custom_texture_id", textureId)
            .apply()
        repository.persistSettingToDb("background_texture", BackgroundTexture.CUSTOM.name)
        repository.persistSettingToDb("selected_custom_texture_id", textureId)
    }

    fun renameCustomTexture(repository: BookRepository, textureId: String, newName: String) {
        repository.dbHelper.updateCustomTextureName(textureId, newName)
        repository._customTextures.value = repository.dbHelper.getAllCustomTextures()
    }

    fun deleteCustomTexture(repository: BookRepository, textureId: String) {
        val existing = repository._customTextures.value.find { it.id == textureId }
        existing?.imagePath?.let { path ->
            try {
                val f = File(path)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }
        repository.dbHelper.deleteCustomTexture(textureId)
        repository._customTextures.value = repository.dbHelper.getAllCustomTextures()
        if (repository._selectedCustomTextureId.value == textureId) {
            val fallback = repository._customTextures.value.firstOrNull()
            if (fallback != null) {
                selectCustomTexture(repository, fallback.id)
            } else {
                repository.setBackgroundTexture(BackgroundTexture.NONE)
                repository._selectedCustomTextureId.value = ""
                repository.prefs.edit().putString("selected_custom_texture_id", "").apply()
                repository.persistSettingToDb("selected_custom_texture_id", "")
            }
        }
    }
}
