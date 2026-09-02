package co.inventario.domain.modelo

/**
 * RNF-05: límites de las imágenes, iguales a los del servidor. La compresión ocurre en el
 * celular antes de subir; el servidor rechaza lo que exceda estos límites.
 */
data class LimitesImagen(val ladoMaximoPx: Int, val bytesMaximos: Int, val calidadInicial: Int) {
    companion object {
        val PRODUCTO = LimitesImagen(ladoMaximoPx = 1280, bytesMaximos = 300 * 1024, calidadInicial = 80)
        val FACTURA = LimitesImagen(ladoMaximoPx = 2048, bytesMaximos = 1536 * 1024, calidadInicial = 85)
    }
}
