package com.dpireborn.app.net

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetUtil {
    /** IPv4 local do celular na rede Wi-Fi/Ethernet/hotspot, ou null se não houver rede. */
    fun localIpv4(): String? {
        var fallback: String? = null
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (nif in Collections.list(ifaces)) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val n = nif.name.lowercase()
                        if (n.startsWith("wlan") || n.startsWith("eth")) return ip
                        if (fallback == null && addr.isSiteLocalAddress) fallback = ip
                    }
                }
            }
        } catch (e: Exception) {
            return fallback
        }
        return fallback
    }
}
