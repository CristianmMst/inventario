package co.inventario.data.red.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DTOs de compras, facturas, reportes e integración (H9). Mismo contrato snake_case de la API v1. */

// --- Proveedores -----------------------------------------------------------------------------

@Serializable
data class ProveedorDto(
    val id: String,
    val nombre: String,
    @SerialName("identificacion_fiscal") val identificacionFiscal: String? = null,
    val contacto: String? = null,
    val telefono: String? = null,
    val email: String? = null,
    val direccion: String? = null,
    val notas: String? = null,
    val estado: String,
)

/** Alta y edición comparten forma; en edición los nulos se omiten (explicitNulls=false). */
@Serializable
data class ProveedorDatosDto(
    val nombre: String? = null,
    @SerialName("identificacion_fiscal") val identificacionFiscal: String? = null,
    val contacto: String? = null,
    val telefono: String? = null,
    val email: String? = null,
    val direccion: String? = null,
    val notas: String? = null,
)

@Serializable
data class ProveedorBreveDto(val id: String, val nombre: String)

@Serializable
data class ProductoBreveDto(val id: String, val nombre: String, val sku: String, @SerialName("unidad_codigo") val unidadCodigo: String)

// --- Órdenes ---------------------------------------------------------------------------------

@Serializable
data class LineaOrdenEntradaDto(
    @SerialName("producto_id") val productoId: String,
    val cantidad: String,
    @SerialName("costo_unitario_estimado") val costoUnitarioEstimado: DineroDto? = null,
)

@Serializable
data class OrdenNuevaDto(
    @SerialName("proveedor_id") val proveedorId: String,
    @SerialName("fecha_esperada") val fechaEsperada: String? = null,
    val moneda: String? = null,
    val notas: String? = null,
    val lineas: List<LineaOrdenEntradaDto>,
)

@Serializable
data class OrdenEdicionDto(
    @SerialName("fecha_esperada") val fechaEsperada: String? = null,
    val notas: String? = null,
    val lineas: List<LineaOrdenEntradaDto>? = null,
)

@Serializable
data class LineaOrdenDto(
    val id: String,
    val producto: ProductoBreveDto,
    @SerialName("cantidad_ordenada") val cantidadOrdenada: String,
    @SerialName("costo_unitario_estimado") val costoUnitarioEstimado: DineroDto? = null,
    @SerialName("cantidad_recibida") val cantidadRecibida: String,
    @SerialName("cantidad_pendiente") val cantidadPendiente: String,
)

@Serializable
data class OrdenDto(
    val id: String,
    val numero: String,
    val proveedor: ProveedorBreveDto,
    val estado: String,
    @SerialName("fecha_esperada") val fechaEsperada: String? = null,
    val moneda: String,
    val notas: String? = null,
    @SerialName("motivo_cierre") val motivoCierre: String? = null,
    @SerialName("emitida_en") val emitidaEn: String? = null,
    @SerialName("cerrada_en") val cerradaEn: String? = null,
    val lineas: List<LineaOrdenDto>,
    @SerialName("total_estimado") val totalEstimado: DineroDto? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class MotivoEntradaDto(val motivo: String)

// --- Recepciones -----------------------------------------------------------------------------

@Serializable
data class LineaRecepcionEntradaDto(
    @SerialName("producto_id") val productoId: String,
    val cantidad: String,
    @SerialName("costo_unitario") val costoUnitario: DineroDto,
)

@Serializable
data class RecepcionNuevaDto(
    @SerialName("proveedor_id") val proveedorId: String,
    @SerialName("orden_id") val ordenId: String? = null,
    val fecha: String? = null,
    val moneda: String? = null,
    @SerialName("tasa_cambio") val tasaCambio: String? = null,
    val notas: String? = null,
    val lineas: List<LineaRecepcionEntradaDto>,
)

@Serializable
data class ConfirmacionRecepcionDto(@SerialName("confirmar_exceso") val confirmarExceso: Boolean)

@Serializable
data class OrdenBreveDto(val id: String, val numero: String)

@Serializable
data class LineaRecepcionDto(
    val id: String,
    val producto: ProductoBreveDto,
    @SerialName("orden_linea_id") val ordenLineaId: String? = null,
    @SerialName("cantidad_recibida") val cantidadRecibida: String,
    @SerialName("costo_unitario") val costoUnitario: DineroDto,
    @SerialName("tasa_cambio") val tasaCambio: String? = null,
    @SerialName("costo_unitario_base") val costoUnitarioBase: DineroDto? = null,
    val exceso: Boolean,
)

@Serializable
data class RecepcionDto(
    val id: String,
    val numero: String,
    val proveedor: ProveedorBreveDto,
    val orden: OrdenBreveDto? = null,
    val estado: String,
    val fecha: String,
    val moneda: String,
    @SerialName("tasa_cambio") val tasaCambio: String,
    val notas: String? = null,
    @SerialName("confirmada_en") val confirmadaEn: String? = null,
    val lineas: List<LineaRecepcionDto>,
    val total: DineroDto,
    @SerialName("total_base") val totalBase: DineroDto? = null,
    @SerialName("movimientos_generados") val movimientosGenerados: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String,
)

// --- Facturas --------------------------------------------------------------------------------

@Serializable
data class FacturaNuevaDto(
    @SerialName("proveedor_id") val proveedorId: String,
    val numero: String,
    @SerialName("fecha_emision") val fechaEmision: String,
    @SerialName("fecha_vencimiento") val fechaVencimiento: String? = null,
    val moneda: String? = null,
    @SerialName("tasa_cambio") val tasaCambio: String? = null,
    @SerialName("base_gravable") val baseGravable: DineroDto,
    val impuesto: DineroDto,
    val total: DineroDto,
    val notas: String? = null,
    val recepciones: List<String> = emptyList(),
)

@Serializable
data class PagoDto(@SerialName("fecha_pago") val fechaPago: String)

@Serializable
data class RecepcionesVinculacionDto(val recepciones: List<String>)

@Serializable
data class RecepcionBreveDto(val id: String, val numero: String, val fecha: String, val total: DineroDto)

@Serializable
data class FacturaDto(
    val id: String,
    val proveedor: ProveedorBreveDto,
    val numero: String,
    @SerialName("fecha_emision") val fechaEmision: String,
    @SerialName("fecha_vencimiento") val fechaVencimiento: String? = null,
    val moneda: String,
    @SerialName("tasa_cambio") val tasaCambio: String,
    @SerialName("base_gravable") val baseGravable: DineroDto,
    val impuesto: DineroDto,
    val total: DineroDto,
    @SerialName("total_base") val totalBase: DineroDto,
    @SerialName("estado_pago") val estadoPago: String,
    @SerialName("fecha_pago") val fechaPago: String? = null,
    @SerialName("motivo_anulacion") val motivoAnulacion: String? = null,
    val notas: String? = null,
    val recepciones: List<RecepcionBreveDto> = emptyList(),
    val imagenes: List<ImagenDto> = emptyList(),
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PaginaFacturasDto(
    val datos: List<FacturaDto>,
    @SerialName("cursor_siguiente") val cursorSiguiente: String? = null,
    @SerialName("tiene_mas") val tieneMas: Boolean = false,
    @SerialName("total_filtro") val totalFiltro: DineroDto,
    @SerialName("cantidad_filtro") val cantidadFiltro: Int,
)

// --- Reportes --------------------------------------------------------------------------------

@Serializable
data class FilaBajoMinimoDto(
    val producto: ProductoBreveDto,
    @SerialName("stock_actual") val stockActual: String,
    @SerialName("stock_minimo") val stockMinimo: String,
    val deficit: String,
    @SerialName("deficit_relativo") val deficitRelativo: String,
)

@Serializable
data class FilaAgotadoDto(val producto: ProductoBreveDto, @SerialName("stock_actual") val stockActual: String, @SerialName("stock_minimo") val stockMinimo: String? = null)

@Serializable
data class FilaSinMovimientoDto(
    val producto: ProductoBreveDto,
    @SerialName("stock_actual") val stockActual: String,
    @SerialName("valor_a_costo") val valorACosto: DineroDto? = null,
    @SerialName("ultimo_movimiento_en") val ultimoMovimientoEn: String? = null,
    @SerialName("creado_en") val creadoEn: String,
)

@Serializable
data class ValorCategoriaDto(val categoria: CategoriaDto? = null, val productos: Int, val valor: DineroDto)

@Serializable
data class FilaNoValorizableDto(val producto: ProductoBreveDto, @SerialName("stock_actual") val stockActual: String)

@Serializable
data class ValorizacionDto(
    val total: DineroDto,
    @SerialName("productos_valorizados") val productosValorizados: Int,
    @SerialName("por_categoria") val porCategoria: List<ValorCategoriaDto>,
    @SerialName("no_valorizables") val noValorizables: PaginaDto<FilaNoValorizableDto>,
)

@Serializable
data class ComprasProveedorDto(val proveedor: ProveedorBreveDto, @SerialName("total_recibido") val totalRecibido: DineroDto, @SerialName("total_facturado") val totalFacturado: DineroDto)

@Serializable
data class ComprasCategoriaDto(val categoria: CategoriaDto? = null, @SerialName("total_recibido") val totalRecibido: DineroDto)

@Serializable
data class ResumenComprasDto(
    val desde: String,
    val hasta: String,
    @SerialName("total_recibido") val totalRecibido: DineroDto,
    @SerialName("total_facturado") val totalFacturado: DineroDto,
    val recepciones: Int,
    val facturas: Int,
    @SerialName("por_proveedor") val porProveedor: List<ComprasProveedorDto>,
    @SerialName("por_categoria") val porCategoria: List<ComprasCategoriaDto>,
)

@Serializable
data class MermaMotivoDto(val motivo: String, val etiqueta: String, val cantidad: String, val valor: DineroDto)

@Serializable
data class MermaProductoDto(val producto: ProductoBreveDto, val cantidad: String, val valor: DineroDto)

@Serializable
data class ResumenMermasDto(
    val desde: String,
    val hasta: String,
    @SerialName("total_cantidad") val totalCantidad: String,
    @SerialName("total_valor") val totalValor: DineroDto,
    @SerialName("por_motivo") val porMotivo: List<MermaMotivoDto>,
    @SerialName("por_producto") val porProducto: PaginaDto<MermaProductoDto>,
)

@Serializable
data class FilaDiscrepanciaDto(
    @SerialName("movimiento_id") val movimientoId: String,
    val producto: ProductoBreveDto,
    val tipo: String,
    val cantidad: String,
    @SerialName("stock_resultante") val stockResultante: String,
    val motivo: String,
    val nota: String? = null,
    @SerialName("ocurrido_en") val ocurridoEn: String,
    @SerialName("autor_tipo") val autorTipo: String,
)

// --- Integración -----------------------------------------------------------------------------

@Serializable
data class ApiKeyNuevaDto(val nombre: String)

@Serializable
data class ApiKeyDto(
    val id: String,
    val nombre: String,
    val prefijo: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("ultimo_uso_en") val ultimoUsoEn: String? = null,
    @SerialName("revocado_en") val revocadoEn: String? = null,
    /** Solo viene en la respuesta de creación: el secreto se muestra una vez (RF-AUT-005). */
    val clave: String? = null,
)

@Serializable
data class SuscripcionNuevaDto(val url: String, val tipos: List<String>, val secreto: String, val descripcion: String? = null)

@Serializable
data class SuscripcionDto(
    val id: String,
    val url: String,
    val tipos: List<String>,
    val activa: Boolean,
    val descripcion: String? = null,
    @SerialName("created_at") val createdAt: String,
)
