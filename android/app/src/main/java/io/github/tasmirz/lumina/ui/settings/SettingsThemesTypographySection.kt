package io.github.tasmirz.lumina.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tasmirz.lumina.data.BookRepository
import io.github.tasmirz.lumina.model.BackgroundTexture
import io.github.tasmirz.lumina.model.CustomTextureData
import io.github.tasmirz.lumina.model.CustomThemeData
import io.github.tasmirz.lumina.model.ImageFilterMode
import io.github.tasmirz.lumina.model.ReadingMode
import io.github.tasmirz.lumina.model.TextAlignmentMode
import io.github.tasmirz.lumina.model.ThemeFamily
import io.github.tasmirz.lumina.model.ThemeVariant
import io.github.tasmirz.lumina.model.TypefaceMode
import io.github.tasmirz.lumina.ui.components.TexturePreviewCard
import kotlin.math.roundToInt

/**
 * Section 0: Themes, Typography, Margins, Textures, and Visual Filters.
 */
@Composable
fun SettingsThemesTypographySection(
    repository: BookRepository?,
    currentReadingMode: ReadingMode,
    onReadingModeChange: (ReadingMode) -> Unit,
    themeVariant: ThemeVariant,
    onThemeVariantChange: (ThemeVariant) -> Unit,
    themeFamily: ThemeFamily,
    onThemeFamilyChange: (ThemeFamily) -> Unit,
    customThemes: List<CustomThemeData>,
    onNavigateToCustomCss: () -> Unit,
    onRenameTheme: (CustomThemeData) -> Unit,
    backgroundTexture: BackgroundTexture,
    onBackgroundTextureChange: (BackgroundTexture) -> Unit,
    customTextures: List<CustomTextureData>,
    selectedCustomTextureId: String?,
    onPickTexture: () -> Unit,
    onRenameTexture: (CustomTextureData) -> Unit,
    onDeleteTexture: (CustomTextureData) -> Unit,
    dynamicRollingTexture: Boolean,
    onDynamicRollingTextureChange: (Boolean) -> Unit,
    horizontalPadding: Int,
    verticalPadding: Int,
    pagedBottomClearanceDp: Int,
    pagedSafeLinesToRemove: Int,
    fillPageToBottom: Boolean,
    verticallyCenterPages: Boolean,
    adaptiveVerticalSpacing: Boolean,
    goldenRatioChapterOpeners: Boolean,
    allowTableSplitting: Boolean,
    pagedSwipeThreshold: Float,
    typeface: TypefaceMode,
    onTypefaceChange: (TypefaceMode) -> Unit,
    currentFontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    lineHeight: Float,
    onLineHeightChange: (Float) -> Unit,
    letterSpacing: Float,
    onLetterSpacingChange: (Float) -> Unit,
    paragraphSpacing: Float,
    inlineImageWidthPercent: Int,
    textAlignment: TextAlignmentMode,
    onTextAlignmentChange: (TextAlignmentMode) -> Unit,
    imageFilterMode: ImageFilterMode,
    invertScreen: Boolean
) {
    val context = LocalContext.current

    CollapsibleCard(
        icon = Icons.Outlined.Palette,
        title = "Themes, Typography & Margins",
        subtitle = "${typeface.displayName} • Side Margin: ${horizontalPadding}dp"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Reading Mode Selector
            Text(
                text = "Reading Mode",
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple(ReadingMode.SCROLL, "Continuous", Icons.Filled.SwapVert),
                    Triple(ReadingMode.PAGED, "Full Paged", Icons.AutoMirrored.Filled.MenuBook),
                    Triple(ReadingMode.PAGED_SCROLL, "Paged + Scroll", Icons.Filled.UnfoldMore)
                ).forEach { (mode, label, icon) ->
                    val isSelected = currentReadingMode == mode
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable {
                                repository?.setReadingMode(mode)
                                onReadingModeChange(mode)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(13.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = label,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                fontWeight = FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Theme Variant Chips
            Text(
                text = "Color Mode",
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThemeVariant.entries.forEach { variant ->
                    val isSel = variant == themeVariant
                    FilterChip(
                        selected = isSel,
                        onClick = { onThemeVariantChange(variant) },
                        label = { Text(variant.displayName, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Themes Palette
            Text(
                text = "Preset Palettes",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThemeFamily.entries.forEach { family ->
                    val isSel = family == themeFamily
                    FilterChip(
                        selected = isSel,
                        onClick = { onThemeFamilyChange(family) },
                        label = { Text(family.displayName, fontSize = 11.sp) }
                    )
                }
            }

            if (customThemes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Custom Named Themes",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onNavigateToCustomCss,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Manage", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    customThemes.forEach { th ->
                        val isSel = themeFamily == ThemeFamily.CUSTOM &&
                            repository != null &&
                            th.bgColor == repository.customBgColor.value &&
                            th.textColor == repository.customTextColor.value
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                repository?.applyCustomTheme(th)
                                onThemeFamilyChange(ThemeFamily.CUSTOM)
                                Toast.makeText(context, "Applied '${th.name}'", Toast.LENGTH_SHORT).show()
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(th.bgColor.toInt()))
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRenameTheme(th) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            },
                            label = { Text(th.name, fontSize = 11.sp) }
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // VISUAL FILTERS & SCREEN INVERSION
            Text(
                text = "Visual Filters & Screen Inversion",
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Image Filter Mode", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ImageFilterMode.entries.forEach { mode ->
                    val isSel = mode == imageFilterMode
                    FilterChip(
                        selected = isSel,
                        onClick = { repository?.setImageFilterMode(mode) },
                        label = { Text(mode.displayName, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Invert Screen", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                    Text("GPU difference color inversion across the reader canvas", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = invertScreen,
                    onCheckedChange = { repository?.setInvertScreen(it) },
                    modifier = Modifier.size(width = 48.dp, height = 28.dp)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Background Surface Texture
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Background Surface Texture",
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp
                )
                OutlinedButton(
                    onClick = onPickTexture,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Load Texture", fontSize = 11.sp, fontWeight = FontWeight.Normal)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            val isDarkSettings = isSystemInDarkTheme()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    BackgroundTexture.NONE to "Clean",
                    BackgroundTexture.GRAIN to "Paper Grain",
                    BackgroundTexture.PARCHMENT to "Parchment",
                    BackgroundTexture.LINEN to "Linen",
                    BackgroundTexture.CANVAS to "Canvas",
                    BackgroundTexture.KRAFT to "Kraft",
                    BackgroundTexture.RULED_FINE to "Fine Lined",
                    BackgroundTexture.RULED_WIDE to "Wide Lined",
                    BackgroundTexture.RULED_GRID to "Grid Lined"
                ).forEach { (tex, label) ->
                    val isSel = backgroundTexture == tex
                    TexturePreviewCard(
                        label = label,
                        isSelected = isSel,
                        texture = tex,
                        isDark = isDarkSettings,
                        onClick = { onBackgroundTextureChange(tex) }
                    )
                }
                customTextures.forEach { customTex ->
                    val isSel = backgroundTexture == BackgroundTexture.CUSTOM && selectedCustomTextureId == customTex.id
                    TexturePreviewCard(
                        label = customTex.name,
                        isSelected = isSel,
                        texture = BackgroundTexture.CUSTOM,
                        isDark = isDarkSettings,
                        onClick = { repository?.selectCustomTexture(customTex.id) }
                    )
                }
            }

            if (customTextures.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Custom Surface Textures",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    customTextures.forEach { tex ->
                        val isSelected = backgroundTexture == BackgroundTexture.CUSTOM && selectedCustomTextureId == tex.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { repository?.selectCustomTexture(tex.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.Texture,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = tex.name,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 12.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                ) {
                                                    Text(
                                                        "Active",
                                                        fontSize = 8.5.sp,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${if (tex.isTiled) "Seamless Tiled" else "Cover Fit"} • ${(tex.opacity * 100).toInt()}% opacity",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onRenameTexture(tex) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = "Rename",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteTexture(tex) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dynamic Rolling Texture", fontSize = 12.sp, fontWeight = FontWeight.Normal)
                    Text("Roll background textures when scrolling or swiping pages", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = dynamicRollingTexture,
                    onCheckedChange = onDynamicRollingTextureChange
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Page Margins & Spacing
            Text(
                text = "Page Margins & Padding",
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Horizontal Padding
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Side Margin (Horizontal)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${horizontalPadding} dp", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = horizontalPadding.toFloat(),
                onValueChange = { repository?.setHorizontalPadding(it.toInt()) },
                valueRange = 8f..48f,
                steps = 9,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Vertical Padding
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Top / Bottom Margin (Vertical)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${verticalPadding} dp", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = verticalPadding.toFloat(),
                onValueChange = { repository?.setVerticalPadding(it.toInt()) },
                valueRange = 0f..48f,
                steps = 11,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Paged Bottom Clearance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Paged Bottom Clearance", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Controls bottom clearance buffer to fill the page or avoid dock overlap", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Text("${pagedBottomClearanceDp} dp", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = pagedBottomClearanceDp.toFloat(),
                onValueChange = { repository?.setPagedBottomClearanceDp(it.toInt()) },
                valueRange = 0f..80f,
                steps = 15,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Paged Safe Lines to Remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Paged Line Safety Reducer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Reduces line budget per page in portrait & landscape to eliminate bottom text cut-off", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Text(if (pagedSafeLinesToRemove == 0) "0 (Auto)" else "-$pagedSafeLinesToRemove ${if (pagedSafeLinesToRemove == 1) "line" else "lines"}", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = pagedSafeLinesToRemove.toFloat(),
                onValueChange = { repository?.setPagedSafeLinesToRemove(it.toInt()) },
                valueRange = 0f..8f,
                steps = 7,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Fill Page to Bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fill Page to Bottom", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Flows paragraphs down to the bottom margin rather than leaving empty gaps", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Switch(
                    checked = fillPageToBottom,
                    onCheckedChange = { repository?.setFillPageToBottom(it) },
                    modifier = Modifier.size(width = 48.dp, height = 28.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Vertically Center Short Pages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Vertically Center Short Pages", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Centers content vertically on chapter openers and short pages", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Switch(
                    checked = verticallyCenterPages,
                    onCheckedChange = { repository?.setVerticallyCenterPages(it) },
                    modifier = Modifier.size(width = 48.dp, height = 28.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Adaptive Vertical Justification
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Adaptive Vertical Justification", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Distributes spacing evenly between paragraphs on under-filled pages", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Switch(
                    checked = adaptiveVerticalSpacing,
                    onCheckedChange = { repository?.setAdaptiveVerticalSpacing(it) },
                    modifier = Modifier.size(width = 48.dp, height = 28.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Golden Ratio Chapter Openers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Golden Ratio Chapter Openers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Positions chapter headers ~25% down the screen for classic book aesthetics", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Switch(
                    checked = goldenRatioChapterOpeners,
                    onCheckedChange = { repository?.setGoldenRatioChapterOpeners(it) },
                    modifier = Modifier.size(width = 48.dp, height = 28.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Allow Table Splitting Across Pages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow Table Splitting Across Pages", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Splits multi-row tables across page breaks with repeated column headers", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Switch(
                    checked = allowTableSplitting,
                    onCheckedChange = { repository?.setAllowTableSplitting(it) },
                    modifier = Modifier.size(width = 48.dp, height = 28.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Page Turn Swipe Effort
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Page Turn Swipe Effort", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Distance required to turn page (lower = easier flick)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                val effortLabel = when {
                    pagedSwipeThreshold <= 0.14f -> "Minimal"
                    pagedSwipeThreshold <= 0.22f -> "Light"
                    pagedSwipeThreshold <= 0.34f -> "Medium"
                    else -> "Firm"
                }
                Text(
                    text = "${(pagedSwipeThreshold * 100).toInt()}% ($effortLabel)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = pagedSwipeThreshold,
                onValueChange = { repository?.setPagedSwipeThreshold(it) },
                valueRange = 0.03f..0.50f,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Typography Engine Controls
            Text(
                text = "Typography Engine",
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Typeface Selector
            Text("Reading Typeface", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TypefaceMode.entries.forEach { mode ->
                    val isSel = mode == typeface
                    FilterChip(
                        selected = isSel,
                        onClick = { onTypefaceChange(mode) },
                        label = { Text(mode.displayName, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Font Size Stepper & Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Font Size", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val newSize = (currentFontSize - 1).coerceAtLeast(12)
                            repository?.setFontSize(newSize)
                            onFontSizeChange(newSize)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease Font Size", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = "${currentFontSize} sp",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = {
                            val newSize = (currentFontSize + 1).coerceAtMost(48)
                            repository?.setFontSize(newSize)
                            onFontSizeChange(newSize)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase Font Size", modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = currentFontSize.toFloat(),
                onValueChange = {
                    val s = it.roundToInt()
                    repository?.setFontSize(s)
                    onFontSizeChange(s)
                },
                valueRange = 12f..48f,
                steps = 35,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Line Height
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Line Spacing", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${String.format("%.2f", lineHeight)}x", fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = lineHeight,
                onValueChange = onLineHeightChange,
                valueRange = 1.0f..2.2f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Letter Spacing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Letter Spacing", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${String.format("%.2f", letterSpacing)}sp", fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = letterSpacing,
                onValueChange = onLetterSpacingChange,
                valueRange = 0.0f..2.5f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Paragraph Spacing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Paragraph Spacing", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${String.format("%.2f", paragraphSpacing)}x", fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = paragraphSpacing,
                onValueChange = { repository?.setParagraphSpacing(it) },
                valueRange = 0.6f..2.4f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Inline Image Width
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Inline Image Width", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${inlineImageWidthPercent}%", fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = inlineImageWidthPercent.toFloat(),
                onValueChange = { repository?.setInlineImageWidthPercent(it.toInt()) },
                valueRange = 50f..100f,
                steps = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Text Alignment
            Text("Text Alignment", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextAlignmentMode.entries.forEach { align ->
                    val isSel = align == textAlignment
                    FilterChip(
                        selected = isSel,
                        onClick = { onTextAlignmentChange(align) },
                        label = { Text(align.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}
