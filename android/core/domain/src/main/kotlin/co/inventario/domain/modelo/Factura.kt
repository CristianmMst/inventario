package co.inventario.domain.modelo

import java.time.Instant
import java.time.LocalDate

/** RF-FAC-004: pendiente, pagada o anulada. */
enum class EstadoPago(val codigo: String, val etiqueta: String) {
    PENDIENTE("pendiente", "Pendiente"),
    PAGADA("pagada", "Pagada"),
    ANULADA("anulada", "Anulada"),
    ;

    companion object {
        fun desde(codigo: String): EstadoPago = entries.first { it.codigo == codigo }
    }
}

data class RecepcionBreve(val id: String, val numero: String, val fecha: LocalDate, val total: Dinero)

data class Imagen(val id: String, val url: String, val ancho: Int, val alto: Int, val bytes: Long)

/** RF-FAC-001: factura de compra. Base + impuesto = total exactamente (RN-18). */
data class Factura(
    val id: String,
    val proveedor: ProveedorBreve,
    val numero: String,
    val fechaEmision: LocalDate,
    val fechaVencimiento: LocalDate?,
    val moneda: Moneda,
    val tasaCambio: String,
    val baseGravable: Dinero,
    val impuesto: Dinero,
    val total: Dinero,
    val totalBase: Dinero,
    val estadoPago: EstadoPago,
    val fechaPago: LocalDate?,
    val motivoAnulacion: String?,
    val notas: String?,
    val recepciones: List<RecepcionBreve>,
    val imagenes: List<Imagen>,
    val creadaEn: Instant,
)
