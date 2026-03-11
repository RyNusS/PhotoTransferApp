package com.family.phototransfer.util

import java.io.InputStream
import java.security.MessageDigest

object HashUtil {

    /**
     * 바이트 배열로부터 SHA-256 해시 생성
     * 파일 전송 시 중복 감지에 사용
     */
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * InputStream으로부터 SHA-256 해시 생성
     */
    fun sha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 빠른 중복 감지용: 파일명 + 크기 조합 키
     * 정밀 해시 전 사전 필터링에 사용
     */
    fun quickKey(fileName: String, fileSize: Long): String {
        return "${fileName}_${fileSize}"
    }
}
