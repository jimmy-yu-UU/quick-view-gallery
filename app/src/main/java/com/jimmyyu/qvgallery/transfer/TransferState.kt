package com.jimmyyu.qvgallery.transfer

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TransferRecord(
    val name: String,
    val size: Long,
    val incoming: Boolean,
    val peer: String?,
    val ok: Boolean = true,
    val time: Long = System.currentTimeMillis(),
)

/** What the receive screen shows while the server runs. */
data class ServerInfo(val code: String, val port: Int, val addresses: List<String>) {
    val urls: List<String> get() = addresses.map { "http://$it:$port/" }
}

/** Process-wide transfer state shared by the service and the UI. */
object TransferState {
    private val _server = MutableStateFlow<ServerInfo?>(null)
    val server: StateFlow<ServerInfo?> = _server.asStateFlow()

    private val _history = MutableStateFlow<List<TransferRecord>>(emptyList())
    val history: StateFlow<List<TransferRecord>> = _history.asStateFlow()

    /** Bumped whenever a file arrives so the gallery can rescan. */
    private val _receivedTick = MutableStateFlow(0)
    val receivedTick: StateFlow<Int> = _receivedTick.asStateFlow()

    internal var running: TransferServer? = null

    internal fun setServer(info: ServerInfo?) {
        _server.value = info
    }

    fun record(r: TransferRecord) {
        _history.update { listOf(r) + it.take(199) }
        if (r.incoming && r.ok) _receivedTick.update { it + 1 }
    }

    fun deviceName(context: Context): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER} ${Build.MODEL}"

    /** IPv4 addresses on Wi-Fi / Ethernet / hotspot interfaces. */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else if (it.name.startsWith("ap") || it.name.startsWith("swlan")) 1 else 2 }
            .flatMap { nif -> nif.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress } }
            .map { it.hostAddress.orEmpty() }
            .distinct()
    }.getOrDefault(emptyList())
}
