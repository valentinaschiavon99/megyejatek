package io.github.valentinaschiavon99.megyejatek.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class InputSanitizerTest {
    @Test fun keepsNormalHungarianNames() {
        assertEquals("Tina", InputSanitizer.nickname("Tina"))
        assertEquals("Őrült Ödön", InputSanitizer.nickname("Őrült Ödön"))
        assertEquals("tina_99 🎯", InputSanitizer.nickname("tina_99 🎯"))
    }

    @Test fun trimsAndCollapsesWhitespace() {
        assertEquals("a b", InputSanitizer.nickname("  a \t\n  b  "))
        assertEquals("", InputSanitizer.nickname("   "))
    }

    @Test fun removesControlAndDirectionCharacters() {
        // U+202E would render "evil" + reversed text; zero-width chars hide look-alike names.
        assertEquals("abcdef", InputSanitizer.nickname("abc‮def"))
        assertEquals("admin", InputSanitizer.nickname("ad​min"))
        assertEquals("xy", InputSanitizer.nickname("x\u0000\u0007y"))
        assertEquals("ok", InputSanitizer.nickname("﻿ok⁦"))
    }

    @Test fun capsLengthWithoutSplittingEmoji() {
        val long = "a".repeat(40)
        assertEquals(InputSanitizer.MAX_NICKNAME_LENGTH, InputSanitizer.nickname(long).length)
        val emoji = "🎯".repeat(30) // each is two UTF-16 chars
        val out = InputSanitizer.nickname(emoji)
        assertEquals(InputSanitizer.MAX_NICKNAME_LENGTH, out.codePointCount(0, out.length))
    }
}
