package de.kleinanzeigenbot.app

import android.content.Context

/** Gespeicherte Einstellungen der App (nur auf dem Gerät). */
class Speicher(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("inventar", Context.MODE_PRIVATE)

    var server: String?
        get() = prefs.getString("server", null)
        set(wert) = prefs.edit().putString("server", wert).apply()

    var blitz: Int
        get() = prefs.getInt("blitz", 0)
        set(wert) = prefs.edit().putInt("blitz", wert).apply()

    var neuigkeitenGezeigt: String?
        get() = prefs.getString("neuigkeiten_gezeigt", null)
        set(wert) = prefs.edit().putString("neuigkeiten_gezeigt", wert).apply()

    var spaeterVersion: String?
        get() = prefs.getString("spaeter_version", null)
        set(wert) = prefs.edit().putString("spaeter_version", wert).apply()

    var spaeterZeit: Long
        get() = prefs.getLong("spaeter_zeit", 0)
        set(wert) = prefs.edit().putLong("spaeter_zeit", wert).apply()
}
