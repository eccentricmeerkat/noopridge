package com.noop.ui

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SQ9 S1: the changelog's `**bold**` lead sentences render as bold, with no asterisks left in the text. */
class InlineBoldTest {

    @Test
    fun pairedAsterisksBecomeASemiboldSpan() {
        val out = inlineBold("**A lift log (#2098).** An on-device log book.")
        assertEquals("A lift log (#2098). An on-device log book.", out.text)
        assertEquals(1, out.spanStyles.size)
        assertEquals(0, out.spanStyles[0].start)
        assertEquals("A lift log (#2098).".length, out.spanStyles[0].end)
        assertEquals(FontWeight.SemiBold, out.spanStyles[0].item.fontWeight)
    }

    @Test
    fun severalPairsAndPlainText() {
        val out = inlineBold("a **b** c **d** e")
        assertEquals("a b c d e", out.text)
        assertEquals(2, out.spanStyles.size)
        assertEquals("b", out.text.substring(out.spanStyles[0].start, out.spanStyles[0].end))
        assertEquals("d", out.text.substring(out.spanStyles[1].start, out.spanStyles[1].end))
    }

    @Test
    fun anUnpairedMarkerIsKeptAsTyped() {
        val out = inlineBold("5 ** 2 is 25")
        assertEquals("5 ** 2 is 25", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun noMarkerIsUnchanged() {
        assertEquals("plain", inlineBold("plain").text)
    }
}
