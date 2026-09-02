package co.inventario.common

/** Resultado de una operación que puede fallar por una causa conocida. */
sealed interface Resultado<out T> {
    data class Exito<T>(val valor: T) : Resultado<T>
    data class Fallo(val error: ErrorApp) : Resultado<Nothing>
}

/** Error que la UI sabe mostrar: siempre lleva un texto en español (RNF-07). */
data class ErrorApp(
    val codigo: String,
    val mensaje: String,
    val requestId: String? = null,
    val detalles: Map<String, String> = emptyMap(),
)
