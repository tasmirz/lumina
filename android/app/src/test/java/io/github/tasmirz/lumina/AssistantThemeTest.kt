package io.github.tasmirz.lumina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.tasmirz.lumina.data.AssistantAction
import io.github.tasmirz.lumina.data.AssistantService

class AssistantThemeTest {

    @Test
    fun testParsePresetThemes() {
        val service = AssistantService()

        val cyberpunk = service.parseLocalCommand("create a cyberpunk theme", 10)
        assertNotNull(cyberpunk)
        assertTrue(cyberpunk is AssistantAction.CreateTheme)
        val cp = cyberpunk as AssistantAction.CreateTheme
        assertEquals("Cyberpunk Neon", cp.name)
        assertEquals(0xFF0D0D15L, cp.bgColor)
        assertEquals(0xFFE0E6EDL, cp.textColor)
        assertEquals(0xFF00F0FFL, cp.accentColor)

        val sepia = service.parseLocalCommand("make a sepia theme for night reading", 10)
        assertNotNull(sepia)
        assertTrue(sepia is AssistantAction.CreateTheme)
        val sp = sepia as AssistantAction.CreateTheme
        assertEquals("Vintage Sepia", sp.name)

        val emerald = service.parseLocalCommand("set an emerald theme", 10)
        assertNotNull(emerald)
        assertTrue(emerald is AssistantAction.CreateTheme)
        assertEquals("Emerald Night", (emerald as AssistantAction.CreateTheme).name)

        val oled = service.parseLocalCommand("generate an oled theme", 10)
        assertNotNull(oled)
        assertTrue(oled is AssistantAction.CreateTheme)
        val ol = oled as AssistantAction.CreateTheme
        assertEquals(0xFF000000L, ol.bgColor)
    }

    @Test
    fun testParseHexCodeTheme() {
        val service = AssistantService()

        val hexCommand = service.parseLocalCommand("create theme Solar with bg #112233, text #eeeeee, accent #ffaa00", 10)
        assertNotNull(hexCommand)
        assertTrue(hexCommand is AssistantAction.CreateTheme)
        val custom = hexCommand as AssistantAction.CreateTheme
        assertEquals(0xFF112233L, custom.bgColor)
        assertEquals(0xFFEEEEEEL, custom.textColor)
        assertEquals(0xFFFFAA00L, custom.accentColor)
    }

    @Test
    fun testParseUpdateTheme() {
        val service = AssistantService()

        val updateCommand = service.parseLocalCommand("update theme Solar with bg #223344, accent #00ffaa", 10)
        assertNotNull(updateCommand)
        assertTrue(updateCommand is AssistantAction.UpdateTheme)
        val ut = updateCommand as AssistantAction.UpdateTheme
        assertEquals("Solar", ut.name)
        assertEquals(0xFF223344L, ut.bgColor)
        assertEquals(null, ut.textColor)
        assertEquals(0xFF00FFAAL, ut.accentColor)

        val modifyCommand = service.parseLocalCommand("modify theme Cyberpunk Neon with text #ffffff", 10)
        assertNotNull(modifyCommand)
        assertTrue(modifyCommand is AssistantAction.UpdateTheme)
        val mt = modifyCommand as AssistantAction.UpdateTheme
        assertEquals("Cyberpunk Neon", mt.name)
        assertEquals(null, mt.bgColor)
        assertEquals(0xFFFFFFFFL, mt.textColor)
        assertEquals(null, mt.accentColor)
    }
}
