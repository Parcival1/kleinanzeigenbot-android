package de.kleinanzeigenbot.app

import org.json.JSONObject

data class Release(val version: String, val apkUrl: String, val groesse: Long, val sha256: String)

/** Liest `/app/android/version.json` des eigenen KleinanzeigenBot-Servers. */
object Releases {
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    fun auswaehlen(json: String, server: String): Release? {
        val eintrag = try { JSONObject(json) } catch (e: Exception) { return null }
        val version = eintrag.optString("version")
        val apk = eintrag.optString("apk")
        val sha256 = eintrag.optString("sha256").lowercase()
        // Nur ein Pfad auf demselben Server, nie eine fremde Adresse.
        if (version.isEmpty() || !apk.startsWith("/") || apk.startsWith("//") || !SHA256.matches(sha256)) return null
        return Release(version, server.trimEnd('/') + apk, eintrag.optLong("groesse", 0), sha256)
    }
}
