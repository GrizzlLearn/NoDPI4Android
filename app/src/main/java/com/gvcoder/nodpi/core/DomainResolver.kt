package com.gvcoder.nodpi.core

import org.json.JSONObject
import java.net.InetAddress
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.ConcurrentHashMap

/** A literal HTTPS endpoint avoids bootstrapping DoH through the failing system DNS. */
class DomainResolver(private val doh: Boolean, private val ipv4Only: Boolean = false) {
    fun resolve(host: String): InetAddress {
        if (ipv4Only && host.contains(':')) throw UnknownHostException("IPv6 is disabled by IPv4-only VPN mode")
        if (ipv4Only && !doh) return InetAddress.getAllByName(host).firstOrNull { it is java.net.Inet4Address }
            ?: throw UnknownHostException("No IPv4 address for $host")
        if (!doh || host.contains(':') || host.matches(Regex("[0-9.]+"))) return InetAddress.getByName(host)
        cache[host]?.takeIf { it.expires > System.nanoTime() }?.let { return it.address }
        val name = URLEncoder.encode(host, "UTF-8")
        val connection = URL("https://1.1.1.1/dns-query?name=$name&type=A").openConnection(Proxy.NO_PROXY) as HttpsURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/dns-json")
            if (connection.responseCode != 200) throw UnknownHostException("DoH HTTP ${connection.responseCode}: $host")
            val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val answers = json.optJSONArray("Answer")
            if (json.optInt("Status", -1) == 0 && answers != null) {
                for (i in 0 until answers.length()) {
                    val answer = answers.getJSONObject(i)
                    if (answer.optInt("type") == 1) {
                        val literal = answer.getString("data")
                        if (!literal.matches(Regex("[0-9.]+"))) continue
                        val address = InetAddress.getByName(literal)
                        val ttl = answer.optInt("TTL", 0).coerceIn(0, 300)
                        if (cache.size >= 256) cache.clear()
                        if (ttl > 0) cache[host] = Cached(address, System.nanoTime() + ttl * 1_000_000_000L)
                        return address
                    }
                }
            }
            throw UnknownHostException("DoH: no IPv4 address for $host")
        } finally {
            connection.disconnect()
        }
    }
    private data class Cached(val address: InetAddress, val expires: Long)
    private companion object { val cache = ConcurrentHashMap<String, Cached>() }
}
