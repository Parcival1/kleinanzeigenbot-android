package de.kleinanzeigenbot.app

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Serveradresse (beim ersten Start Pflicht), Version, Updates, „Was ist neu“. */
class EinstellungenActivity : ComponentActivity() {

    companion object {
        const val ERSTSTART = "erststart"
    }

    private lateinit var eingabe: EditText
    private lateinit var meldung: TextView
    private lateinit var verbinden: Button
    private lateinit var speicher: Speicher
    private lateinit var aktualisierung: Aktualisierung

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_einstellungen)
        speicher = Speicher(this)
        aktualisierung = Aktualisierung(this)

        val wurzel = findViewById<View>(R.id.wurzel)
        ViewCompat.setOnApplyWindowInsetsListener(wurzel) { v, insets ->
            val r = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.updatePadding(left = r.left, top = r.top, right = r.right, bottom = r.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val erststart = intent.getBooleanExtra(ERSTSTART, false) || speicher.server == null
        findViewById<TextView>(R.id.titel).setText(if (erststart) R.string.willkommen else R.string.einstellungen)
        findViewById<View>(R.id.willkommen).visibility = if (erststart) View.VISIBLE else View.GONE

        eingabe = findViewById(R.id.eingabe)
        meldung = findViewById(R.id.meldung)
        verbinden = findViewById(R.id.verbinden)
        eingabe.setText(speicher.server?.removePrefix("http://") ?: "")
        eingabe.setOnEditorActionListener { _, aktion, ereignis ->
            // „Los“ auf der Bildschirmtastatur oder Enter auf einer echten Tastatur.
            val enter = ereignis?.keyCode == KeyEvent.KEYCODE_ENTER
            if (aktion != EditorInfo.IME_ACTION_GO && !enter) return@setOnEditorActionListener false
            if (ereignis == null || ereignis.action == KeyEvent.ACTION_DOWN) pruefenUndSpeichern()
            true
        }
        verbinden.setOnClickListener { pruefenUndSpeichern() }

        findViewById<TextView>(R.id.version).text = getString(R.string.version_text, BuildConfig.VERSION_NAME)
        findViewById<TextView>(R.id.quelle).text = getString(R.string.quelle)
        findViewById<View>(R.id.updates).setOnClickListener { aktualisierung.pruefen(vonHand = true) }
        findViewById<View>(R.id.neuigkeiten).setOnClickListener { NeuigkeitenAnzeige.aktuelle(this) }

        if (erststart) eingabe.requestFocus()
    }

    private fun pruefenUndSpeichern() {
        val adresse = ServerAdresse.normalisieren(eingabe.text.toString())
        if (adresse == null) {
            fehler(getString(R.string.adresse_ungueltig))
            return
        }
        meldung.visibility = View.GONE
        verbinden.isEnabled = false
        verbinden.setText(R.string.pruefe)
        thread(name = "server-pruefung") {
            val erreichbar = istInventarServer(adresse)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                verbinden.isEnabled = true
                verbinden.setText(R.string.verbinden)
                if (erreichbar) {
                    speichern(adresse)
                } else {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.adresse_keine_antwort, adresse))
                        .setPositiveButton(R.string.trotzdem_speichern) { _, _ -> speichern(adresse) }
                        .setNegativeButton(R.string.abbrechen, null)
                        .show()
                }
            }
        }
    }

    private fun istInventarServer(adresse: String): Boolean = try {
        val verbindung = URL("$adresse/health").openConnection() as HttpURLConnection
        verbindung.connectTimeout = 5_000
        verbindung.readTimeout = 5_000
        try {
            verbindung.responseCode == 200 &&
                verbindung.inputStream.bufferedReader().use { it.readText() }.contains("\"ok\"")
        } finally {
            verbindung.disconnect()
        }
    } catch (e: Exception) {
        false
    }

    private fun speichern(adresse: String) {
        speicher.server = adresse
        setResult(RESULT_OK)
        finish()
    }

    private fun fehler(text: String) {
        meldung.text = text
        meldung.visibility = View.VISIBLE
    }
}
