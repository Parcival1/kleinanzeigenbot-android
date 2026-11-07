package de.kleinanzeigenbot.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Update-Suche beim eigenen KleinanzeigenBot-Server (`/app/android/version.json`), nicht bei GitHub.
 *
 * Beim Öffnen und bei jeder Rückkehr in die App (höchstens einmal pro Minute)
 * wird nachgesehen; „Später“ blendet dieselbe Version 4 Stunden aus. Die APK
 * wird in den Cache geladen, gegen die SHA-256 des Servers geprüft und über den
 * Paketinstaller als Update über die bestehende App installiert (gleiches Paket,
 * gleiche Signatur – eine fremd signierte APK lehnt Android ohnehin ab).
 */
class Aktualisierung(private val activity: Activity) {

    companion object {
        private const val MINDESTABSTAND_MS = 60_000L
        private const val SPAETER_MS = 4 * 60 * 60 * 1000L
        private var letztePruefung = 0L
        private var dialogOffen = false
        // APK, die auf die Erlaubnis „Unbekannte Apps installieren“ wartet.
        private var wartet: File? = null
    }

    private val speicher = Speicher(activity)

    /** In onResume aufrufen. */
    fun beimOeffnen() {
        if (fortsetzen()) return
        val jetzt = SystemClock.elapsedRealtime()
        if (letztePruefung != 0L && jetzt - letztePruefung < MINDESTABSTAND_MS) return
        letztePruefung = jetzt
        pruefen(vonHand = false)
    }

    /** „Nach Updates suchen“: ignoriert „Später“ und den Mindestabstand. */
    fun pruefen(vonHand: Boolean) {
        if (dialogOffen) return
        thread(name = "update-suche") {
            val ergebnis = runCatching { neuestesRelease() }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                val release = ergebnis.getOrNull()
                val installiert = BuildConfig.VERSION_NAME
                when {
                    ergebnis.isFailure ->
                        if (vonHand) melden(activity.getString(R.string.update_keine_verbindung))
                    release != null && Versionen.istNeuer(release.version, installiert) ->
                        if (vonHand || !zurueckgestellt(release.version)) anbieten(release)
                    vonHand -> melden(activity.getString(R.string.update_aktuell, installiert))
                    else -> Unit
                }
            }
        }
    }

    private fun neuestesRelease(): Release? {
        val server = speicher.server ?: throw IllegalStateException("kein Server")
        val verbindung = URL("$server/app/android/version.json").openConnection() as HttpURLConnection
        verbindung.connectTimeout = 10_000
        verbindung.readTimeout = 15_000
        verbindung.setRequestProperty("Accept", "application/json")
        verbindung.setRequestProperty("User-Agent", "KleinanzeigenBot-App/${BuildConfig.VERSION_NAME}")
        try {
            // 404: Auf dem Server liegt (noch) keine App – kein Fehler, nur kein Update.
            if (verbindung.responseCode == 404) return null
            if (verbindung.responseCode != 200) throw IllegalStateException("HTTP ${verbindung.responseCode}")
            return Releases.auswaehlen(verbindung.inputStream.bufferedReader().use { it.readText() }, server)
        } finally {
            verbindung.disconnect()
        }
    }

    private fun zurueckgestellt(version: String): Boolean =
        speicher.spaeterVersion == version && System.currentTimeMillis() - speicher.spaeterZeit < SPAETER_MS

    private fun anbieten(release: Release) {
        dialogOffen = true
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_titel)
            .setMessage(activity.getString(R.string.update_text, release.version, BuildConfig.VERSION_NAME))
            .setPositiveButton(R.string.update_jetzt) { _, _ -> laden(release) }
            .setNegativeButton(R.string.update_spaeter) { _, _ ->
                speicher.spaeterVersion = release.version
                speicher.spaeterZeit = System.currentTimeMillis()
            }
            .setOnDismissListener { dialogOffen = false }
            .show()
    }

    private fun laden(release: Release) {
        val ansicht = activity.layoutInflater.inflate(R.layout.dialog_fortschritt, null)
        val balken = ansicht.findViewById<ProgressBar>(R.id.balken)
        val prozent = ansicht.findViewById<TextView>(R.id.prozent)
        val abgebrochen = AtomicBoolean(false)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.update_laedt)
            .setView(ansicht)
            .setCancelable(false)
            .setNegativeButton(R.string.abbrechen) { _, _ -> abgebrochen.set(true) }
            .show()

        thread(name = "update-download") {
            val ordner = File(activity.cacheDir, "updates")
            val ergebnis = runCatching {
                ordner.mkdirs()
                ordner.listFiles()?.forEach { it.delete() }
                val ziel = File(ordner, "kleinanzeigenbot-${release.version}.apk")
                val pruefsumme = MessageDigest.getInstance("SHA-256")
                val verbindung = URL(release.apkUrl).openConnection() as HttpURLConnection
                verbindung.connectTimeout = 15_000
                verbindung.readTimeout = 30_000
                verbindung.instanceFollowRedirects = true
                try {
                    if (verbindung.responseCode != 200) throw IllegalStateException("HTTP ${verbindung.responseCode}")
                    val gesamt = if (release.groesse > 0) release.groesse else verbindung.contentLengthLong
                    var geladen = 0L
                    var zuletzt = -1
                    verbindung.inputStream.use { ein ->
                        ziel.outputStream().use { aus ->
                            val puffer = ByteArray(64 * 1024)
                            while (true) {
                                if (abgebrochen.get()) throw InterruptedException()
                                val n = ein.read(puffer)
                                if (n < 0) break
                                aus.write(puffer, 0, n)
                                pruefsumme.update(puffer, 0, n)
                                geladen += n
                                val p = if (gesamt > 0) (geladen * 100 / gesamt).toInt() else 0
                                if (p != zuletzt) {
                                    zuletzt = p
                                    activity.runOnUiThread { balken.progress = p; prozent.text = "$p %" }
                                }
                            }
                        }
                    }
                } finally {
                    verbindung.disconnect()
                }
                // Abgebrochener oder verfälschter Download: nicht installieren.
                if (release.groesse > 0 && ziel.length() != release.groesse) throw IllegalStateException("Größe")
                if (ziel.length() < 100_000) throw IllegalStateException("zu klein")
                val ist = pruefsumme.digest().joinToString("") { "%02x".format(it) }
                if (ist != release.sha256) throw IllegalStateException("Prüfsumme")
                ziel
            }
            activity.runOnUiThread {
                if (dialog.isShowing) dialog.dismiss()
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                ergebnis.onSuccess { installieren(it) }
                    .onFailure { if (it !is InterruptedException) melden(activity.getString(R.string.update_fehler)) }
            }
        }
    }

    private fun installieren(apk: File) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.erlaubnis_titel)
                .setMessage(R.string.erlaubnis_text)
                .setPositiveButton(R.string.weiter) { _, _ ->
                    wartet = apk
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                    )
                }
                .setNegativeButton(R.string.abbrechen, null)
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.dateien", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            melden(activity.getString(R.string.update_fehler))
        }
    }

    /** Nach der Rückkehr aus den Android-Einstellungen automatisch weitermachen. */
    private fun fortsetzen(): Boolean {
        val apk = wartet ?: return false
        wartet = null
        if (activity.packageManager.canRequestPackageInstalls() && apk.exists()) {
            installieren(apk)
        } else {
            melden(activity.getString(R.string.erlaubnis_fehlt))
        }
        return true
    }

    private fun melden(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
}
