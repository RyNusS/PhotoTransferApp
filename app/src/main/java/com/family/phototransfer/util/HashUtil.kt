package com.family.phototransfer.util

import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-256 해시 유틸리티
 * - ByteArray 버전 (기존 호환)
 * - InputStream 버전 (스트리밍 전송 지원, OOM 방지)
 */
object HashUtil {

    /**
     * ByteArray → SHA-256 (기존 코드 호환용)
     */
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).toHexString()
    }

    /**
     * InputStream → SHA-256 (대용량 파일도 OOM 없이 처리)
     * 스트림을 읽으면서 해시를 계산하므로 메모리 사용량이 일정함
     */
    fun sha256(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65_536) // 64KB 버퍼
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }
}
