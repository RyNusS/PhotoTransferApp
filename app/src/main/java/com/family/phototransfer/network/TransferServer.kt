package com.family.phototransfer.network

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
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
    val sourceDevice: String = ""   // ✅ 송신 기기명
)

class TransferServer(
    private val saveDirectory:  File,
    private val onFileReceived: (ReceivedFile) -> Unit,
    private val onError:        (String) -> Unit,
    private val context:        Context? = null
) {
    private var serverSocket: ServerSocket? = null
    private var discoverySocket: ServerSocket? = null  // ✅ 탐색 전용 소켓
    var isRunning = false
        private set

    suspend fun start() = withContext(Dispatchers.IO) {
        try {
            serverSocket   = ServerSocket(TRANSFER_PORT)
            discoverySocket = ServerSocket(DISCOVERY_PORT)
            isRunning = true
            Log.d(TAG, "서버 시작 - 전송포트: $TRANSFER_PORT, 탐색포트: $DISCOVERY_PORT")

            // 탐색 포트 리스너를 별도 스레드로 실행
            val discoveryThread = Thread {
                runDiscoveryListener()
            }.apply { isDaemon = true; start() }

            // 전송 포트 메인 루프
            while (isActive && isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    Log.d(TAG, "클라이언트 연결: ${clientSocket.inetAddress.hostAddress}")
                    // ✅ 방어 로직 2: 각 클라이언트를 독립 스레드로 처리
                    // → 한 파일 처리 중 오류가 나도 서버 루프 전체가 죽지 않음
                    Thread {
                        try {
                            handleClient(clientSocket)
                        } catch (e: Exception) {
                            Log.e(TAG, "클라이언트 스레드 오류: ${e.message}")
                        }
                    }.apply { isDaemon = true }.start()
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "클라이언트 처리 오류: ${e.message}")
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

    /**
     * 탐색 포트(9877) 리스너
     * 연결 요청이 오면 "PHOTO_TRANSFER_RECEIVER" 응답 후 즉시 종료
     * → 파일 전송 포트(9876)에는 전혀 영향 없음
     */
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

    private fun handleClient(socket: Socket) {
        socket.use {
            try {
                // 메타데이터 수신 (짧은 타임아웃)
                socket.soTimeout = 15_000
                val input  = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())

                // 1) 메타데이터 수신
                val fileName     = input.readUTF()
                val fileSize     = input.readLong()
                input.readUTF()  // 해시 자리 (빈 문자열, 미사용)
                val sourceDevice = try { input.readUTF() } catch (e: Exception) { "" }

                // 동영상 여부에 따라 타임아웃 재설정
                val isVideo = fileName.lowercase().let {
                    it.endsWith(".mp4") || it.endsWith(".mov") || it.endsWith(".avi") ||
                    it.endsWith(".mkv") || it.endsWith(".3gp") || it.endsWith(".m4v")
                }
                socket.soTimeout = if (isVideo) 300_000 else 60_000
                Log.d(TAG, "수신 시작: $fileName ($fileSize bytes, video=$isVideo) from $sourceDevice")

                // 2) 파일 저장
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context != null) {
                    saveViaMediaStore(input, fileName, fileSize, context)
                } else {
                    saveViaFileSystem(input, fileName, fileSize)
                }

                if (result == null) {
                    output.writeUTF("ERROR")
                    output.flush()
                    onError("파일 저장 실패: $fileName")
                    return
                }

                output.writeUTF("OK")
                output.flush()
                Log.d(TAG, "수신 완료: $fileName (${result.fileSize} bytes)")
                onFileReceived(result.copy(sourceDevice = sourceDevice))

            } catch (e: Exception) {
                Log.e(TAG, "파일 수신 오류: ${e.javaClass.simpleName} - ${e.message}")
                try {
                    DataOutputStream(socket.getOutputStream()).apply {
                        writeUTF("ERROR"); flush()
                    }
                } catch (_: Exception) {}
                onError("파일 수신 오류: ${e.message}")
            }
        }
    }

    /**
     * Android 10+ : MediaStore API로 Pictures에 저장
     * → EACCES 오류 없이 /Pictures 직접 저장 가능
     */
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
                        if (isVideo) Environment.DIRECTORY_MOVIES
                        else         Environment.DIRECTORY_PICTURES
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            uri = ctx.contentResolver.insert(contentUri, values)
                ?: return null

            var received = 0L
            ctx.contentResolver.openOutputStream(uri)?.use { outputStream ->
                received = copyStream(input, outputStream, fileSize)
            }

            // 수신 완료 → IS_PENDING 해제
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                ctx.contentResolver.update(uri, values, null, null)
            }

            Log.d(TAG, "MediaStore 저장 완료: $uri")
            ReceivedFile(fileName = fileName, savedPath = uri.toString(), fileSize = received)

        } catch (e: Exception) {
            Log.e(TAG, "MediaStore 저장 오류: ${e.message}")
            // ✅ 방어 로직 1: 저장 실패 시 IS_PENDING 파일 즉시 삭제 (좀비 파일 방지)
            uri?.let {
                try {
                    ctx.contentResolver.delete(it, null, null)
                    Log.d(TAG, "실패한 IS_PENDING 파일 삭제: $it")
                } catch (de: Exception) {
                    Log.e(TAG, "IS_PENDING 파일 삭제 실패: ${de.message}")
                }
            }
            null
        }
    }

    /**
     * Android 9 이하 : 기존 FileOutputStream 방식
     */
    private fun saveViaFileSystem(
        input:    DataInputStream,
        fileName: String,
        fileSize: Long
    ): ReceivedFile? {
        return try {
            if (!saveDirectory.exists()) saveDirectory.mkdirs()
            val saveFile = getUniqueFile(saveDirectory, fileName)

            var received = 0L
            FileOutputStream(saveFile).use { fos ->
                received = copyStream(input, fos, fileSize)
            }

            ReceivedFile(
                fileName  = saveFile.name,
                savedPath = saveFile.absolutePath,
                fileSize  = received
            )
        } catch (e: Exception) {
            Log.e(TAG, "파일 시스템 저장 오류: ${e.message}")
            null
        }
    }

    /** InputStream → OutputStream 복사, 수신 바이트 수 반환 */
    private fun copyStream(
        input:    DataInputStream,
        output:   OutputStream,
        fileSize: Long
    ): Long {
        val buffer    = ByteArray(8192)
        var received  = 0L
        while (received < fileSize) {
            val toRead    = minOf(buffer.size.toLong(), fileSize - received).toInt()
            val bytesRead = input.read(buffer, 0, toRead)
            if (bytesRead == -1) break
            output.write(buffer, 0, bytesRead)
            received += bytesRead
        }
        output.flush()
        return received
    }

    private fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast(".").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png"         -> "image/png"
            "gif"         -> "image/gif"
            "webp"        -> "image/webp"
            "bmp"         -> "image/bmp"
            "mp4"         -> "video/mp4"
            "mov"         -> "video/quicktime"
            "avi"         -> "video/x-msvideo"
            "mkv"         -> "video/x-matroska"
            "3gp"         -> "video/3gpp"
            else          -> "image/jpeg"
        }
    }

    private fun getUniqueFile(dir: File, fileName: String): File {
        var file = File(dir, fileName)
        if (!file.exists()) return file
        val name = fileName.substringBeforeLast(".")
        val ext  = fileName.substringAfterLast(".", "")
        var i = 1
        while (file.exists()) {
            file = File(dir, if (ext.isEmpty()) "${name}_$i" else "${name}_$i.$ext")
            i++
        }
        return file
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
            discoverySocket?.close()
            discoverySocket = null
            Log.d(TAG, "서버 중지")
        } catch (e: Exception) {
            Log.e(TAG, "서버 중지 오류: ${e.message}")
        }
    }
}
