package de.kleinanzeigenbot.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.io.File
import java.util.concurrent.Executors

/**
 * Kamera für kleine Bildschirme: Vorschau über die ganze Fläche, großer
 * Auslöser unten in Daumenhöhe, mehrere Fotos am Stück, Vorschaubilder zum
 * Verwerfen, „Fertig“ gibt alle Fotos an das Upload-Feld der Weboberfläche.
 */
class KameraActivity : ComponentActivity() {

    companion object {
        const val MEHRERE = "mehrere"
        private const val FOTOS = "fotos"
        private const val ALTER_MS = 60 * 60 * 1000L

        /** Ergebnis für den Datei-Rückruf des WebViews; null = abgebrochen. */
        fun fotos(code: Int, daten: Intent?): Array<Uri>? {
            if (code != Activity.RESULT_OK || daten == null) return null
            val liste = IntentCompat.getParcelableArrayListExtra(daten, FOTOS, Uri::class.java) ?: return null
            return liste.toTypedArray().takeIf { it.isNotEmpty() }
        }

        private val BLITZ = intArrayOf(ImageCapture.FLASH_MODE_AUTO, ImageCapture.FLASH_MODE_ON, ImageCapture.FLASH_MODE_OFF)
    }

    private lateinit var vorschau: PreviewView
    private lateinit var zaehler: TextView
    private lateinit var fertig: Button
    private lateinit var ausloeser: ImageButton
    private lateinit var blitzKnopf: ImageButton
    private lateinit var leiste: LinearLayout
    private lateinit var leisteRahmen: HorizontalScrollView
    private lateinit var blende: View
    private lateinit var fokus: View
    private lateinit var hinweis: View
    private lateinit var speicher: Speicher

    private val fotos = mutableListOf<Uri>()
    private val dateien = mutableMapOf<Uri, File>()
    private val arbeiter = Executors.newSingleThreadExecutor()
    private val ton = MediaActionSound()
    private var mehrere = true
    private var aufnahme: ImageCapture? = null
    private var kameraObjekt: Camera? = null
    private var laufend = 0
    private var blitzIndex = 0
    private lateinit var ordner: File
    private lateinit var drehung: OrientationEventListener

    private val erlaubnis = registerForActivityResult(ActivityResultContracts.RequestPermission()) { erteilt ->
        if (erteilt) starten() else hinweisZeigen(getString(R.string.kamera_erlaubnis_fehlt), zuEinstellungen = true)
    }

    private val galerie = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        uris.forEach { hinzufuegen(it, null) }
        if (!mehrere) abschliessen()
    }

    private val galerieEinzeln = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            hinzufuegen(uri, null)
            abschliessen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContentView(R.layout.activity_kamera)
        speicher = Speicher(this)
        mehrere = intent.getBooleanExtra(MEHRERE, true)

        vorschau = findViewById(R.id.vorschau)
        zaehler = findViewById(R.id.zaehler)
        fertig = findViewById(R.id.fertig)
        ausloeser = findViewById(R.id.ausloeser)
        blitzKnopf = findViewById(R.id.blitz)
        leiste = findViewById(R.id.leiste)
        leisteRahmen = findViewById(R.id.leiste_rahmen)
        blende = findViewById(R.id.blende)
        fokus = findViewById(R.id.fokus)
        hinweis = findViewById(R.id.hinweis)

        val oben = findViewById<View>(R.id.oben)
        val unten = findViewById<View>(R.id.unten)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val r = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            oben.updatePadding(top = r.top + dp(6), left = r.left + dp(8), right = r.right + dp(8))
            unten.updatePadding(bottom = r.bottom + dp(12), left = r.left, right = r.right)
            insets
        }

        // Vorschau zeigt genau den Bildausschnitt, der aufgenommen wird (4:3).
        vorschau.scaleType = PreviewView.ScaleType.FIT_CENTER

        ordner = File(cacheDir, "fotos").apply { mkdirs() }
        aufraeumen()

        blitzIndex = speicher.blitz.coerceIn(0, BLITZ.size - 1)
        blitzAnzeigen()
        blitzKnopf.setOnClickListener {
            blitzIndex = (blitzIndex + 1) % BLITZ.size
            speicher.blitz = blitzIndex
            aufnahme?.flashMode = BLITZ[blitzIndex]
            blitzAnzeigen()
            Toast.makeText(this, blitzKnopf.contentDescription, Toast.LENGTH_SHORT).show()
        }
        ausloeser.setOnClickListener { ausloesen() }
        fertig.setOnClickListener { abschliessen() }
        findViewById<View>(R.id.schliessen).setOnClickListener { schliessen() }
        findViewById<View>(R.id.galerie).setOnClickListener {
            val nurBilder = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (mehrere) galerie.launch(nurBilder) else galerieEinzeln.launch(nurBilder)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = schliessen()
        })

        gestenEinrichten()
        drehung = object : OrientationEventListener(this) {
            override fun onOrientationChanged(grad: Int) {
                if (grad == ORIENTATION_UNKNOWN) return
                // Die Ansicht bleibt hochkant; das Foto bekommt trotzdem die richtige Lage.
                aufnahme?.targetRotation = when (grad) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        ton.load(MediaActionSound.SHUTTER_CLICK)
        anzeigeAktualisieren()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            starten()
        } else {
            erlaubnis.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onStart() {
        super.onStart()
        if (drehung.canDetectOrientation()) drehung.enable()
    }

    override fun onStop() {
        drehung.disable()
        super.onStop()
    }

    override fun onDestroy() {
        arbeiter.shutdown()
        ton.release()
        super.onDestroy()
    }

    private fun starten() {
        hinweis.visibility = View.GONE
        val zukunft = ProcessCameraProvider.getInstance(this)
        zukunft.addListener({
            try {
                val anbieter = zukunft.get()
                val seitenverhaeltnis = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                val bildVorschau = Preview.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(seitenverhaeltnis).build())
                    .build()
                bildVorschau.setSurfaceProvider(vorschau.surfaceProvider)
                // Der Server verkleinert ohnehin auf 1600 px; mehr kostet nur Zeit beim Hochladen.
                val foto = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(BLITZ[blitzIndex])
                    .setJpegQuality(90)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(seitenverhaeltnis)
                            .setResolutionStrategy(ResolutionStrategy(Size(2048, 1536),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build()
                    )
                    .build()
                anbieter.unbindAll()
                kameraObjekt = anbieter.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, bildVorschau, foto)
                aufnahme = foto
                ausloeser.isEnabled = true
            } catch (e: Exception) {
                ausloeser.isEnabled = false
                hinweisZeigen(getString(R.string.kamera_fehler), zuEinstellungen = false)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun ausloesen() {
        val foto = aufnahme ?: return
        if (laufend >= 3) return
        laufend++
        ausloeser.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        ton.play(MediaActionSound.SHUTTER_CLICK)
        blende.alpha = 0.7f
        blende.animate().alpha(0f).setDuration(180).start()

        val datei = File(ordner, "foto_${System.currentTimeMillis()}.jpg")
        foto.takePicture(ImageCapture.OutputFileOptions.Builder(datei).build(), ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(ergebnis: ImageCapture.OutputFileResults) {
                    laufend--
                    hinzufuegen(FileProvider.getUriForFile(this@KameraActivity, "$packageName.dateien", datei), datei)
                    if (!mehrere) abschliessen()
                }

                override fun onError(fehler: ImageCaptureException) {
                    laufend--
                    datei.delete()
                    Toast.makeText(this@KameraActivity, R.string.foto_fehler, Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun hinzufuegen(uri: Uri, datei: File?) {
        fotos.add(uri)
        if (datei != null) dateien[uri] = datei
        val bild = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(8) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(this@KameraActivity, R.drawable.vorschau_rahmen)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            contentDescription = getString(R.string.foto_verwerfen)
            setOnClickListener { verwerfenFragen(uri, this) }
        }
        leiste.addView(bild)
        leisteRahmen.post { leisteRahmen.fullScroll(View.FOCUS_RIGHT) }
        anzeigeAktualisieren()
        arbeiter.execute {
            val klein = runCatching { miniatur(uri) }.getOrNull()
            runOnUiThread { if (klein != null) bild.setImageBitmap(klein) }
        }
    }

    private fun verwerfenFragen(uri: Uri, bild: View) {
        AlertDialog.Builder(this)
            .setMessage(R.string.foto_verwerfen)
            .setPositiveButton(R.string.verwerfen) { _, _ ->
                fotos.remove(uri)
                dateien.remove(uri)?.delete()
                leiste.removeView(bild)
                anzeigeAktualisieren()
            }
            .setNegativeButton(R.string.behalten, null)
            .show()
    }

    private fun anzeigeAktualisieren() {
        val n = fotos.size
        zaehler.text = when (n) {
            0 -> getString(R.string.keine_fotos)
            1 -> getString(R.string.foto_eins)
            else -> getString(R.string.fotos_anzahl, n)
        }
        fertig.isEnabled = n > 0
        fertig.text = if (n > 0) getString(R.string.fertig_anzahl, n) else getString(R.string.fertig)
        leisteRahmen.visibility = if (n > 0) View.VISIBLE else View.GONE
    }

    private fun abschliessen() {
        if (fotos.isEmpty()) return
        setResult(Activity.RESULT_OK, Intent().putParcelableArrayListExtra(FOTOS, ArrayList(fotos)))
        finish()
    }

    private fun schliessen() {
        if (fotos.isEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.fotos_uebernehmen_titel)
            .setMessage(getString(R.string.fotos_uebernehmen_text, fotos.size))
            .setPositiveButton(R.string.uebernehmen) { _, _ -> abschliessen() }
            .setNegativeButton(R.string.alle_verwerfen) { _, _ ->
                dateien.values.forEach { it.delete() }
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .setNeutralButton(R.string.abbrechen, null)
            .show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun gestenEinrichten() {
        val zoom = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val kamera = kameraObjekt ?: return false
                val jetzt = kamera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                kamera.cameraControl.setZoomRatio(jetzt * detector.scaleFactor)
                return true
            }
        })
        vorschau.setOnTouchListener { ansicht, ereignis ->
            zoom.onTouchEvent(ereignis)
            if (ereignis.action == MotionEvent.ACTION_UP && !zoom.isInProgress && ereignis.pointerCount == 1) {
                scharfstellen(ereignis.x, ereignis.y)
                ansicht.performClick()
            }
            true
        }
    }

    private fun scharfstellen(x: Float, y: Float) {
        val kamera = kameraObjekt ?: return
        val punkt = vorschau.meteringPointFactory.createPoint(x, y)
        kamera.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(punkt).build())
        fokus.x = x - fokus.width / 2f
        fokus.y = y - fokus.height / 2f
        fokus.animate().cancel()
        fokus.alpha = 1f
        fokus.scaleX = 1.3f
        fokus.scaleY = 1.3f
        fokus.animate().scaleX(1f).scaleY(1f).setDuration(200).withEndAction {
            fokus.animate().alpha(0f).setStartDelay(600).setDuration(300).start()
        }.start()
    }

    private fun blitzAnzeigen() {
        val (bild, text) = when (BLITZ[blitzIndex]) {
            ImageCapture.FLASH_MODE_ON -> R.drawable.ic_blitz_an to R.string.blitz_an
            ImageCapture.FLASH_MODE_OFF -> R.drawable.ic_blitz_aus to R.string.blitz_aus
            else -> R.drawable.ic_blitz_auto to R.string.blitz_auto
        }
        blitzKnopf.setImageResource(bild)
        blitzKnopf.contentDescription = getString(text)
    }

    private fun hinweisZeigen(text: String, zuEinstellungen: Boolean) {
        hinweis.visibility = View.VISIBLE
        findViewById<TextView>(R.id.hinweis_text).text = text
        val knopf = findViewById<Button>(R.id.hinweis_knopf)
        knopf.visibility = if (zuEinstellungen) View.VISIBLE else View.GONE
        knopf.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        ausloeser.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        // Erlaubnis in den Android-Einstellungen nachträglich erteilt.
        if (aufnahme == null && hinweis.visibility == View.VISIBLE &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) starten()
    }

    /** Fotos früherer Aufnahmen entfernen; jüngere können noch im Upload sein. */
    private fun aufraeumen() {
        val grenze = System.currentTimeMillis() - ALTER_MS
        ordner.listFiles()?.filter { it.lastModified() < grenze }?.forEach { it.delete() }
    }

    private fun miniatur(uri: Uri): Bitmap? {
        val ziel = dp(56) * 2
        val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, grenzen) }
        var faktor = 1
        while (grenzen.outWidth / (faktor * 2) >= ziel && grenzen.outHeight / (faktor * 2) >= ziel) faktor *= 2
        val bild = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = faktor })
        } ?: return null
        val grad = contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (grad == 0f) return bild
        return Bitmap.createBitmap(bild, 0, 0, bild.width, bild.height, Matrix().apply { postRotate(grad) }, true)
    }

    private fun dp(wert: Int): Int = (wert * resources.displayMetrics.density).toInt()
}
