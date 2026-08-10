package com.gwstreams.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DnsBootstrapper {
    // Global active_portal overrides are intentionally ignored because manual/preset
    // provider hosts must remain authoritative unless the feed becomes provider-aware.
    // Only a schema like {"providers":{"CCTV":{"active_portal":"http://..."}}}
    // is eligible to override a host, and callers must provide the provider key.
    private const val MASTER_DNS_URL = "https://raw.githubusercontent.com/boberthegr8/greatwhitestreams4/main/auto/dns.json"

    suspend fun fetchLatestPortalHost(provider: String?): String? = withContext(Dispatchers.IO) {
        if (provider.isNullOrBlank()) return@withContext null
        try {
            val jsonText = (URL(MASTER_DNS_URL).openConnection() as HttpURLConnection).run {
                connectTimeout = 5_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "GWStreams-DNS")
                inputStream.bufferedReader().use { it.readText() }
            }
            val json = JSONObject(jsonText)
            val providerConfig = json.optJSONObject("providers")?.optJSONObject(provider) ?: return@withContext null
            if (providerConfig.optBoolean("maintenance_mode", false)) {
                return@withContext "MAINTENANCE_MODE_ACTIVE"
            }

            val newHost = providerConfig.optString("active_portal", "")
            if (newHost.isNotEmpty()) {
                return@withContext newHost
            }
        } catch (e: Exception) {
            // Feed missing/unreachable/malformed: keep the caller's configured provider host.
        }
        return@withContext null
    }
}
