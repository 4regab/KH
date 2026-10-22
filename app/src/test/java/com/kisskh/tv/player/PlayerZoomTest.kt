package com.kisskh.tv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerZoomTest {
    @Test
    fun pinchOutFillsAndPinchInFits() {
        assertTrue(PlayerZoom.after(fill = false, scale = 1.5f))
        assertFalse(PlayerZoom.after(fill = true, scale = 0.6f))
    }

    @Test
    fun smallPinchChangesNothing() {
        assertTrue(PlayerZoom.after(fill = true, scale = 1.05f))
        assertFalse(PlayerZoom.after(fill = false, scale = 0.95f))
    }
}
