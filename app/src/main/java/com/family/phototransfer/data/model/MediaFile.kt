package com.family.phototransfer.data.model

data class MediaFile(
    val id: Long,
    val uri: String,
    val name: String,
    val path: String,
    val size: Long,          // bytes
    val dateModified: Long,  // epoch millis
    val mimeType: String,    // image/* or video/*
    val hash: String = ""    // SHA-256 (중복 감지용, 필요시 계산)
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

data class DeviceInfo(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 9876,
    val isPixelReceiver: Boolean = false
)
