package com.family.phototransfer.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receiver 탭의 수신 상태를 Upload 탭과 공유하기 위한 Singleton
 *
 * - ReceiverViewModel: isReceiving = true/false 로 상태 변경
 * - UploadViewModel: isReceiving을 구독하여 탐색 버튼 활성/비활성 제어
 */
@Singleton
class ReceiverStateHolder @Inject constructor() {
    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

    fun setReceiving(receiving: Boolean) {
        _isReceiving.value = receiving
    }
}
