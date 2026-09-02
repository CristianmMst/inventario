package co.inventario.data.red

import kotlinx.serialization.json.Json as KotlinxJson

/**
 * Configuración única de JSON. No es `isLenient`: un monto numérico en vez de cadena debe
 * fallar (E-01). Los campos desconocidos se ignoran: el servidor puede añadir sin romper.
 */
val Json: KotlinxJson = KotlinxJson {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = false
    isLenient = false
}
