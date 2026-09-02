package co.inventario.data.red

import co.inventario.data.red.dto.AnulacionDto
import co.inventario.data.red.dto.CategoriaDto
import co.inventario.data.red.dto.CategoriaNuevaDto
import co.inventario.data.red.dto.ConteoEntradaDto
import co.inventario.data.red.dto.ConteoSalidaDto
import co.inventario.data.red.dto.LoginDto
import co.inventario.data.red.dto.ProductoEdicionDto
import co.inventario.data.red.dto.UnidadMedidaDto
import co.inventario.data.red.dto.ApiKeyDto
import co.inventario.data.red.dto.ApiKeyNuevaDto
import co.inventario.data.red.dto.ConfirmacionRecepcionDto
import co.inventario.data.red.dto.FacturaDto
import co.inventario.data.red.dto.FacturaNuevaDto
import co.inventario.data.red.dto.FilaAgotadoDto
import co.inventario.data.red.dto.FilaBajoMinimoDto
import co.inventario.data.red.dto.FilaDiscrepanciaDto
import co.inventario.data.red.dto.FilaSinMovimientoDto
import co.inventario.data.red.dto.MotivoEntradaDto
import co.inventario.data.red.dto.OrdenDto
import co.inventario.data.red.dto.OrdenEdicionDto
import co.inventario.data.red.dto.OrdenNuevaDto
import co.inventario.data.red.dto.PagoDto
import co.inventario.data.red.dto.PaginaFacturasDto
import co.inventario.data.red.dto.ProveedorDatosDto
import co.inventario.data.red.dto.ProveedorDto
import co.inventario.data.red.dto.RecepcionDto
import co.inventario.data.red.dto.RecepcionNuevaDto
import co.inventario.data.red.dto.RecepcionesVinculacionDto
import co.inventario.data.red.dto.ResumenComprasDto
import co.inventario.data.red.dto.ResumenMermasDto
import co.inventario.data.red.dto.SuscripcionDto
import co.inventario.data.red.dto.SuscripcionNuevaDto
import co.inventario.data.red.dto.ValorizacionDto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.DELETE
import retrofit2.http.Multipart
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Streaming
import co.inventario.data.red.dto.MotivoDto
import co.inventario.data.red.dto.MovimientoDto
import co.inventario.data.red.dto.MovimientoNuevoDto
import co.inventario.data.red.dto.NegocioDto
import co.inventario.data.red.dto.PaginaDto
import co.inventario.data.red.dto.ProductoDto
import co.inventario.data.red.dto.ProductoNuevoDto
import co.inventario.data.red.dto.RegistroDto
import co.inventario.data.red.dto.SesionDto
import co.inventario.data.red.dto.StockDto
import co.inventario.data.red.dto.TokenRenovacionDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Contrato HTTP de la API v1 (plan.md §4). Crece por hito; toda ruta figura en OpenAPI. */
interface InventarioApi {

    // Identidad (RF-AUT-001..003)
    @POST("api/v1/auth/registro")
    suspend fun registro(@Body datos: RegistroDto): Response<SesionDto>

    @POST("api/v1/auth/login")
    suspend fun login(@Body datos: LoginDto): Response<SesionDto>

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body datos: TokenRenovacionDto): Response<Unit>

    @GET("api/v1/negocio")
    suspend fun negocio(): Response<NegocioDto>

    // Catálogo (RF-CAT)
    @GET("api/v1/productos/por-codigo/{codigo}")
    suspend fun productoPorCodigo(@Path("codigo") codigo: String): Response<ProductoDto>

    @GET("api/v1/productos/{id}")
    suspend fun producto(@Path("id") id: String): Response<ProductoDto>

    @GET("api/v1/productos/buscar")
    suspend fun buscar(
        @Query("q") texto: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limite: Int = 50,
    ): Response<PaginaDto<ProductoDto>>

    @GET("api/v1/productos")
    suspend fun productos(
        @Query("categoria_id") categoriaId: String? = null,
        @Query("estado") estado: String? = null,
        @Query("condicion_stock") condicionStock: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limite: Int = 50,
    ): Response<PaginaDto<ProductoDto>>

    @POST("api/v1/productos")
    suspend fun crearProducto(@Body datos: ProductoNuevoDto): Response<ProductoDto>

    @PATCH("api/v1/productos/{id}")
    suspend fun editarProducto(@Path("id") id: String, @Body datos: ProductoEdicionDto): Response<ProductoDto>

    @POST("api/v1/productos/{id}/archivar")
    suspend fun archivarProducto(@Path("id") id: String): Response<ProductoDto>

    @POST("api/v1/productos/{id}/desarchivar")
    suspend fun desarchivarProducto(@Path("id") id: String): Response<ProductoDto>

    @Multipart
    @PUT("api/v1/productos/{id}/imagen")
    suspend fun subirImagenProducto(@Path("id") id: String, @Part archivo: MultipartBody.Part): Response<ProductoDto>

    @GET("api/v1/productos/{id}/stock")
    suspend fun stock(@Path("id") id: String): Response<StockDto>

    @GET("api/v1/categorias")
    suspend fun categorias(@Query("limit") limite: Int = 200): Response<PaginaDto<CategoriaDto>>

    @POST("api/v1/categorias")
    suspend fun crearCategoria(@Body datos: CategoriaNuevaDto): Response<CategoriaDto>

    @GET("api/v1/unidades-medida")
    suspend fun unidades(): Response<PaginaDto<UnidadMedidaDto>>

    // Movimientos (RF-INV)
    @GET("api/v1/motivos-movimiento")
    suspend fun motivos(@Query("tipo") tipo: String? = null): Response<PaginaDto<MotivoDto>>

    @POST("api/v1/movimientos")
    suspend fun registrarMovimiento(
        @Header("Idempotency-Key") claveIdempotencia: String,
        @Body datos: MovimientoNuevoDto,
    ): Response<MovimientoDto>

    @POST("api/v1/movimientos/{id}/anular")
    suspend fun anularMovimiento(
        @Header("Idempotency-Key") claveIdempotencia: String,
        @Path("id") movimientoId: String,
        @Body datos: AnulacionDto,
    ): Response<MovimientoDto>

    @POST("api/v1/productos/{id}/conteo")
    suspend fun conteo(
        @Header("Idempotency-Key") claveIdempotencia: String,
        @Path("id") productoId: String,
        @Body datos: ConteoEntradaDto,
    ): Response<ConteoSalidaDto>

    @GET("api/v1/productos/{id}/movimientos")
    suspend fun historial(
        @Path("id") productoId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limite: Int = 50,
    ): Response<PaginaDto<MovimientoDto>>

    // Compras (RF-COM)
    @GET("api/v1/proveedores")
    suspend fun proveedores(@Query("estado") estado: String? = null, @Query("cursor") cursor: String? = null, @Query("limit") limite: Int = 100): Response<PaginaDto<ProveedorDto>>

    @POST("api/v1/proveedores")
    suspend fun crearProveedor(@Body datos: ProveedorDatosDto): Response<ProveedorDto>

    @GET("api/v1/proveedores/{id}")
    suspend fun proveedor(@Path("id") id: String): Response<ProveedorDto>

    @PATCH("api/v1/proveedores/{id}")
    suspend fun editarProveedor(@Path("id") id: String, @Body datos: ProveedorDatosDto): Response<ProveedorDto>

    @DELETE("api/v1/proveedores/{id}")
    suspend fun eliminarProveedor(@Path("id") id: String): Response<Unit>

    @POST("api/v1/proveedores/{id}/archivar")
    suspend fun archivarProveedor(@Path("id") id: String): Response<ProveedorDto>

    @POST("api/v1/proveedores/{id}/desarchivar")
    suspend fun desarchivarProveedor(@Path("id") id: String): Response<ProveedorDto>

    @GET("api/v1/ordenes-compra")
    suspend fun ordenes(
        @Query("proveedor_id") proveedorId: String? = null,
        @Query("estado") estado: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limite: Int = 50,
    ): Response<PaginaDto<OrdenDto>>

    @POST("api/v1/ordenes-compra")
    suspend fun crearOrden(@Body datos: OrdenNuevaDto): Response<OrdenDto>

    @GET("api/v1/ordenes-compra/{id}")
    suspend fun orden(@Path("id") id: String): Response<OrdenDto>

    @PATCH("api/v1/ordenes-compra/{id}")
    suspend fun editarOrden(@Path("id") id: String, @Body datos: OrdenEdicionDto): Response<OrdenDto>

    @POST("api/v1/ordenes-compra/{id}/emitir")
    suspend fun emitirOrden(@Path("id") id: String): Response<OrdenDto>

    @POST("api/v1/ordenes-compra/{id}/cancelar")
    suspend fun cancelarOrden(@Path("id") id: String, @Body datos: MotivoEntradaDto): Response<OrdenDto>

    @POST("api/v1/ordenes-compra/{id}/cerrar-con-faltante")
    suspend fun cerrarOrdenConFaltante(@Path("id") id: String, @Body datos: MotivoEntradaDto): Response<OrdenDto>

    @GET("api/v1/recepciones")
    suspend fun recepciones(
        @Query("proveedor_id") proveedorId: String? = null,
        @Query("orden_id") ordenId: String? = null,
        @Query("estado") estado: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limite: Int = 50,
    ): Response<PaginaDto<RecepcionDto>>

    @POST("api/v1/recepciones")
    suspend fun crearRecepcion(@Body datos: RecepcionNuevaDto): Response<RecepcionDto>

    @GET("api/v1/recepciones/{id}")
    suspend fun recepcion(@Path("id") id: String): Response<RecepcionDto>

    @POST("api/v1/recepciones/{id}/confirmar")
    suspend fun confirmarRecepcion(
        @Header("Idempotency-Key") claveIdempotencia: String,
        @Path("id") id: String,
        @Body datos: ConfirmacionRecepcionDto,
    ): Response<RecepcionDto>

    // Facturas (RF-FAC)
    @GET("api/v1/facturas")
    suspend fun facturas(
        @Query("proveedor_id") proveedorId: String? = null,
        @Query("estado_pago") estadoPago: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limite: Int = 50,
    ): Response<PaginaFacturasDto>

    @POST("api/v1/facturas")
    suspend fun crearFactura(@Header("Idempotency-Key") claveIdempotencia: String, @Body datos: FacturaNuevaDto): Response<FacturaDto>

    @GET("api/v1/facturas/{id}")
    suspend fun factura(@Path("id") id: String): Response<FacturaDto>

    @POST("api/v1/facturas/{id}/pagar")
    suspend fun pagarFactura(@Path("id") id: String, @Body datos: PagoDto): Response<FacturaDto>

    @POST("api/v1/facturas/{id}/anular")
    suspend fun anularFactura(@Path("id") id: String, @Body datos: MotivoEntradaDto): Response<FacturaDto>

    @PUT("api/v1/facturas/{id}/recepciones")
    suspend fun vincularRecepciones(@Path("id") id: String, @Body datos: RecepcionesVinculacionDto): Response<FacturaDto>

    @Multipart
    @POST("api/v1/facturas/{id}/imagenes")
    suspend fun adjuntarImagenFactura(@Path("id") id: String, @Part archivo: MultipartBody.Part): Response<FacturaDto>

    @DELETE("api/v1/facturas/{id}/imagenes/{imagenId}")
    suspend fun quitarImagenFactura(@Path("id") id: String, @Path("imagenId") imagenId: String): Response<FacturaDto>

    @Streaming
    @GET("api/v1/facturas/exportacion")
    suspend fun exportarFacturas(@Query("desde") desde: String, @Query("hasta") hasta: String): Response<ResponseBody>

    // Reportes (RF-REP)
    @GET("api/v1/reportes/bajo-minimo")
    suspend fun reporteBajoMinimo(@Query("cursor") cursor: String? = null, @Query("limit") limite: Int = 100): Response<PaginaDto<FilaBajoMinimoDto>>

    @GET("api/v1/reportes/agotados")
    suspend fun reporteAgotados(@Query("cursor") cursor: String? = null, @Query("limit") limite: Int = 100): Response<PaginaDto<FilaAgotadoDto>>

    @GET("api/v1/reportes/sin-movimiento")
    suspend fun reporteSinMovimiento(@Query("dias") dias: Int = 90, @Query("cursor") cursor: String? = null, @Query("limit") limite: Int = 100): Response<PaginaDto<FilaSinMovimientoDto>>

    @GET("api/v1/reportes/valorizacion")
    suspend fun reporteValorizacion(): Response<ValorizacionDto>

    @GET("api/v1/reportes/compras")
    suspend fun reporteCompras(@Query("desde") desde: String, @Query("hasta") hasta: String): Response<ResumenComprasDto>

    @GET("api/v1/reportes/mermas")
    suspend fun reporteMermas(@Query("desde") desde: String, @Query("hasta") hasta: String): Response<ResumenMermasDto>

    @GET("api/v1/reportes/discrepancias")
    suspend fun reporteDiscrepancias(@Query("cursor") cursor: String? = null, @Query("limit") limite: Int = 100): Response<PaginaDto<FilaDiscrepanciaDto>>

    // Integración (RF-AUT-005, RF-INT-005)
    @GET("api/v1/api-keys")
    suspend fun apiKeys(@Query("limit") limite: Int = 100): Response<PaginaDto<ApiKeyDto>>

    @POST("api/v1/api-keys")
    suspend fun crearApiKey(@Body datos: ApiKeyNuevaDto): Response<ApiKeyDto>

    @DELETE("api/v1/api-keys/{id}")
    suspend fun revocarApiKey(@Path("id") id: String): Response<Unit>

    @GET("api/v1/webhooks")
    suspend fun suscripciones(@Query("limit") limite: Int = 100): Response<PaginaDto<SuscripcionDto>>

    @POST("api/v1/webhooks")
    suspend fun crearSuscripcion(@Body datos: SuscripcionNuevaDto): Response<SuscripcionDto>

    @DELETE("api/v1/webhooks/{id}")
    suspend fun eliminarSuscripcion(@Path("id") id: String): Response<Unit>
}
