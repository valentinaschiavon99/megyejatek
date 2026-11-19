package io.github.valentinaschiavon99.megyejatek.core.security

import io.github.valentinaschiavon99.megyejatek.core.security.CredentialPolicy.PasswordRule
import io.github.valentinaschiavon99.megyejatek.core.security.CredentialPolicy.UsernameProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialPolicyTest {

    @Test fun validUsernames() {
        for (u in listOf("tina", "Tina_99", "a.b-c", "abc", "x".repeat(20))) {
            assertEquals(u, null, CredentialPolicy.usernameProblem(u))
        }
    }

    @Test fun invalidUsernames() {
        assertEquals(UsernameProblem.TooShort, CredentialPolicy.usernameProblem("ab"))
        assertEquals(UsernameProblem.TooLong, CredentialPolicy.usernameProblem("a".repeat(21)))
        assertEquals(UsernameProblem.MustStartWithLetter, CredentialPolicy.usernameProblem("9tina"))
        assertEquals(UsernameProblem.MustStartWithLetter, CredentialPolicy.usernameProblem("_tina"))
        assertEquals(UsernameProblem.InvalidCharacters, CredentialPolicy.usernameProblem("tína"))
        assertEquals(UsernameProblem.InvalidCharacters, CredentialPolicy.usernameProblem("ti na"))
        assertEquals(UsernameProblem.InvalidCharacters, CredentialPolicy.usernameProblem("tina@x"))
    }

    @Test fun internalEmailIsCaseInsensitive() {
        assertEquals("tina@users.megyejatek.invalid", CredentialPolicy.internalEmail("Tina"))
        assertEquals(CredentialPolicy.internalEmail("TINA"), CredentialPolicy.internalEmail("tina"))
    }

    @Test fun strongPasswordPasses() {
        assertTrue(CredentialPolicy.passwordProblems("Kecske!Szeged42", "tina").isEmpty())
        assertEquals(3, CredentialPolicy.strength("Kecske!Szeged42", "tina"))
        assertEquals(4, CredentialPolicy.strength("Hortobágy-Puszta#2026x", "tina"))
    }

    @Test fun eachRuleIsReported() {
        assertTrue(PasswordRule.Length in CredentialPolicy.passwordProblems("Ab1!xyz"))
        assertTrue(PasswordRule.Lowercase in CredentialPolicy.passwordProblems("ABCDEFGH1!X"))
        assertTrue(PasswordRule.Uppercase in CredentialPolicy.passwordProblems("abcdefgh1!x"))
        assertTrue(PasswordRule.Digit in CredentialPolicy.passwordProblems("Abcdefgh!xy"))
        assertTrue(PasswordRule.Symbol in CredentialPolicy.passwordProblems("Abcdefgh12x"))
        assertTrue(PasswordRule.Length in CredentialPolicy.passwordProblems("Aa1!" + "x".repeat(200)))
    }

    @Test fun usernameAndCommonWordsAreRejected() {
        assertTrue(PasswordRule.NotUsername in CredentialPolicy.passwordProblems("Tina!2026Xyz", "tina"))
        assertTrue(PasswordRule.NotCommon in CredentialPolicy.passwordProblems("Password!2026"))
        assertTrue(PasswordRule.NotCommon in CredentialPolicy.passwordProblems("Budapest#1990"))
        assertEquals(0, CredentialPolicy.strength("Password!2026"))
    }

    @Test fun strengthGrows() {
        assertEquals(0, CredentialPolicy.strength(""))
        assertEquals(0, CredentialPolicy.strength("abc"))
        assertEquals(1, CredentialPolicy.strength("abcdefg"))
        assertEquals(2, CredentialPolicy.strength("Abcdef1x"))
    }
}
