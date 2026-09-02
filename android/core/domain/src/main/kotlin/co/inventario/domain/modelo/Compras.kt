package co.inventario.domain.modelo

import java.time.Instant
import java.time.LocalDate

/** RF-COM-001: proveedor. Con documentos no se borra: se archiva (RN-17). */
data class Proveedor(
    val id: String,
    val nombre: String,
    val identificacionFiscal: String?,
    val contacto: String?,
    val telefono: String?,
    val email: String?,
    val direccion: String?,
    val notas: String?,
    val archivado: Boolean,
)

data class ProveedorBreve(val id: String, val nombre: String)

data class ProductoBreve(val id: String, val nombre: String, val sku: String, val unidadCodigo: String)

/** RF-COM-003: solo en borrador se editan las líneas; solo emitida o parcial admite recepciones. */
enum class EstadoOrden(val codigo: String, val etiqueta: String) {
    BORRADOR("borrador", "Borrador"),
    EMITIDA("emitida", "Emitida"),
    PARCIALMENTE_RECIBIDA("parcialmente_recibida", "Parcialmente recibida"),
    RECIBIDA("recibida", "Recibida"),
    CERRADA_CON_FALTANTE("cerrada_con_faltante", "Cerrada con faltante"),
    CANCELADA("cancelada", "Cancelada"),
    ;

    val editable: Boolean get() = this == BORRADOR
    val recibible: Boolean get() = this == EMITIDA || this == PARCIALMENTE_RECIBIDA
    /** RF-COM-010: emitida sin recepciones se cancela; con recepciones se cierra con faltante. */
    val cancelable: Boolean get() = this == BORRADOR || this == EMITIDA
    val cerrableConFaltante: Boolean get() = this == PARCIALMENTE_RECIBIDA

    companion object {
        fun desde(codigo: String): EstadoOrden = entries.first { it.codigo == codigo }
    }
}

data class LineaOrden(
    val id: String,
    val producto: ProductoBreve,
    val cantidadOrdenada: Cantidad,
    val costoUnitarioEstimado: Dinero?,
    val cantidadRecibida: Cantidad,
    val cantidadPendiente: Cantidad,
)

/** RF-COM-002: orden de compra, herramienta opcional de planificación (RN-11). */
data class Orden(
    val id: String,
    val numero: String,
    val proveedor: ProveedorBreve,
    val estado: EstadoOrden,
    val fechaEsperada: LocalDate?,
    val moneda: Moneda,
    val notas: String?,
    val motivoCierre: String?,
    val lineas: List<LineaOrden>,
    val totalEstimado: Dinero?,
    val creadaEn: Instant,
)

enum class EstadoRecepcion(val codigo: String, val etiqueta: String) {
    BORRADOR("borrador", "Borrador"),
    CONFIRMADA("confirmada", "Confirmada"),
    CORREGIDA("corregida", "Corregida"),
    ;

    companion object {
        fun desde(codigo: String): EstadoRecepcion = entries.first { it.codigo == codigo }
    }
}

/** RF-COM-007 / RN-08: la línea congela costo, moneda, tasa y equivalente en base. */
data class LineaRecepcion(
    val id: String,
    val producto: ProductoBreve,
    val ordenLineaId: String?,
    val cantidadRecibida: Cantidad,
    val costoUnitario: Dinero,
    val tasaCambio: String?,
    val costoUnitarioBase: Dinero?,
    val exceso: Boolean,
)

data class Recepcion(
    val id: String,
    val numero: String,
    val proveedor: ProveedorBreve,
    val orden: OrdenBreve?,
    val estado: EstadoRecepcion,
    val fecha: LocalDate,
    val moneda: Moneda,
    val tasaCambio: String,
    val notas: String?,
    val lineas: List<LineaRecepcion>,
    val total: Dinero,
    val totalBase: Dinero?,
    val creadaEn: Instant,
)

data class OrdenBreve(val id: String, val numero: String)
