# Reglas de release. kotlinx.serialization y Retrofit traen las suyas por consumer rules.

# ML Kit descubre sus componentes leyendo los <meta-data> del manifiesto por reflexión, y ahí
# el nombre de la clase va dentro de la **clave** ("com.google.firebase.components:…Registrar"),
# no en `android:name`. R8 no lo ve como referencia, borra los registradores y entonces
# `BarcodeScanning.getClient()` devuelve un cliente con su fábrica interna en null: la app se
# cae al abrir el escaneo. Verificado en un Galaxy A21s con la variante `medicion`.
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep class com.google.mlkit.common.internal.** { *; }
-keep class com.google.mlkit.vision.barcode.internal.** { *; }
-keep class com.google.mlkit.vision.common.internal.** { *; }
