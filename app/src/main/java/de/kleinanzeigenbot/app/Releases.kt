package de.kleinanzeigenbot.app

import org.json.JSONArray

data class Release(val version: String, val apkUrl: String, val groesse: Long, val seite: String)

/** Wählt aus der Antwort von /repos/<repo>/releases das neueste veröffentlichte Release mit APK. */
object Releases {
    fun auswaehlen(json: String): Release? {
        val liste = JSONArray(json)
        var bestes: Release? = null
        for (i in 0 until liste.length()) {
            val eintrag = liste.getJSONObject(i)
            // Entwürfe sind ungetestet und erreichen nie Nutzer.
            if (eintrag.optBoolean("draft", true)) continue
            val anhaenge = eintrag.optJSONArray("assets") ?: continue
            var apk: Release? = null
            for (j in 0 until anhaenge.length()) {
                val anhang = anhaenge.getJSONObject(j)
                if (anhang.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = Release(
                        version = eintrag.optString("tag_name").removePrefix("v"),
                        apkUrl = anhang.optString("browser_download_url"),
                        groesse = anhang.optLong("size", 0),
                        seite = eintrag.optString("html_url"),
                    )
                    break
                }
            }
            if (apk == null || apk.version.isEmpty() || apk.apkUrl.isEmpty()) continue
            if (bestes == null || Versionen.istNeuer(apk.version, bestes.version)) bestes = apk
        }
        return bestes
    }
}
