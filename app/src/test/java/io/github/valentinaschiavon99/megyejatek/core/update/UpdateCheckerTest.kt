package io.github.valentinaschiavon99.megyejatek.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val good = "https://github.com/valentinaschiavon99/megyejatek/releases/download/latest/megyejatek-debug.apk"

    @Test fun onlyThisRepositorysReleasesAreTrusted() {
        assertTrue(UpdateChecker.isTrustedApkUrl(good))
        assertFalse(UpdateChecker.isTrustedApkUrl("http://github.com/valentinaschiavon99/megyejatek/releases/download/latest/x.apk"))
        assertFalse(UpdateChecker.isTrustedApkUrl("https://github.com/someone-else/megyejatek/releases/download/latest/x.apk"))
        assertFalse(UpdateChecker.isTrustedApkUrl("https://github.com.evil.example/valentinaschiavon99/megyejatek/releases/download/x.apk"))
        assertFalse(UpdateChecker.isTrustedApkUrl("https://github.com/valentinaschiavon99/megyejatek/releases/download/../../x.apk"))
        assertFalse(UpdateChecker.isTrustedApkUrl("https://github.com/valentinaschiavon99/megyejatek/releases/download/latest/page.html"))
    }

    @Test fun parsesVersionJson() {
        val info = UpdateChecker().parse(
            """{"versionCode":42,"versionName":"0.42","sha":"abc1234","apkUrl":"$good","sha256":"ff","extra":true}""",
        )
        assertEquals(42, info.versionCode)
        assertEquals("0.42", info.versionName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsForeignDownloadLinks() {
        UpdateChecker().parse("""{"versionCode":42,"versionName":"0.42","apkUrl":"https://evil.example/app.apk"}""")
    }
}
