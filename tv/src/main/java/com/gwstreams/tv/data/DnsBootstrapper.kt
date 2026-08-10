package com.gwstreams.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DnsBootstrapper {
    // The master file controlling the current active domains.
    // E.g. {"active_portal": "http://new-domain.com:8080"}
    private const val MASTER_DNS_URL = "https://raw.githubusercontent.com/boberthegr8/greatwhitestreams4/main/auto/dns.json"

    suspend fun fetchLatestPortalHost(currentHost: String?): String? = withContext(Dispatchers.IO) {
        try {
            val jsonText = (URL(MASTER_DNS_URL).openConnection() as HttpURLConnection).run {
                connectTimeout = 5_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "GWStreams-DNS")
                inputStream.bufferedReader().use { it.readText() }
            }
            val json = JSONObject(jsonText)
            val newHost = json.optString("active_portal", "")
            
            // Apply maintenance killswitch if present
            if (json.optBoolean("maintenance_mode", false)) {
                return@withContext "MAINTENANCE_MODE_ACTIVE"
            }
            
            if (newHost.isNotEmpty() && newHost != currentHost) {
                return@withContext newHost
            }
        } catch (e: Exception) {
            // DNS fetch failed (offline or GitHub blocked), return null to use existing
        }
        return@withContext null
    }
}
