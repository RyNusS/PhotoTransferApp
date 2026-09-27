package com.family.phototransfer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.family.phototransfer.util.DeviceCare
import com.family.phototransfer.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PhotoTransferApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // 알림 채널 등록 (앱 시작 시 1회)
        NotificationHelper.createChannel(this)
        // 저전력 수신 설정 로드 + 재부팅 기록 + 1분 간격 동작 기록
        DeviceCare.init(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
