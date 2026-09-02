package co.inventario.data.red.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * DTOs de la API v1. Los nombres JSON son los del contrato (snake_case, constitution.md §1);
 * dinero y cantidades son cadenas, nunca números (E-01, RN-07).
 */

@Serializable
data class DineroDto(val monto: String, val moneda: String)

@Serializable
data class PaginaDto<T>(
    val datos: List<T>,
    @SerialName("cursor_siguiente") val cursorSiguiente: String? = null,
    @SerialName("tiene_mas") val tieneMas: Boolean = false,
)

@Serializable
data class ErrorDto(
    val code: String,
    val message: String,
    val details: Map<String, JsonElement> = emptyMap(),
) {
    /** Detalle como texto plano; los booleanos y números también vienen como texto. */
    fun detalle(clave: String): String? = details[clave]?.let { (it as? JsonPrimitive)?.content ?: it.toString() }
}

@Serializable
data class ErrorEnvoltorioDto(val error: ErrorDto)

// --- Identidad -------------------------------------------------------------------------------

@Serializable
data class NegocioNuevoDto(
    val nombre: String,
    @SerialName("moneda_base") val monedaBase: String,
    @SerialName("zona_horaria") val zonaHoraria: String,
)

@Serializable
data class RegistroDto(
    val email: String,
    val password: String,
    val nombre: String,
    val negocio: NegocioNuevoDto,
)

@Serializable
data class LoginDto(val email: String, val password: String)

@Serializable
data class TokenRenovacionDto(@SerialName("token_renovacion") val tokenRenovacion: String)

@Serializable
data class UsuarioDto(val id: String, val email: String, val nombre: String)

@Serializable
data class NegocioDto(
    val id: String,
    val nombre: String,
    @SerialName("moneda_base") val monedaBase: String,
    @SerialName("zona_horaria") val zonaHoraria: String,
)

@Serializable
data class SesionDto(
    @SerialName("token_acceso") val tokenAcceso: String,
    val tipo: String = "Bearer",
    @SerialName("expira_en_segundos") val expiraEnSegundos: Int,
    @SerialName("token_renovacion") val tokenRenovacion: String,
    val usuario: UsuarioDto,
    val negocio: NegocioDto,
)

// --- Catálogo --------------------------------------------------------------------------------

@Serializable
data class UnidadMedidaDto(val codigo: String, val nombre: String, val tipo: String, val decimales: Int)

@Serializable
data class CategoriaDto(val id: String, val nombre: String)

@Serializable
data class ImagenDto(val id: String, val url: String, val mime: String, val ancho: Int, val alto: Int, val bytes: Long)

@Serializable
data class ProductoDto(
    val id: String,
    val sku: String,
    val nombre: String,
    val categoria: CategoriaDto? = null,
    val unidad: UnidadMedidaDto,
    @SerialName("costo_actual") val costoActual: DineroDto? = null,
    @SerialName("precio_venta") val precioVenta: DineroDto? = null,
    @SerialName("stock_minimo") val stockMinimo: String? = null,
    @SerialName("stock_actual") val stockActual: String,
    val estado: String,
    @SerialName("codigos_barras") val codigosBarras: List<String> = emptyList(),
    val imagen: ImagenDto? = null,
)

@Serializable
data class ProductoNuevoDto(
    val nombre: String,
    @SerialName("unidad_codigo") val unidadCodigo: String,
    val sku: String? = null,
    @SerialName("categoria_id") val categoriaId: String? = null,
    @SerialName("costo_actual") val costoActual: DineroDto? = null,
    @SerialName("precio_venta") val precioVenta: DineroDto? = null,
    @SerialName("stock_minimo") val stockMinimo: String? = null,
    @SerialName("codigos_barras") val codigosBarras: List<String> = emptyList(),
)

// --- Movimientos -----------------------------------------------------------------------------

@Serializable
data class AutorDto(val tipo: String, val id: String)

@Serializable
data class MovimientoDto(
    val id: String,
    @SerialName("producto_id") val productoId: String,
    val tipo: String,
    val cantidad: String,
    val direccion: Int,
    val motivo: String,
    val nota: String? = null,
    val forzado: Boolean,
    @SerialName("stock_resultante") val stockResultante: String,
    val origen: String,
    val autor: AutorDto,
    @SerialName("ocurrido_en") val ocurridoEn: String,
    @SerialName("anulado_en") val anuladoEn: String? = null,
    @SerialName("anula_movimiento_id") val anulaMovimientoId: String? = null,
    @SerialName("recepcion_id") val recepcionId: String? = null,
    @SerialName("recepcion_linea_id") val recepcionLineaId: String? = null,
)

@Serializable
data class MovimientoNuevoDto(
    @SerialName("producto_id") val productoId: String,
    val tipo: String,
    val cantidad: String,
    val motivo: String,
    val nota: String? = null,
    val forzar: Boolean = false,
    val direccion: Int? = null,
)

@Serializable
data class MotivoDto(
    val codigo: String,
    @SerialName("tipo_movimiento") val tipoMovimiento: String,
    val etiqueta: String,
    @SerialName("exige_nota") val exigeNota: Boolean,
)

@Serializable
data class AnulacionDto(val nota: String? = null)

@Serializable
data class ConteoEntradaDto(
    @SerialName("cantidad_contada") val cantidadContada: String,
    val nota: String? = null,
)

@Serializable
data class ConteoSalidaDto(
    @SerialName("producto_id") val productoId: String,
    @SerialName("stock_anterior") val stockAnterior: String,
    @SerialName("cantidad_contada") val cantidadContada: String,
    val diferencia: String,
    val movimiento: MovimientoDto? = null,
)

@Serializable
data class StockDto(
    @SerialName("producto_id") val productoId: String,
    val cantidad: String,
    @SerialName("actualizado_en") val actualizadoEn: String? = null,
)
