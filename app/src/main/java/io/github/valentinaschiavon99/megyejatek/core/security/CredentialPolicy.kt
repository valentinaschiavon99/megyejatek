package io.github.valentinaschiavon99.megyejatek.core.security

/**
 * Client-side rules for usernames and passwords. The same password rules should be enforced
 * server-side in Firebase Authentication (Password policy); the app checks first so the player
 * gets instant feedback, but never relies on its own check alone.
 */
object CredentialPolicy {

    // ---- username ----------------------------------------------------------------------------

    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 20

    /** ASCII only: the username becomes the local part of an internal e-mail address. */
    private val usernameRegex = Regex("^[A-Za-z][A-Za-z0-9_.-]{${USERNAME_MIN - 1},${USERNAME_MAX - 1}}$")

    enum class UsernameProblem { TooShort, TooLong, MustStartWithLetter, InvalidCharacters }

    fun usernameProblem(username: String): UsernameProblem? = when {
        username.length < USERNAME_MIN -> UsernameProblem.TooShort
        username.length > USERNAME_MAX -> UsernameProblem.TooLong
        !username.first().isAsciiLetter() -> UsernameProblem.MustStartWithLetter
        !usernameRegex.matches(username) -> UsernameProblem.InvalidCharacters
        else -> null
    }

    /** Usernames are unique regardless of case: "Tina" and "tina" are the same account. */
    fun normalizedUsername(username: String) = username.trim().lowercase()

    /**
     * Firebase Authentication signs in with e-mail + password. Players only choose a username, so it
     * is mapped to an address under a reserved, non-routable domain (RFC 2606 ".invalid"):
     * nothing is ever sent there.
     */
    fun internalEmail(username: String) = "${normalizedUsername(username)}@$INTERNAL_DOMAIN"

    const val INTERNAL_DOMAIN = "users.megyejatek.invalid"

    // ---- password ----------------------------------------------------------------------------

    const val PASSWORD_MIN = 10
    const val PASSWORD_MAX = 128 // Firebase's limit is 4096; keep hashing work bounded

    enum class PasswordRule { Length, Lowercase, Uppercase, Digit, Symbol, NotUsername, NotCommon }

    /** Returns the rules the password breaks; empty means it is accepted. */
    fun passwordProblems(password: String, username: String = ""): Set<PasswordRule> = buildSet {
        if (password.length !in PASSWORD_MIN..PASSWORD_MAX) add(PasswordRule.Length)
        if (password.none { it.isLowerCase() }) add(PasswordRule.Lowercase)
        if (password.none { it.isUpperCase() }) add(PasswordRule.Uppercase)
        if (password.none { it.isDigit() }) add(PasswordRule.Digit)
        if (password.none { !it.isLetterOrDigit() && !it.isWhitespace() }) add(PasswordRule.Symbol)
        val lower = password.lowercase()
        if (username.length >= USERNAME_MIN && lower.contains(normalizedUsername(username))) {
            add(PasswordRule.NotUsername)
        }
        if (commonPatterns.any { lower.contains(it) }) add(PasswordRule.NotCommon)
    }

    /** 0 (very weak) … 4 (strong), for the strength bar. */
    fun strength(password: String, username: String = ""): Int {
        if (password.isEmpty()) return 0
        val problems = passwordProblems(password, username)
        if (PasswordRule.NotCommon in problems || PasswordRule.NotUsername in problems) return 0
        val classes = listOf(
            password.any { it.isLowerCase() },
            password.any { it.isUpperCase() },
            password.any { it.isDigit() },
            password.any { !it.isLetterOrDigit() },
        ).count { it }
        return when {
            password.length >= 16 && classes >= 3 -> 4
            problems.isEmpty() -> 3
            password.length >= 8 && classes >= 3 -> 2
            password.length >= 6 -> 1
            else -> 0
        }
    }

    /** Fragments that make a password trivially guessable, including local favourites. */
    private val commonPatterns = listOf(
        "password", "passw0rd", "jelszo", "jelszó", "123456", "12345678", "qwerty", "qwertz",
        "asdfgh", "111111", "abc123", "letmein", "iloveyou", "admin", "welcome", "megyejatek",
        "budapest", "magyar", "hungary", "000000",
    )

    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'
}
