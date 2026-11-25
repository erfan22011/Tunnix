package com.tunnix.ui

import com.tunnix.ui.components.getFlagEmoji
import org.junit.Assert.assertEquals
import org.junit.Test

class FlagEmojiTest {

    @Test
    fun `test flag emoji mapping for common servers`() {
        assertEquals("🇳🇱", getFlagEmoji("NL", "Netherlands 1"))
        assertEquals("🇳🇱", getFlagEmoji("NL", "Server NL-Amsterdam"))
        assertEquals("🇨🇭", getFlagEmoji("CH", "Swiss Fast"))
        assertEquals("🇨🇭", getFlagEmoji("CH", "Switzerland 2"))
        assertEquals("🇩🇪", getFlagEmoji("DE", "Germany Frankfurt"))
        assertEquals("🇫🇷", getFlagEmoji("FR", "France Paris"))
        assertEquals("🇬🇧", getFlagEmoji("GB", "UK London"))
        assertEquals("🇺🇸", getFlagEmoji("US", "USA New York"))
        assertEquals("🌐", getFlagEmoji("UN", "Unknown Location"))
    }
}
