package de.kleinanzeigenbot.app

import java.net.URI

/** Serveradresse, wie sie eingetippt wird, in eine vollständige Basis-URL wandeln. */
object ServerAdresse {
    fun normalisieren(eingabe: String): String? {
        var text = eingabe.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        if (!text.contains("://")) text = "http://$text"
        val uri = try { URI(text) } catch (e: Exception) { return null }
        val schema = uri.scheme?.lowercase() ?: return null
        if (schema != "http" && schema != "https") return null
        val host = uri.host ?: return null
        if (host.isEmpty()) return null
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val pfad = (uri.rawPath ?: "").trimEnd('/')
        return "$schema://$host$port$pfad"
    }

    /** Gehört [url] zum Server [basis]? Gleiches Schema, gleicher Host, gleicher Port. */
    fun gehoertZu(basis: String, url: String): Boolean {
        val a = try { URI(basis) } catch (e: Exception) { return false }
        val b = try { URI(url) } catch (e: Exception) { return false }
        if (a.scheme == null || b.scheme == null || a.host == null || b.host == null) return false
        return a.scheme.equals(b.scheme, ignoreCase = true) &&
            a.host.equals(b.host, ignoreCase = true) &&
            port(a) == port(b)
    }

    private fun port(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }
}
