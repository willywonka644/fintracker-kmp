package io.github.willywonka644.fintracker.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

private const val TAG = "WiFiSyncDiscovery"
private const val SERVICE_TYPE = "_fintracker._tcp"

class WiFiSyncDiscovery(context: Context) {

    private val nsdManager = context.getSystemService(NsdManager::class.java)

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // Guard against resolving more than one service at a time and against
    // calling onFound repeatedly for the same service.
    @Volatile private var resolving = false
    @Volatile private var found = false

    fun discoverDesktop(
        onFound: (host: String, port: Int) -> Unit,
        onLost: () -> Unit,
    ) {
        found = false
        resolving = false

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "NSD discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                if (found || resolving) return
                resolving = true

                @Suppress("DEPRECATION")
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "Resolve failed for ${info.serviceName}, error $errorCode")
                        resolving = false
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress
                        val port = info.port
                        Log.d(TAG, "Resolved: host=$host port=$port")
                        resolving = false
                        if (host != null && port > 0) {
                            found = true
                            onFound(host, port)
                        }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                found = false
                onLost()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "NSD discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.w(TAG, "stopDiscovery failed: ${e.message}")
            }
            discoveryListener = null
        }
        found = false
        resolving = false
    }
}
