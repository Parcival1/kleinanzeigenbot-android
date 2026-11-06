package de.kleinanzeigenbot.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.net.URLEncoder
import org.json.JSONObject

/**
 * Die Inventar-Oberfläche im WebView. Nativ ergänzt: Kamera für Foto-Uploads,
 * Drucken der Etiketten, Fehlerseite ohne WLAN, Update-Suche.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var fortschritt: ProgressBar
    private lateinit var wischen: SwipeRefreshLayout
    private lateinit var fehler: View
    private lateinit var fehlerText: TextView
    private lateinit var speicher: Speicher
    private lateinit var aktualisierung: Aktualisierung

    private var geladenerServer: String? = null
    private var fehlerBeimLaden = false
    private var dateiRueckruf: ValueCallback<Array<Uri>>? = null

    private val zurueck = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = web.goBack()
    }

    private val kamera = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { ergebnis ->
        dateiRueckruf?.onReceiveValue(KameraActivity.fotos(ergebnis.resultCode, ergebnis.data))
        dateiRueckruf = null
    }

    private val dateiwahl = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { ergebnis ->
        dateiRueckruf?.onReceiveValue(gewaehlteDateien(ergebnis.resultCode, ergebnis.data))
        dateiRueckruf = null
    }

    private val scanner = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { ergebnis ->
        val code = ScannerActivity.code(ergebnis.resultCode, ergebnis.data) ?: return@registerForActivityResult
        val server = speicher.server ?: return@registerForActivityResult
        web.loadUrl("$server/scan?code=" + URLEncoder.encode(code, "UTF-8"))
    }

    // „Suchen“ auf der Artikelseite: den gelesenen Text (oder null = abgebrochen) prüft die Seite selbst.
    private val pruefer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { ergebnis ->
        val code = ScannerActivity.code(ergebnis.resultCode, ergebnis.data)
        val wert = if (code == null) "null" else JSONObject.quote(code)
        web.evaluateJavascript("window.inventarGescannt && window.inventarGescannt($wert);", null)
    }

    private val einstellungen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Erster Start ohne Adresse und zurück gedrückt: App schließen statt leerer Seite.
        if (speicher.server == null) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kopf der Weboberfläche ist immer dunkel -> helle Symbole in der Statusleiste.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContentView(R.layout.activity_main)

        speicher = Speicher(this)
        aktualisierung = Aktualisierung(this)
        web = findViewById(R.id.web)
        fortschritt = findViewById(R.id.fortschritt)
        wischen = findViewById(R.id.wischen)
        wischen.setColorSchemeResources(R.color.akzent)
        wischen.setOnRefreshListener { neuLaden() }
        // Nur ganz oben auf der Seite: sonst scrollt Wischen nach unten ganz normal.
        wischen.setOnChildScrollUpCallback { _, _ -> web.scrollY > 0 }
        fehler = findViewById(R.id.fehler)
        fehlerText = findViewById(R.id.fehler_text)

        val oben = findViewById<View>(R.id.oben)
        val unten = findViewById<View>(R.id.unten)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.wurzel)) { wurzel, insets ->
            val leisten = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val tastatur = insets.getInsets(WindowInsetsCompat.Type.ime())
            oben.updateLayoutParams { height = leisten.top }
            unten.updateLayoutParams { height = maxOf(leisten.bottom, tastatur.bottom) }
            wurzel.updatePadding(left = leisten.left, right = leisten.right)
            WindowInsetsCompat.CONSUMED
        }

        findViewById<Button>(R.id.erneut).setOnClickListener { neuLaden() }
        findViewById<Button>(R.id.adresse).setOnClickListener { einstellungenOeffnen(erststart = false) }
        onBackPressedDispatcher.addCallback(this, zurueck)

        webEinrichten()

        val server = speicher.server
        if (server == null) {
            einstellungenOeffnen(erststart = true)
        } else if (savedInstanceState != null && web.restoreState(savedInstanceState) != null) {
            geladenerServer = server
        } else {
            laden(server)
        }
        NeuigkeitenAnzeige.nachUpdate(this)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        // Adresse in den Einstellungen geändert -> neu verbinden.
        val server = speicher.server
        if (server != null && server != geladenerServer) laden(server)
        aktualisierung.beimOeffnen()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        dateiRueckruf?.onReceiveValue(null)
        dateiRueckruf = null
        web.destroy()
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun webEinrichten() {
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            // target="_blank" (Etiketten, Fotos) im selben Fenster öffnen; zurück führt zurück.
            setSupportMultipleWindows(false)
            userAgentString = "$userAgentString InventarApp/${BuildConfig.VERSION_NAME}"
        }
        web.addJavascriptInterface(Bruecke(), "InventarApp")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val ziel = request.url.toString()
                val server = speicher.server ?: return false
                if (ServerAdresse.gehoertZu(server, ziel)) return false
                // Fremde Seiten (z. B. die Anzeige auf kleinanzeigen.de) im Browser öffnen.
                extern(request.url)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                fehlerBeimLaden = false
                fortschritt.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                fortschritt.visibility = View.GONE
                wischen.isRefreshing = false
                if (!fehlerBeimLaden) {
                    fehler.visibility = View.GONE
                    web.visibility = View.VISIBLE
                }
                // Drucken-Knöpfe der Etikettenseite auf den Android-Druckdienst lenken.
                view.evaluateJavascript("window.print = function () { InventarApp.drucken(); };", null)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                fehlerBeimLaden = true
                wischen.isRefreshing = false
                fehlerText.text = getString(R.string.fehler_text, speicher.server ?: "")
                web.visibility = View.INVISIBLE
                fehler.visibility = View.VISIBLE
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                zurueck.isEnabled = view.canGoBack()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                fortschritt.progress = newProgress
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                dateiRueckruf?.onReceiveValue(null)
                dateiRueckruf = callback
                val mehrere = params.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                val nurBilder = params.acceptTypes.any { it.startsWith("image") }
                try {
                    if (nurBilder) {
                        kamera.launch(Intent(this@MainActivity, KameraActivity::class.java)
                            .putExtra(KameraActivity.MEHRERE, mehrere))
                    } else {
                        dateiwahl.launch(params.createIntent().putExtra(Intent.EXTRA_ALLOW_MULTIPLE, mehrere))
                    }
                } catch (e: ActivityNotFoundException) {
                    dateiRueckruf?.onReceiveValue(null)
                    dateiRueckruf = null
                    return false
                }
                return true
            }
        }
    }

    private fun laden(server: String) {
        geladenerServer = server
        web.clearHistory()
        web.loadUrl("$server/")
    }

    private fun neuLaden() {
        val server = speicher.server ?: return
        val aktuell = web.url
        if (aktuell != null && ServerAdresse.gehoertZu(server, aktuell)) web.reload() else laden(server)
    }

    fun einstellungenOeffnen(erststart: Boolean) {
        einstellungen.launch(Intent(this, EinstellungenActivity::class.java)
            .putExtra(EinstellungenActivity.ERSTSTART, erststart))
    }

    private fun drucken() {
        val druck = getSystemService(PRINT_SERVICE) as PrintManager
        druck.print(getString(R.string.drucken_titel),
            web.createPrintDocumentAdapter(getString(R.string.drucken_titel)),
            PrintAttributes.Builder().build())
    }

    private fun extern(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.kein_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private fun gewaehlteDateien(code: Int, daten: Intent?): Array<Uri>? {
        if (code != RESULT_OK || daten == null) return null
        val clip = daten.clipData
        if (clip != null && clip.itemCount > 0) return Array(clip.itemCount) { clip.getItemAt(it).uri }
        return daten.data?.let { arrayOf(it) }
    }

    /** Für die Weboberfläche als window.InventarApp erreichbar. Nur Seiten des eigenen Servers werden geladen. */
    inner class Bruecke {
        @JavascriptInterface
        fun version(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface
        fun drucken() = runOnUiThread { this@MainActivity.drucken() }

        @JavascriptInterface
        fun einstellungen() = runOnUiThread { einstellungenOeffnen(erststart = false) }

        @JavascriptInterface
        fun scannen() = runOnUiThread { scanner.launch(Intent(this@MainActivity, ScannerActivity::class.java)) }

        @JavascriptInterface
        fun pruefen() = runOnUiThread {
            pruefer.launch(Intent(this@MainActivity, ScannerActivity::class.java).putExtra(ScannerActivity.SUCHEN, true))
        }

        /** „Suchen“ ab 1.5: Der Scanner kennt die gesuchte Nummer und meldet falsche Etiketten selbst. */
        @JavascriptInterface
        fun suchen(erwartet: String) = runOnUiThread {
            pruefer.launch(Intent(this@MainActivity, ScannerActivity::class.java)
                .putExtra(ScannerActivity.SUCHEN, true).putExtra(ScannerActivity.ERWARTET, erwartet))
        }

        @JavascriptInterface
        fun updatesSuchen() = runOnUiThread { aktualisierung.pruefen(vonHand = true) }
    }
}
