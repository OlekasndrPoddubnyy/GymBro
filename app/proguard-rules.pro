# Regole ProGuard/R8 specifiche dell'app.
#
# Nella release la minificazione è ATTIVA (isMinifyEnabled = true e isShrinkResources = true in
# build.gradle.kts): R8 toglie il codice non usato e accorcia i nomi di classi e metodi.
#
# Per ora non serve nessuna regola in più:
#  - Room, Compose, CameraX e ZXing portano già le loro regole nelle librerie;
#  - l'app non usa reflection: il backup e i QR scrivono e leggono il JSON a mano (org.json),
#    i nomi degli enum salvati (es. "PETTO", "RECUPERO") si leggono con valueOf, che R8 conserva.
#
# Se una release si comporta diversamente dalla debug (crash su ClassNotFoundException,
# NoSuchMethodException o simili), la regola -keep per la classe coinvolta va aggiunta qui.
# Le tracce dello stack della release sono offuscate: per leggerle serve
# app/build/outputs/mapping/release/mapping.txt della stessa build (va conservato con l'APK).
