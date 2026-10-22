package com.kisskh.tv.api

import com.kisskh.tv.api.media.AudioCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCheckTest {
    @Test
    fun foreignOnlyIsTrueOnlyForKnownNonEnglishAudio() {
        assertTrue(AudioCheck.foreignOnly(listOf("ta")))
        assertTrue(AudioCheck.foreignOnly(listOf("hi", "ta")))
        assertFalse(AudioCheck.foreignOnly(listOf("en")))
        assertFalse(AudioCheck.foreignOnly(listOf("eng")))
        assertFalse(AudioCheck.foreignOnly(listOf("ta", "en")))
        assertFalse(AudioCheck.foreignOnly(listOf("en-US", "hi")))
        assertFalse(AudioCheck.foreignOnly(listOf("und")))
        assertFalse(AudioCheck.foreignOnly(emptyList()))
        assertFalse(AudioCheck.foreignOnly(listOf(null)))
    }

    @Test
    fun labelNamesTheLanguage() {
        assertEquals("Tamil", AudioCheck.label(listOf("ta")))
    }
}
