package com.mytv.tvos

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL

enum class NetKind { WIFI, ETHERNET, OTHER, NONE }

/** YES = reachable, NO = a network is connected but nothing answers, CHECKING = not verified yet. */
enum class Internet { YES, NO, CHECKING }

data class NetInfo(val kind: NetKind, val internetCap: Boolean, val validated: Boolean)

data class NetState(val kind: NetKind, val internet: Internet, val ssid: String?, val ssidHidden: Boolean)

/**
 * Network status for the Wi-Fi tile.
 *
 * Why the old "connected, no network" message could be wrong: Android marks a network VALIDATED only after its own probe
 * (a request to Google's connectivity-check server) succeeds, and many TV boxes either never run it, run it late, block it
 * (region, DNS, ad-block) or report no "active" network at all, while the TV itself reaches the internet without trouble.
 * So VALIDATED is used as proof of internet when present, but its absence is never used as proof of the opposite: an
 * independent probe against several hosts (including the ones this launcher really needs) decides, and any successful
 * weather / prayer-time download counts as proof as well.
 */
object Net {

    private const val EVIDENCE_MS = 10 * 60_000L
    private const val PROBE_GAP_MS = 30_000L

    @Volatile var state = NetState(NetKind.NONE, Internet.CHECKING, null, false)
        private set

    @Volatile private var lastEvidence = 0L
    @Volatile private var probeResult: Boolean? = null
    @Volatile private var probeAt = 0L
    @Volatile private var probing = false

    private val main = Handler(Looper.getMainLooper())
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()
    private var cm: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var appCtx: Context? = null

    val PROBES = listOf(
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
        "https://api.open-meteo.com/",
        "https://api.aladhan.com/"
    )

    /** Pure decision, kept separate so it can be tested without a device. */
    fun decide(nets: List<NetInfo>, probe: Boolean?, evidenceRecent: Boolean): Pair<NetKind, Internet> {
        val cands = nets.filter { it.internetCap }
        val kind = when {
            cands.any { it.kind == NetKind.WIFI } -> NetKind.WIFI
            cands.any { it.kind == NetKind.ETHERNET } -> NetKind.ETHERNET
            cands.any { it.kind == NetKind.OTHER } -> NetKind.OTHER
            evidenceRecent -> NetKind.OTHER // some boxes report no network at all while the internet works
            else -> NetKind.NONE
        }
        if (kind == NetKind.NONE) return NetKind.NONE to Internet.NO
        val internet = when {
            cands.any { it.validated } -> Internet.YES
            evidenceRecent -> Internet.YES
            probe == true -> Internet.YES
            probe == false -> Internet.NO
            else -> Internet.CHECKING
        }
        return kind to internet
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    /** A download that really worked (weather, prayer times): the strongest proof there is. */
    fun evidence() {
        lastEvidence = System.currentTimeMillis()
        probeResult = true
        appCtx?.let { refresh(it) }
    }

    @Suppress("DEPRECATION")
    fun start(context: Context) {
        val ctx = context.applicationContext
        appCtx = ctx
        if (callback != null) { refresh(ctx); return }
        val c = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        cm = c
        val cb: ConnectivityManager.NetworkCallback = if (Build.VERSION.SDK_INT >= 31) Cb31(ctx) else CbOld(ctx)
        callback = cb
        try {
            c.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb)
        } catch (_: Exception) { callback = null }
        refresh(ctx)
    }

    fun stop() {
        try { callback?.let { cm?.unregisterNetworkCallback(it) } } catch (_: Exception) {}
        callback = null
    }

    private class CbOld(val ctx: Context) : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { probeResult = null; refresh(ctx) }
        override fun onLost(network: Network) { probeResult = null; refresh(ctx) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { refresh(ctx) }
    }

    @androidx.annotation.RequiresApi(31)
    private class Cb31(val ctx: Context) : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
        override fun onAvailable(network: Network) { probeResult = null; refresh(ctx) }
        override fun onLost(network: Network) { probeResult = null; refresh(ctx) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { refresh(ctx) }
    }

    @Suppress("DEPRECATION")
    fun refresh(context: Context) {
        val ctx = context.applicationContext
        val c = (cm ?: ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager) ?: return
        val infos = ArrayList<NetInfo>()
        var ssid: String? = null
        try {
            for (n in c.allNetworks) {
                val caps = c.getNetworkCapabilities(n) ?: continue
                val kind = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.WIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetKind.ETHERNET
                    else -> NetKind.OTHER
                }
                infos.add(NetInfo(kind, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET), caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)))
                if (kind == NetKind.WIFI && ssid == null && Build.VERSION.SDK_INT >= 29) {
                    (caps.transportInfo as? WifiInfo)?.let { ssid = clean(it.ssid) }
                }
            }
        } catch (_: Exception) {}
        val wifiUp = infos.any { it.kind == NetKind.WIFI && it.internetCap }
        if (wifiUp && ssid == null) {
            ssid = try { clean((ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo?.ssid) } catch (_: Exception) { null }
        }
        val recent = System.currentTimeMillis() - lastEvidence < EVIDENCE_MS
        val (kind, internet) = decide(infos, probeResult, recent)
        val next = NetState(kind, internet, if (kind == NetKind.WIFI) ssid else null, kind == NetKind.WIFI && ssid == null)
        val changed = next != state
        state = next
        if (internet == Internet.CHECKING) probe(ctx)
        if (changed) main.post { for (l in listeners) l() }
    }

    /** "<unknown ssid>", empty and quoted names: only a real name is ever shown. */
    fun clean(raw: String?): String? {
        val s = raw?.trim()?.trim('"')?.trim() ?: return null
        if (s.isEmpty() || s.equals("<unknown ssid>", true) || s == "0x") return null
        return s
    }

    private fun probe(ctx: Context) {
        val now = System.currentTimeMillis()
        if (probing || now - probeAt < PROBE_GAP_MS) return
        probing = true
        probeAt = now
        Thread {
            var ok = false
            for (u in PROBES) {
                if (reachable(u)) { ok = true; break }
            }
            probeResult = ok
            probing = false
            refresh(ctx)
        }.start()
    }

    private fun reachable(url: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 4_000
            conn.readTimeout = 4_000
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.responseCode in 100..499 // any HTTP answer proves a working path to the internet
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    /** Title and detail line of the Wi-Fi tile. */
    fun describe(s: NetState): Pair<String, String> {
        val net = when (s.internet) { Internet.YES -> "Internet OK"; Internet.NO -> "Connected, no internet"; Internet.CHECKING -> "Checking internet…" }
        return when (s.kind) {
            NetKind.NONE -> "Wi-Fi" to "Not connected"
            NetKind.WIFI -> "Wi-Fi" to ((s.ssid ?: "Connected (network name hidden)") + "\n" + net)
            NetKind.ETHERNET -> "Ethernet" to ("Connected\n" + net)
            NetKind.OTHER -> "Network" to ("Connected\n" + net)
        }
    }
}
