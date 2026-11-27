import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Plugin Compose per Kotlin 2.x. NON applichiamo kotlin-android: con AGP 9 (built-in Kotlin)
    // è già fornito da AGP e applicarlo rompe la build.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Credenziali di firma release (assenti = release non firmata)
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

android {
    namespace = "com.my.gymbro"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.my.gymbro"
        minSdk = 35
        targetSdk = 36
        // Da aumentare a OGNI APK dato a qualcuno (versionCode +1, versionName a piacere):
        // si legge in app da ⋮ › Informazioni, così si sa quale versione ha chi la usa.
        // Android rifiuta anche di installare sopra un versionCode più alto.
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // Toglie codice, icone e risorse non usate: la release pesa molto meno della debug.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Con la built-in Kotlin di AGP 9, il JVM target di Kotlin viene allineato a compileOptions.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    // Nei test JVM le classi Android sono stub: Log & co. restituiscono valori vuoti invece di
    // lanciare eccezioni (serve per provare il motore dell'allenamento, che scrive nel log).
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Il test di migrazione legge gli schemi esportati da Room (app/schemas) come asset.
    sourceSets {
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }
}

// Storico degli schemi Room (app/schemas/<versione>.json), da tenere in git: serve per le
// AutoMigration e per testare le migrazioni dalla prossima versione del database in poi.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.window)

    // QR delle schede: ZXing crea e legge i codici, CameraX inquadra. Tutto sul telefono, senza internet.
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    // org.json è già in Android, ma nei test JVM ci sono solo stub vuoti: serve per provare il backup.
    testImplementation(libs.org.json)
    // runTest e tempo virtuale: conti alla rovescia di minuti provati in millisecondi.
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    constraints {
        // Room porta kotlinx-serialization 1.7.3, ma room-testing (test di migrazione) è compilato
        // con la 1.8.x e i test strumentati devono usare le stesse versioni dell'app.
        implementation(libs.kotlinx.serialization.core) {
            because("room-testing 2.8 richiede kotlinx-serialization ≥ 1.8")
        }
    }

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
