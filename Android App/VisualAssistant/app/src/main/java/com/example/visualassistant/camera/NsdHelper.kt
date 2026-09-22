package com.example.visualassistant.camera

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Helper for finding the Raspberry Pi on the local network using Network Service Discovery (NSD).
 */
class NsdHelper(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val TAG = "NsdHelper"
    private val SERVICE_TYPE = "_http._tcp."
    private val TARGET_DEVICE_NAME = "cameraGlasses"

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    
    var onDeviceFound: ((String) -> Unit)? = null

    fun startDiscovery() {
        stopDiscovery()
        
        Log.i(TAG, "Acquiring multicast lock and starting discovery...")
        try {
            multicastLock = wifiManager.createMulticastLock("visualAssistantNsd").apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock", e)
        }

        Log.i(TAG, "Starting discovery for $SERVICE_TYPE matching $TARGET_DEVICE_NAME")
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service candidate found: ${service.serviceName} (${service.serviceType})")
                if (service.serviceName.contains(TARGET_DEVICE_NAME, ignoreCase = true)) {
                    Log.i(TAG, "Matched target device: ${service.serviceName}. Attempting to resolve...")
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress
                            Log.i(TAG, "Successfully resolved ${serviceInfo.serviceName} to IP: $host")
                            if (host != null) {
                                onDeviceFound?.invoke(host)
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped for $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed to start for $serviceType: $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed to stop for $serviceType: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                Log.d(TAG, "Stopping active discovery")
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping discovery", e)
            }
        }
        discoveryListener = null

        multicastLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                    Log.d(TAG, "Multicast lock released")
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing multicast lock", e)
                }
            }
        }
        multicastLock = null
    }
}
