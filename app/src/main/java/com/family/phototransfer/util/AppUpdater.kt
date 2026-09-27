package com.family.phototransfer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases 기반 앱 업데이트.
 *
 * CI가 master push마다 "PhotoTransfer-<versionCode>.apk" 파일을 릴리스로 올린다.
 * 앱은 최신 릴리스의 versionCode가 자신보다 크면 내려받아 설치 화면을 띄운다.
 * (Android 10 이하는 보안상 사용자가 "설치"를 한 번 눌러야 함)
 */
object AppUpdater {

    private const val LATEST_URL =
        "https://api.github.com/repos/RyNusS/PhotoTransferApp/releases/latest"
    private val APK_NAME_REGEX = Regex("""PhotoTransfer-(\d+)\.apk""")

    data class Release(
        val versionCode: Int,
        val title: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long
    )

    /** 최신 릴리스 정보 조회 (실패 시 예외) */
    suspend fun fetchLatest(): Release = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PhotoTransferApp")
        }
        try {
            val code = conn.responseCode
            if (code == 404) throw IllegalStateException("아직 올라온 업데이트가 없습니다")
            if (code != 200) throw IllegalStateException("업데이트 서버 응답 오류 ($code)")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val match = APK_NAME_REGEX.matchEntire(asset.getString("name")) ?: continue
                return@withContext Release(
                    versionCode = match.groupValues[1].toInt(),
                    title = json.optString("name", json.optString("tag_name")),
                    notes = json.optString("body", ""),
                    apkUrl = asset.getString("browser_download_url"),
                    apkSize = asset.optLong("size", 0L)
                )
            }
            throw IllegalStateException("릴리스에 설치 파일(APK)이 없습니다")
        } finally {
            conn.disconnect()
        }
    }

    /** APK 다운로드 → 캐시 폴더 파일 반환 */
    suspend fun download(
        context: Context,
        release: Release,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "PhotoTransfer-${release.versionCode}.apk")

        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PhotoTransferApp")
        }
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException("다운로드 실패 (${conn.responseCode})")
            }
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else release.apkSize
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(32_768)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        target
    }

    /** "이 앱에서 설치 허용" 권한이 있는지 */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** 권한 설정 화면 열기 */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /** 시스템 설치 화면 띄우기 */
    fun launchInstall(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
