package com.hcqdrive.service.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log

object MdnsService {

    private const val TAG = "HcqDrive"
    private const val SERVICE_TYPE = "_http._tcp"

    private var nsdManager: NsdManager? = null
    private var isRegistered = false
    private var multicastLock: WifiManager.MulticastLock? = null
    private var listenerKey: NsdManager.RegistrationListener? = null

    fun register(context: Context, port: Int, name: String = "HcqDrive"): Boolean {
        if (isRegistered) return true
        return try {
            val manager = context.applicationContext
                .getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return false.also { Log.w(TAG, "mDNS: NsdManager not available") }
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = name
                serviceType = SERVICE_TYPE
                this.port = port
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo?) {
                    isRegistered = true
                    Log.i(TAG, "mDNS: registered '${info?.serviceName}' on port ${info?.port}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                    Log.w(TAG, "mDNS: registration failed error=$errorCode")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo?) {
                    isRegistered = false
                    Log.i(TAG, "mDNS: unregistered '${info?.serviceName}'")
                }
                override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                    Log.w(TAG, "mDNS: unregistration failed error=$errorCode")
                }
            }
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            listenerKey = listener
            nsdManager = manager
            acquireMulticastLock(context.applicationContext)
            true
        } catch (e: Exception) {
            Log.w(TAG, "mDNS: register exception: ${e.message}")
            releaseMulticastLock()
            false
        }
    }

    fun unregister() {
        val manager = nsdManager
        val listener = listenerKey
        if (manager != null && listener != null) {
            try {
                manager.unregisterService(listener)
            } catch (e: Exception) {
                Log.w(TAG, "mDNS: unregister exception: ${e.message}")
            }
        }
        releaseMulticastLock()
        nsdManager = null
        listenerKey = null
        isRegistered = false
    }

    private fun acquireMulticastLock(context: Context) {
        if (multicastLock != null) return
        try {
            val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            val lock = wifi.createMulticastLock("hcqdrive:mdns")
            lock.setReferenceCounted(false)
            lock.acquire()
            multicastLock = lock
        } catch (e: SecurityException) {
            // CHANGE_WIFI_MULTICAST_LOCK not granted; mDNS still works on most stacks.
            Log.w(TAG, "mDNS: multicast lock unavailable (${e.message})")
        } catch (e: Exception) {
            Log.w(TAG, "mDNS: multicast lock failed: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock ?: return
        try {
            if (lock.isHeld) lock.release()
        } catch (e: Exception) {
            Log.w(TAG, "mDNS: multicast lock release failed: ${e.message}")
        }
        multicastLock = null
    }
}
