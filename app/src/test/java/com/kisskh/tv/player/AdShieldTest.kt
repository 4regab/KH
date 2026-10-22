package com.kisskh.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdShieldTest {
    @Test
    fun theHostListIsFilledIntoTheScript() {
        val script = AdShield.script(listOf("a.com", "b.net"))
        assertTrue(script.contains("[\"a.com\",\"b.net\"]"))
        assertFalse(script.contains("__HOSTS__"))
    }

    @Test
    fun theScriptStaysSmallAndEs5() {
        val script = AdShield.script(listOf("a.com"))
        assertTrue(script.length < 9_000)
        assertFalse(script.contains("=>"))
        assertFalse(script.contains("`"))
        assertFalse(Regex("\\b(let|const)\\s").containsMatchIn(script))
        assertEquals(script.count { it == '{' }, script.count { it == '}' })
        assertEquals(script.count { it == '(' }, script.count { it == ')' })
    }
}
