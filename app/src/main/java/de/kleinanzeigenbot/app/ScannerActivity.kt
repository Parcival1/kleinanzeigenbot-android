package de.kleinanzeigenbot.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Liest das Etikett (Code 128 mit der Inventarnummer) und gibt den Text an
 * die MainActivity zurück, die dann <server>/scan?code=… öffnet. Erkennung
 * mit ML Kit auf dem Gerät; das Modell steckt in der App.
 */
class ScannerActivity : ComponentActivity() {

    companion object {
        private const val CODE = "code"

        /** Aufruf über „Suchen“ auf der Artikelseite: nur der Titel ändert sich. */
        const val SUCHEN = "suchen"

        /**
         * Gesuchte Inventarnummer („Suchen“ ab 1.5): Falsche Etiketten meldet der Scanner rot und sucht
         * weiter; zurück kommt nur das richtige (oder eine eingetippte Nummer, die prüft die Seite).
         */
        const val ERWARTET = "erwartet"

        /** Gleiche Nummer? Groß-/Kleinschreibung und Leerraum zählen nicht. */
        fun passt(gelesen: String, erwartet: String) = gelesen.trim().equals(erwartet.trim(), ignoreCase = true)

        /** Gelesener Text oder null (abgebrochen). */
        fun code(ergebnis: Int, daten: Intent?): String? =
            if (ergebnis == Activity.RESULT_OK) daten?.getStringExtra(CODE)?.trim()?.takeIf { it.isNotEmpty() } else null
    }

    private lateinit var vorschau: PreviewView
    private lateinit var lampe: ImageButton
    private lateinit var auswertung: ExecutorService
    private var leser: BarcodeScanner? = null
    private var kamera: Camera? = null
    private var lampeAn = false
    private var fertig = false
    private var erwartet: String? = null
    private var letzterFalscher: String? = null
    private val ton: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
    } catch (e: RuntimeException) {
        null // kein Ton verfügbar – die Vibration reicht
    }

    private val erlaubnis = registerForActivityResult(ActivityResultContracts.RequestPermission()) { erteilt ->
        if (erteilt) starten() else findViewById<View>(R.id.hinweis).visibility = View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)
        vorschau = findViewById(R.id.vorschau)
        lampe = findViewById(R.id.lampe)
        auswertung = Executors.newSingleThreadExecutor()

        val oben = findViewById<View>(R.id.oben)
        val unten = findViewById<View>(R.id.unten)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val r = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            oben.updatePadding(top = r.top + dp(6), left = r.left + dp(8), right = r.right + dp(8))
            unten.updatePadding(bottom = r.bottom + dp(16), left = r.left + dp(16), right = r.right + dp(16))
            insets
        }

        if (intent.getBooleanExtra(SUCHEN, false)) findViewById<TextView>(R.id.titel).setText(R.string.scan_suchen_titel)
        erwartet = intent.getStringExtra(ERWARTET)?.trim()?.takeIf { it.isNotEmpty() }
        if (erwartet != null) findViewById<TextView>(R.id.hinweis_text).setText(R.string.suchen_hinweis)
        findViewById<View>(R.id.schliessen).setOnClickListener { finish() }
        findViewById<View>(R.id.eintippen).setOnClickListener { eintippen() }
        findViewById<View>(R.id.hinweis_knopf).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        lampe.setOnClickListener {
            lampeAn = !lampeAn
            kamera?.cameraControl?.enableTorch(lampeAn)
            lampe.alpha = if (lampeAn) 1f else 0.7f
        }
        lampe.alpha = 0.7f

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            starten()
        } else {
            erlaubnis.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        leser?.close()
        ton?.release()
        auswertung.shutdown()
        super.onDestroy()
    }

    private fun starten() {
        findViewById<View>(R.id.hinweis).visibility = View.GONE
        val zukunft = ProcessCameraProvider.getInstance(this)
        zukunft.addListener({
            if (isFinishing || isDestroyed) return@addListener
            try {
                val anbieter = zukunft.get()
                val optionen = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_DATA_MATRIX)
                    .build()
                val scanner = BarcodeScanning.getClient(optionen).also { leser = it }
                val bild = Preview.Builder().build().also { it.setSurfaceProvider(vorschau.surfaceProvider) }
                // Schmale Etiketten brauchen Auflösung: 1280×720 reicht ML Kit und bleibt flüssig.
                val analyse = ImageAnalysis.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analyse.setAnalyzer(auswertung, MlKitAnalyzer(listOf(scanner),
                    ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, ContextCompat.getMainExecutor(this)) { ergebnis ->
                    val text = ergebnis.getValue(scanner)?.firstNotNullOfOrNull { it.rawValue?.trim()?.ifEmpty { null } }
                    if (text != null) gefunden(text)
                })
                anbieter.unbindAll()
                kamera = anbieter.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, bild, analyse)
                lampe.visibility = if (kamera?.cameraInfo?.hasFlashUnit() == true) View.VISIBLE else View.INVISIBLE
            } catch (e: Exception) {
                Toast.makeText(this, R.string.kamera_fehler, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun gefunden(text: String) {
        if (fertig) return
        val gesucht = erwartet
        if (gesucht != null && !passt(text, gesucht)) {
            falsch(text, gesucht)
            return
        }
        fertig = true
        vorschau.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        ton?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        zurueck(text)
    }

    /** Rote Meldung im Kamerabild; die Kamera läuft weiter. Dasselbe Etikett meldet sich nur einmal. */
    private fun falsch(text: String, gesucht: String) {
        if (text == letzterFalscher) return
        letzterFalscher = text
        findViewById<TextView>(R.id.falsch_text).text = getString(R.string.suchen_falsch_text, text, gesucht)
        findViewById<View>(R.id.falsch).visibility = View.VISIBLE
        findViewById<View>(R.id.rahmen).setBackgroundResource(R.drawable.scan_rahmen_falsch)
        vorschau.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
        ton?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
    }

    private fun eintippen() {
        val feld = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            hint = "I-00012"
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.scan_eintippen_titel)
            .setView(feld)
            .setPositiveButton(R.string.scan_oeffnen) { _, _ -> feld.text.toString().trim().takeIf { it.isNotEmpty() }?.let(::zurueck) }
            .setNegativeButton(R.string.abbrechen, null)
            .create()
        feld.setOnEditorActionListener { _, aktion, ereignis ->
            // „Los“ auf der Bildschirmtastatur oder Enter auf einer echten Tastatur.
            if (aktion != EditorInfo.IME_ACTION_GO && ereignis?.keyCode != KeyEvent.KEYCODE_ENTER) {
                return@setOnEditorActionListener false
            }
            if (ereignis == null || ereignis.action == KeyEvent.ACTION_DOWN) {
                feld.text.toString().trim().takeIf { it.isNotEmpty() }?.let { dialog.dismiss(); zurueck(it) }
            }
            true
        }
        dialog.show()
        feld.setPadding(dp(20), dp(16), dp(20), dp(16))
        feld.requestFocus()
    }

    private fun zurueck(text: String) {
        fertig = true
        setResult(RESULT_OK, Intent().putExtra(CODE, text))
        finish()
    }

    private fun dp(wert: Int) = (wert * resources.displayMetrics.density).toInt()
}
