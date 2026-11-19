package io.github.valentinaschiavon99.megyejatek.core.security

/**
 * Cleans text that players type and that other players may later see (nicknames on a
 * leaderboard). Removes control characters and invisible direction overrides, which can be
 * used to make a name render as something else, collapses whitespace and caps the length.
 */
object InputSanitizer {
    const val MAX_NICKNAME_LENGTH = 24

    private val bidiControls = setOf(
        0x200E, 0x200F, // LRM, RLM
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E, // embeddings and overrides
        0x2066, 0x2067, 0x2068, 0x2069, // isolates
    )
    private val invisibles = setOf(0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF) // zero-width chars, BOM

    fun nickname(raw: String): String {
        val sb = StringBuilder()
        var count = 0
        var lastWasSpace = true // also drops leading spaces
        var i = 0
        while (i < raw.length && count < MAX_NICKNAME_LENGTH) {
            val cp = raw.codePointAt(i)
            i += Character.charCount(cp)
            when {
                Character.isWhitespace(cp) || Character.isSpaceChar(cp) -> {
                    if (!lastWasSpace) {
                        sb.append(' ')
                        count++
                        lastWasSpace = true
                    }
                }
                Character.isISOControl(cp) || cp in bidiControls || cp in invisibles -> Unit
                Character.getType(cp) == Character.PRIVATE_USE.toInt() ||
                    Character.getType(cp) == Character.UNASSIGNED.toInt() -> Unit
                else -> {
                    sb.appendCodePoint(cp)
                    count++
                    lastWasSpace = false
                }
            }
        }
        return sb.toString().trimEnd()
    }
}
