package dm1sh.android_tracker.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class DeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class DeviceMetrics(
        val batteryLevel: Int,
        val batteryState: Int?,
        val storageFreeBytes: Long,
        val storageTotalBytes: Long,
        val networkState: Int?,
        val wifiSsid: String?
    )

    fun collect(): DeviceMetrics {
        val battery = readBattery()
        val storage = readStorage()
        val network = readNetwork()
        return DeviceMetrics(
            batteryLevel = battery.level,
            batteryState = battery.state,
            storageFreeBytes = storage.free,
            storageTotalBytes = storage.total,
            networkState = network.type,
            wifiSsid = network.ssid
        )
    }

    // ---- Battery ----

    private data class BatteryInfo(val level: Int, val state: Int?)

    private fun readBattery(): BatteryInfo {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return BatteryInfo(-1, null)

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val levelPct = if (scale > 0 && level >= 0) (level * 100 / scale) else -1

        // Raw BatteryManager.BATTERY_STATUS_* value: UNKNOWN=1, CHARGING=2,
        // DISCHARGING=3, NOT_CHARGING=4, FULL=5. Anything else -> null.
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val state = status.takeIf {
            it == BatteryManager.BATTERY_STATUS_UNKNOWN ||
                it == BatteryManager.BATTERY_STATUS_CHARGING ||
                it == BatteryManager.BATTERY_STATUS_DISCHARGING ||
                it == BatteryManager.BATTERY_STATUS_NOT_CHARGING ||
                it == BatteryManager.BATTERY_STATUS_FULL
        }
        return BatteryInfo(levelPct, state)
    }

    // ---- Storage ----

    private data class StorageInfo(val free: Long, val total: Long)

    private fun readStorage(): StorageInfo {
        val stat = StatFs(File(context.filesDir.parent!!).path)
        val free = stat.availableBytes
        val total = stat.totalBytes
        return StorageInfo(free, total)
    }

    // ---- Network / SSID ----

    private data class NetworkInfo(val type: Int?, val ssid: String?)

    private fun readNetwork(): NetworkInfo {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        // First present transport in precedence order, as a raw
        // NetworkCapabilities.TRANSPORT_* value (CELLULAR=0, WIFI=1,
        // BLUETOOTH=2, ETHERNET=3). NONE/VPN/OTHER -> null.
        val transports = listOf(
            NetworkCapabilities.TRANSPORT_WIFI,
            NetworkCapabilities.TRANSPORT_CELLULAR,
            NetworkCapabilities.TRANSPORT_ETHERNET,
            NetworkCapabilities.TRANSPORT_BLUETOOTH
        )
        val type = capabilities?.let { caps ->
            transports.firstOrNull { caps.hasTransport(it) }
        }

        val ssid = when {
            // No active network -> not connected -> null; also null for
            // cellular / no transport.
            capabilities == null || type == NetworkCapabilities.TRANSPORT_CELLULAR || type == null -> null
            else -> readWifiSsid(connectivityManager)
        }

        return NetworkInfo(type, ssid)
    }

    /**
     * Reads the current connected Wi-Fi SSID, guarded by version-specific APIs.
     * Returns null when Wi-Fi is enabled but not connected, or when the SSID
     * can't be resolved (permission missing, no active connection).
     */
    private fun readWifiSsid(connectivityManager: ConnectivityManager): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            readWifiSsidApi31Plus(connectivityManager)
        } else {
            @Suppress("DEPRECATION")
            readWifiSsidLegacy()
        }
    }

    @Suppress("DEPRECATION")
    private fun readWifiSsidLegacy(): String? {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectionInfo = wifiManager.connectionInfo ?: return null
        if (connectionInfo.supplicantState != android.net.wifi.SupplicantState.COMPLETED) {
            return null
        }
        return normalizeSsid(connectionInfo.ssid)
    }

    private fun readWifiSsidApi31Plus(connectivityManager: ConnectivityManager): String? {
        val latch = CountDownLatch(1)
        val ssidRef = AtomicReference<String?>(null)

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback(
            NetworkCallbackFlagsIncludeLocationInfo
        ) {
            override fun onCapabilitiesChanged(
                network: android.net.Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val info = networkCapabilities.transportInfo
                if (info is WifiInfo) {
                    ssidRef.set(normalizeSsid(info.ssid))
                }
                latch.countDown()
            }
        }

        connectivityManager.registerNetworkCallback(request, callback)
        try {
            // Synchronously wait a bounded amount of time for the SSID.
            latch.await(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            connectivityManager.unregisterNetworkCallback(callback)
        }
        return ssidRef.get()
    }

    private fun normalizeSsid(ssid: String?): String? {
        val cleaned = ssid
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotEmpty() }
            ?.takeIf { !it.equals("unknown ssid", ignoreCase = true) }
            ?.takeIf { it != WifiManager.UNKNOWN_SSID }
        return if (cleaned.isNullOrBlank() || cleaned == "<unknown ssid>") null else cleaned
    }

    companion object {
        // FLAG_INCLUDE_LOCATION_INFO is an int constant on NetworkCallback.
        private val NetworkCallbackFlagsIncludeLocationInfo: Int =
            ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
    }
}
