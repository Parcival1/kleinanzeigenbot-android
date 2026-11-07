package de.kleinanzeigenbot.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Server (fest eingebaut, nur zur Anzeige), Version, Updates, „Was ist neu“. */
class EinstellungenActivity : ComponentActivity() {

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

        findViewById<TextView>(R.id.server_anzeige).text = speicher.server

        findViewById<TextView>(R.id.version).text = getString(R.string.version_text, BuildConfig.VERSION_NAME)
        findViewById<TextView>(R.id.quelle).text = getString(R.string.quelle)
        findViewById<View>(R.id.updates).setOnClickListener { aktualisierung.pruefen(vonHand = true) }
        findViewById<View>(R.id.neuigkeiten).setOnClickListener { NeuigkeitenAnzeige.aktuelle(this) }
    }
}
