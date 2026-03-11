package com.family.phototransfer.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class DiscoveredDevice(
    val ipAddress: String,
    val deviceName: String,
    val port: Int = TRANSFER_PORT
)

const val TRANSFER_PORT = 9876          // 파일 전송용 포트
const val DISCOVERY_PORT = 9877         // 기기 탐색용 포트
const val SOCKET_TIMEOUT_MS = 300       // 탐색 타임아웃 (ms)
const val TRANSFER_TIMEOUT_MS = 30_000  // 전송 타임아웃 (ms)

class WifiDeviceScanner(private val context: Context) {

    /**
     * 같은 WiFi 네트워크에서 픽셀1 수신 앱이 실행 중인 기기를 탐색
     * IP 범위 전체를 병렬로 스캔 (예: 192.168.1.1 ~ 192.168.1.254)
     */
    suspend fun scanNetwork(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        // 현재 연결된 WiFi의 게이트웨이 IP로 서브넷 범위 계산
        val dhcpInfo = wifiManager.dhcpInfo
        val gateway = dhcpInfo.gateway
        if (gateway == 0) return@withContext emptyList()

        // 서브넷 prefix 추출 (예: "192.168.1")
        val gatewayIp = intToIp(gateway)
        val subnet = gatewayIp.substringBeforeLast(".")

        // 1~254 전체 IP를 병렬로 동시 스캔
        val jobs = (1..254).map { host ->
            async {
                val targetIp = "$subnet.$host"
                if (isPhotoTransferReceiver(targetIp)) {
                    val deviceName = getDeviceName(targetIp) ?: "Unknown Device ($targetIp)"
                    DiscoveredDevice(
                        ipAddress = targetIp,
                        deviceName = deviceName
                    )
                } else null
            }
        }

        jobs.awaitAll().filterNotNull()
    }

    /**
     * 특정 IP에 수신 앱(PhotoTransfer)이 실행 중인지 확인
     * TRANSFER_PORT에 소켓 연결 시도로 판단
     */
    private fun isPhotoTransferReceiver(ip: String): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, TRANSFER_PORT), SOCKET_TIMEOUT_MS)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 연결된 기기의 이름 조회 (호스트명)
     */
    private fun getDeviceName(ip: String): String? {
        return try {
            val addr = InetAddress.getByName(ip)
            addr.hostName.takeIf { it != ip }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 현재 기기의 IP 주소 반환
     */
    fun getMyIpAddress(): String {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        return intToIp(wifiInfo.ipAddress)
    }

    // int 형태의 IP를 문자열로 변환 (Android WiFi API는 int로 반환)
    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}
