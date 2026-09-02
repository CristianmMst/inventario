package co.inventario.feature.escaneo

/**
 * Antirrebote de lecturas (RF-CAT-008, RNF-10). La cámara ve el mismo código muchas veces por
 * segundo; solo se acepta la primera y, mientras siga a la vista, la ventana se desliza: apuntar
 * 5 s produce una lectura. Retirar la etiqueta más de `ventanaMs` permite leerla otra vez.
 * Un código distinto se acepta de inmediato.
 */
class AntirreboteLecturas(
    private val ventanaMs: Long = 1_500,
    private val reloj: () -> Long = System::currentTimeMillis,
) {
    private var ultimoCodigo: String? = null
    private var ultimaVistaMs = 0L

    @Synchronized
    fun aceptar(codigo: String): Boolean {
        val ahora = reloj()
        val repetido = codigo == ultimoCodigo && ahora - ultimaVistaMs < ventanaMs
        ultimoCodigo = codigo
        ultimaVistaMs = ahora
        return !repetido
    }
}
