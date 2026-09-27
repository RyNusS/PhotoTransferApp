package com.family.phototransfer.network

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.family.phototransfer.util.ReceivePowerSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "TransferServer"

data class ReceivedFile(
    val fileName:     String,
    val savedPath:    String,
    val fileSize:     Long,
    val sourceDevice: String = ""
)

class TransferServer(
    private val saveDirectory:  File,
    private val onFileReceived: (ReceivedFile) -> Unit,
    private val onError:        (String) -> Unit,
    private val context:        Context? = null
) {
    private var serverSocket:    ServerSocket? = null
    private var discoverySocket: ServerSocket? = null
    private var httpSocket:      ServerSocket? = null   // HTTP 서버 (cloudflared 용)
    var isRunning = false
        private set

    suspend fun start() = withContext(Dispatchers.IO) {
        try {
            serverSocket    = ServerSocket(TRANSFER_PORT)
            discoverySocket = ServerSocket(DISCOVERY_PORT)
            httpSocket      = ServerSocket(HTTP_TRANSFER_PORT)
            isRunning = true
            Log.d(TAG, "서버 시작 - 전송:$TRANSFER_PORT, 탐색:$DISCOVERY_PORT, HTTP:$HTTP_TRANSFER_PORT")

            Thread { runDiscoveryListener() }.apply { isDaemon = true; start() }
            Thread { runHttpListener() }.apply { isDaemon = true; start() }

            // 전송 포트 메인 루프 (raw TCP)
            while (isActive && isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    Log.d(TAG, "TCP 클라이언트 연결: ${clientSocket.inetAddress.hostAddress}")
                    Thread {
                        try { handleClient(clientSocket) }
                        catch (e: Exception) { Log.e(TAG, "TCP 클라이언트 스레드 오류: ${e.message}") }
                    }.apply { isDaemon = true }.start()
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "TCP 클라이언트 처리 오류: ${e.message}")
                        onError("연결 오류: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "서버 오류: ${e.message}")
            onError("서버 시작 실패: ${e.message}")
        } finally {
            isRunning = false
        }
    }

    // ── 탐색 포트(9877) 리스너 ────────────────────────────────────────
    private fun runDiscoveryListener() {
        while (isRunning) {
            try {
                val client = discoverySocket?.accept() ?: break
                Thread {
                    client.use {
                        try {
                            val output = DataOutputStream(client.getOutputStream())
                            output.writeUTF("PHOTO_TRANSFER_RECEIVER")
                            output.flush()
                        } catch (_: Exception) {}
                    }
                }.start()
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "탐색 리스너 오류: ${e.message}")
            }
        }
    }

    // ── HTTP 포트(9875) 리스너 — cloudflared 터널 전용 ────────────────
    private fun runHttpListener() {
        while (isRunning) {
            try {
                val client = httpSocket?.accept() ?: break
                Thread {
                    try { handleHttpClient(client) }
                    catch (e: Exception) { Log.e(TAG, "HTTP 클라이언트 오류: ${e.message}") }
                }.apply { isDaemon = true }.start()
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "HTTP 리스너 오류: ${e.message}")
            }
        }
    }

    // ── HTTP 클라이언트 처리 ─────────────────────────────────────────
    private fun handleHttpClient(socket: Socket) {
        socket.use {
            try {
                socket.soTimeout = 30_000

                // HTTP 헤더를 바이트 단위로 읽어 \r\n\r\n 까지 수집
                val headerBuf = ByteArrayOutputStream()
                val inputStream = socket.inputStream
                var b1 = -1; var b2 = -1; var b3 = -1

                while (true) {
                    val b = inputStream.read()
                    if (b == -1) return
                    headerBuf.write(b)
                    // \r\n\r\n 감지
                    if (b3 == '\r'.code && b2 == '\n'.code && b1 == '\r'.code && b == '\n'.code) break
                    b3 = b2; b2 = b1; b1 = b
                }

                val headerText = headerBuf.toString("UTF-8")
                val headers = headerText.lines()
                    .drop(1)   // "POST /transfer HTTP/1.1" 스킵
                    .filter { it.contains(':') }
                    .associate { line ->
                        val idx = line.indexOf(':')
                        line.substring(0, idx).trim().lowercase() to line.substring(idx + 1).trim()
                    }

                val fileName = headers["x-filename"]               ?: return
                val fileSize = headers["x-filesize"]?.toLongOrNull() ?: return
                val device   = headers["x-device"]                 ?: ""

                val isVideo = fileName.lowercase().let {
                    it.endsWith(".mp4") || it.endsWith(".mov") || it.endsWith(".avi") ||
                    it.endsWith(".mkv") || it.endsWith(".3gp") || it.endsWith(".m4v")
                }
                socket.soTimeout = if (isVideo) 300_000 else 60_000

                Log.d(TAG, "HTTP 수신 시작: $fileName ($fileSize bytes) from $device")

                val dataInput = DataInputStream(inputStream)
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context != null) {
                    saveViaMediaStore(dataInput, fileName, fileSize, context)
                } else {
                    saveViaFileSystem(dataInput, fileName, fileSize)
                }

                if (result != null) ReceivePowerSaver.pauseBetweenFiles()   // 저전력 모드: 파일 사이 휴식
                val body = if (result != null) "OK" else "ERROR"
                val resp = "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nContent-Type: text/plain\r\n\r\n$body"
                socket.outputStream.write(resp.toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()

                if (result != null) {
                    Log.d(TAG, "HTTP 수신 완료: $fileName")
                    onFileReceived(result.copy(sourceDevice = device))
                } else {
                    onError("HTTP 파일 저장 실패: $fileName")
                }

            } catch (e: Exception) {
                Log.e(TAG, "HTTP 파일 수신 오류: ${e.javaClass.simpleName} - ${e.message}")
                try {
                    val err = "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 5\r\n\r\nERROR"
                    socket.outputStream.write(err.toByteArray(Charsets.UTF_8))
                } catch (_: Exception) {}
                onError("HTTP 파일 수신 오류: ${e.message}")
            }
        }
    }

    // ── raw TCP 클라이언트 처리 (기존 로컬 WiFi) ──────────────────────
    private fun handleClient(socket: Socket) {
        socket.use {
            try {
                socket.soTimeout = 15_000
                val input  = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())

                val fileName     = input.readUTF()
                val fileSize     = input.readLong()
                input.readUTF()  // 해시 자리 (미사용)
                val sourceDevice = try { input.readUTF() } catch (e: Exception) { "" }

                val isVideo = fileName.lowercase().let {
                    it.endsWith(".mp4") || it.endsWith(".mov") || it.endsWith(".avi") ||
                    it.endsWith(".mkv") || it.endsWith(".3gp") || it.endsWith(".m4v")
                }
                socket.soTimeout = if (isVideo) 300_000 else 60_000
                Log.d(TAG, "TCP 수신 시작: $fileName ($fileSize bytes, video=$isVideo) from $sourceDevice")

                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context != null) {
                    saveViaMediaStore(input, fileName, fileSize, context)
                } else {
                    saveViaFileSystem(input, fileName, fileSize)
                }

                if (result == null) {
                    output.writeUTF("ERROR"); output.flush()
                    onError("파일 저장 실패: $fileName")
                    return
                }

                ReceivePowerSaver.pauseBetweenFiles()   // 저전력 모드: 파일 사이 휴식
                output.writeUTF("OK"); output.flush()
                Log.d(TAG, "TCP 수신 완료: $fileName (${result.fileSize} bytes)")
                onFileReceived(result.copy(sourceDevice = sourceDevice))

            } catch (e: Exception) {
                Log.e(TAG, "TCP 파일 수신 오류: ${e.javaClass.simpleName} - ${e.message}")
                try {
                    DataOutputStream(socket.getOutputStream()).apply { writeUTF("ERROR"); flush() }
                } catch (_: Exception) {}
                onError("파일 수신 오류: ${e.message}")
            }
        }
    }

    // ── Android 10+ : MediaStore API 저장 ────────────────────────────
    private fun saveViaMediaStore(
        input:    DataInputStream,
        fileName: String,
        fileSize: Long,
        ctx:      Context
    ): ReceivedFile? {
        var uri: android.net.Uri? = null
        return try {
            val mimeType = guessMimeType(fileName)
            val isVideo  = mimeType.startsWith("video")

            val contentUri = if (isVideo)
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE,    mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            uri = ctx.contentResolver.insert(contentUri, values) ?: return null

            var received = 0L
            ctx.contentResolver.openOutputStream(uri)?.use { outputStream ->
                received = copyStream(input, outputStream, fileSize)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                ctx.contentResolver.update(uri, values, null, null)
            }

            ReceivedFile(
                fileName  = fileName,
                savedPath = uri.toString(),
                fileSize  = received
            )
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore 저장 오류: ${e.message}")
            uri?.let { ctx.contentResolver.delete(it, null, null) }
            null
        }
    }

    // ── Android 9 이하 : 파일시스템 직접 저장 ──────────────────────
    private fun saveViaFileSystem(
        input:    DataInputStream,
        fileName: String,
        fileSize: Long
    ): ReceivedFile? {
        return try {
            val file = File(saveDirectory, fileName)
            var received = 0L
            FileOutputStream(file).use { out ->
                received = copyStream(input, out, fileSize)
            }
            ReceivedFile(fileName = fileName, savedPath = file.absolutePath, fileSize = received)
        } catch (e: Exception) {
            Log.e(TAG, "파일시스템 저장 오류: ${e.message}")
            null
        }
    }

    private fun copyStream(input: DataInputStream, output: OutputStream, fileSize: Long): Long {
        val buffer    = ByteArray(65_536)
        var totalRead = 0L
        val startNanos = System.nanoTime()
        while (totalRead < fileSize) {
            val toRead    = minOf(buffer.size.toLong(), fileSize - totalRead).toInt()
            val bytesRead = input.read(buffer, 0, toRead)
            if (bytesRead == -1) break
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            ReceivePowerSaver.throttle(startNanos, totalRead)   // 저전력 모드: 수신 속도 제한
        }
        return totalRead
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() }    catch (_: Exception) {}
        try { discoverySocket?.close() } catch (_: Exception) {}
        try { httpSocket?.close() }      catch (_: Exception) {}
        serverSocket    = null
        discoverySocket = null
        httpSocket      = null
    }

    private fun guessMimeType(fileName: String): String = when {
        fileName.lowercase().endsWith(".mp4")  -> "video/mp4"
        fileName.lowercase().endsWith(".mov")  -> "video/quicktime"
        fileName.lowercase().endsWith(".avi")  -> "video/avi"
        fileName.lowercase().endsWith(".mkv")  -> "video/x-matroska"
        fileName.lowercase().endsWith(".3gp")  -> "video/3gpp"
        fileName.lowercase().endsWith(".m4v")  -> "video/x-m4v"
        fileName.lowercase().endsWith(".jpg")  -> "image/jpeg"
        fileName.lowercase().endsWith(".jpeg") -> "image/jpeg"
        fileName.lowercase().endsWith(".png")  -> "image/png"
        fileName.lowercase().endsWith(".gif")  -> "image/gif"
        fileName.lowercase().endsWith(".heic") -> "image/heic"
        fileName.lowercase().endsWith(".webp") -> "image/webp"
        else -> "image/jpeg"
    }
}
