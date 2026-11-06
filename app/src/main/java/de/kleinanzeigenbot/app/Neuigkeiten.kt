package de.kleinanzeigenbot.app

import org.json.JSONArray

data class Neuigkeit(val version: String, val datum: String, val punkte: List<String>)

/** „Was ist neu“ aus assets/neuigkeiten.json (neueste Version oben). */
object Neuigkeiten {
    fun lesen(json: String): List<Neuigkeit> {
        val liste = JSONArray(json)
        return (0 until liste.length()).map { i ->
            val eintrag = liste.getJSONObject(i)
            val punkte = eintrag.optJSONArray("punkte") ?: JSONArray()
            Neuigkeit(
                version = eintrag.getString("version"),
                datum = eintrag.optString("datum"),
                punkte = (0 until punkte.length()).map { punkte.getString(it) },
            )
        }
    }

    /** Einträge nach [seit] bis einschließlich [bis], neueste zuerst. */
    fun zwischen(alle: List<Neuigkeit>, seit: String, bis: String): List<Neuigkeit> =
        alle.filter { Versionen.istNeuer(it.version, seit) && !Versionen.istNeuer(it.version, bis) }
            .sortedWith { a, b -> Versionen.vergleiche(b.version, a.version) }

    fun alsText(eintraege: List<Neuigkeit>): String =
        eintraege.joinToString("\n\n") { e ->
            val kopf = if (eintraege.size > 1) "Version ${e.version}\n" else ""
            kopf + e.punkte.joinToString("\n") { "• $it" }
        }
}
