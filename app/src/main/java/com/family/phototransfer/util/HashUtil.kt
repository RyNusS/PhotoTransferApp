package com.family.phototransfer.util

import java.io.InputStream
import java.security.MessageDigest

object HashUtil {

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).toHexString()
    }

    fun sha256(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65_536)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().toHexString()
    }

    /**
     * InputStream을 읽으면서 동시에 SHA-256 해시를 계산하고
     * 읽은 데이터를 outputStream으로 흘려보내는 TeeStream
     * → 해시 계산과 전송을 한 번의 스트림으로 처리
     */
    fun sha256WithTee(input: InputStream, output: java.io.OutputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65_536)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
            output.write(buffer, 0, bytesRead)
        }
        output.flush()
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }
}
