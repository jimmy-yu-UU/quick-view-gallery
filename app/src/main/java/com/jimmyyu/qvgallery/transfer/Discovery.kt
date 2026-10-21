package com.jimmyyu.qvgallery.transfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentLinkedQueue

data class Peer(val name: String, val host: String, val port: Int)

/** Announces this device's receiver over mDNS (NSD). */
class Advertiser(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null
    @Volatile var registeredName: String? = null
        private set

    fun start(name: String, port: Int) {
        stop()
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { registeredName = info.serviceName }
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) { Log.w(TAG, "register failed $error") }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit // Not needed: only registration/discovery results matter here.
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit // Not needed: only registration/discovery results matter here.
        }
        listener = l
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
    }

    fun stop() {
        listener?.let { runCatching { nsd.unregisterService(it) } }
        listener = null
        registeredName = null
    }

    companion object {
        const val SERVICE_TYPE = "_qvgallery._tcp."
        private const val TAG = "Advertiser"
    }
}

/** Finds nearby receivers. Resolves one service at a time, as older NSD versions require. */
class Browser(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()
    private var listener: NsdManager.DiscoveryListener? = null
    private val queue = ConcurrentLinkedQueue<NsdServiceInfo>()
    @Volatile private var resolving = false

    fun start(ownName: String?) {
        stop()
        _peers.value = emptyList()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit // Not needed: only registration/discovery results matter here.
            override fun onDiscoveryStopped(type: String) = Unit // Not needed: only registration/discovery results matter here.
            override fun onStartDiscoveryFailed(type: String, error: Int) { Log.w(TAG, "discovery failed $error") }
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit // Not needed: only registration/discovery results matter here.
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName == ownName) return
                queue.add(info)
                resolveNext()
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _peers.update { list -> list.filterNot { it.name == info.serviceName } }
            }
        }
        listener = l
        nsd.discoverServices(Advertiser.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving) return
        val next = queue.poll() ?: return
        resolving = true
        nsd.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                resolving = false
                resolveNext()
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress
                if (host != null) {
                    val peer = Peer(info.serviceName, host, info.port)
                    _peers.update { list -> list.filterNot { it.name == peer.name } + peer }
                }
                resolving = false
                resolveNext()
            }
        })
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        queue.clear()
        resolving = false
    }

    companion object {
        private const val TAG = "Browser"
    }
}
