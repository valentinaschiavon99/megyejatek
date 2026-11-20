package io.github.valentinaschiavon99.megyejatek.core.update

import io.github.valentinaschiavon99.megyejatek.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Contents of version.json, published by CI next to the APK in the "latest" release. */
@Serializable
data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val sha: String = "",
    val apkUrl: String,
    val sha256: String = "",
    val publishedAt: String = "",
)

sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus
    data class Available(val release: ReleaseInfo) : UpdateStatus
    data class Failed(val reason: String) : UpdateStatus
}

/**
 * Checks GitHub for a newer build. Only a fixed HTTPS address is ever contacted, and the
 * download link is accepted only if it points to this repository's releases, so a tampered
 * version.json can't send the player to another site.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val currentSha: String get() = BuildConfig.GIT_SHA

    suspend fun check(): UpdateStatus = withContext(Dispatchers.IO) {
        try {
            val release = parse(download(BuildConfig.UPDATE_INFO_URL))
            if (release.versionCode > currentVersionCode) UpdateStatus.Available(release) else UpdateStatus.UpToDate
        } catch (e: IOException) {
            UpdateStatus.Failed(e.message ?: "network")
        } catch (e: IllegalArgumentException) {
            UpdateStatus.Failed(e.message ?: "invalid")
        }
    }

    internal fun parse(body: String): ReleaseInfo {
        val info = json.decodeFromString(ReleaseInfo.serializer(), body)
        require(isTrustedApkUrl(info.apkUrl)) { "unexpected download address" }
        require(info.versionCode > 0) { "bad version" }
        return info
    }

    private fun download(address: String): String {
        val conn = URL(address).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = true // github.com -> release asset host, both HTTPS
            conn.setRequestProperty("Accept", "application/json, application/octet-stream")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.use { stream ->
                val bytes = stream.readNBytesCompat(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) throw IOException("response too large")
                bytes.decodeToString()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        while (total < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - total))
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    companion object {
        private const val MAX_BYTES = 16 * 1024
        private const val TRUSTED_PREFIX = "https://github.com/valentinaschiavon99/megyejatek/releases/download/"

        fun isTrustedApkUrl(url: String): Boolean =
            url.startsWith(TRUSTED_PREFIX) && url.endsWith(".apk") && !url.contains("..")
    }
}
