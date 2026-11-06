package de.kleinanzeigenbot.app

import android.app.Activity
import android.app.AlertDialog

/** Zeigt nach einem Update einmal „Was ist neu“. Nach einer Neuinstallation nicht. */
object NeuigkeitenAnzeige {
    private fun alle(activity: Activity): List<Neuigkeit> =
        activity.assets.open("neuigkeiten.json").bufferedReader().use { Neuigkeiten.lesen(it.readText()) }

    fun nachUpdate(activity: Activity) {
        val speicher = Speicher(activity)
        val aktuell = BuildConfig.VERSION_NAME
        val gezeigt = speicher.neuigkeitenGezeigt
        speicher.neuigkeitenGezeigt = aktuell
        if (gezeigt == null || !Versionen.istNeuer(aktuell, gezeigt)) return
        val eintraege = Neuigkeiten.zwischen(alle(activity), gezeigt, aktuell)
        if (eintraege.isNotEmpty()) zeigen(activity, aktuell, eintraege)
    }

    fun aktuelle(activity: Activity) {
        val eintraege = alle(activity).sortedWith { a, b -> Versionen.vergleiche(b.version, a.version) }.take(3)
        if (eintraege.isNotEmpty()) zeigen(activity, eintraege.first().version, eintraege)
    }

    private fun zeigen(activity: Activity, version: String, eintraege: List<Neuigkeit>) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.neu_titel, version))
            .setMessage(Neuigkeiten.alsText(eintraege))
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
