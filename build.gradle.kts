// AGP 9.2.1 abilita la built-in Kotlin support, che porta di base Kotlin 2.2.10.
// Forziamo il Kotlin Gradle plugin alla 2.3.x: la registrazione pulita delle sorgenti
// generate (Room/KSP) richiede l'API introdotta in Kotlin 2.3.
// NB: NON applicare il plugin org.jetbrains.kotlin.android — con AGP 9 è già fornito da AGP.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
