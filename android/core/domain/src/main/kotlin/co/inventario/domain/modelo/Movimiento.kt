package co.inventario.domain.modelo

import java.time.Instant

/** RF-INV-001: cinco tipos; el tipo determina el signo, nunca la cantidad. */
enum class TipoMovimiento(val codigo: String, val restaStock: Boolean) {
    ENTRADA("entrada", restaStock = false),
    SALIDA("salida", restaStock = true),
    AJUSTE("ajuste", restaStock = false),
    MERMA("merma", restaStock = true),
    CONTRAMOVIMIENTO("contramovimiento", restaStock = false),
    ;

    companion object {
        fun desde(codigo: String): TipoMovimiento = entries.first { it.codigo == codigo }
    }
}

enum class Origen(val codigo: String) {
    APP("app"),
    API("api"),
    RECEPCION("recepcion"),
    ;

    companion object {
        fun desde(codigo: String): Origen = entries.first { it.codigo == codigo }
    }
}

data class Autor(val tipo: String, val id: String)

/** RF-INV-002 / RF-INV-012: el movimiento tal como lo devuelve la API, con su stock resultante. */
data class Movimiento(
    val id: String,
    val productoId: String,
    val tipo: TipoMovimiento,
    val cantidad: Cantidad,
    val direccion: Int,
    val motivo: String,
    val nota: String?,
    val forzado: Boolean,
    val stockResultante: Cantidad,
    val origen: Origen,
    val autor: Autor,
    val ocurridoEn: Instant,
    val anuladoEn: Instant?,
    val anulaMovimientoId: String?,
    val recepcionId: String?,
) {
    val anulado: Boolean get() = anuladoEn != null
}

/** RF-INV-010: motivo de la lista cerrada, con la marca de nota obligatoria. */
data class Motivo(val codigo: String, val tipo: TipoMovimiento, val etiqueta: String, val exigeNota: Boolean)
