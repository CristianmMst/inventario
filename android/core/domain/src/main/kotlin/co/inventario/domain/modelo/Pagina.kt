package co.inventario.domain.modelo

/** Página por cursor (constitution.md §3): la app nunca pide "página 3", pide "lo que sigue". */
data class Pagina<T>(
    val datos: List<T>,
    val cursorSiguiente: String?,
    val tieneMas: Boolean,
) {
    fun <R> map(transformar: (T) -> R): Pagina<R> = Pagina(datos.map(transformar), cursorSiguiente, tieneMas)

    companion object {
        fun <T> vacia(): Pagina<T> = Pagina(emptyList(), null, false)
    }
}
