import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Marke an einer Stelle: marke.properties im Projektordner.
val marke = Properties().apply { rootProject.file("marke.properties").inputStream().use { load(it) } }

// Bei einem Tag-Bau kommt die Version aus dem Tag (-PappVersion=…), sonst aus marke.properties.
val appVersion: String = (findProperty("appVersion") as String?)?.removePrefix("v")
    ?: marke.getProperty("VERSION")

// 1.2.3 -> 10203. Muss mit jeder Version wachsen, sonst lehnt Android das Update ab.
fun versionCodeOf(name: String): Int {
    val teile = name.split(".").map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
    return teile[0] * 10000 + teile[1] * 100 + teile[2]
}

val schluesselDatei: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "de.kleinanzeigenbot.app"
    compileSdk = 35

    defaultConfig {
        applicationId = marke.getProperty("APPLICATION_ID")
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        // Fester Server; nur der Rauchtest baut mit -PserverAdresse=http://10.0.2.2:8106 gegen seinen Testserver.
        val server = (project.findProperty("serverAdresse") as String?) ?: marke.getProperty("SERVER")
        buildConfigField("String", "SERVER", "\"$server\"")
        resValue("string", "app_name", marke.getProperty("APP_NAME"))
    }

    signingConfigs {
        create("release") {
            schluesselDatei?.let { pfad ->
                storeFile = file(pfad)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "kleinanzeigenbot"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (schluesselDatei != null) signingConfigs.getByName("release") else null
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    // Nach unten wischen = Seite neu laden
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    // Etiketten scannen: Erkennung auf dem Gerät, Modell in der App (kein Play-Dienst, kein Netz)
    implementation("androidx.camera:camera-mlkit-vision:$camerax")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    testImplementation("junit:junit:4.13.2")
    // org.json ist im Android-Stub für Unit-Tests leer; echte Implementierung nur für Tests.
    testImplementation("org.json:json:20240303")
}
