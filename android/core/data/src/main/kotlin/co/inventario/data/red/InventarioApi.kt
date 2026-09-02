package co.inventario.data.red

import co.inventario.data.red.dto.LoginDto
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
    suspend fun editarProducto(@Path("id") id: String, @Body datos: Map<String, String?>): Response<ProductoDto>

    @GET("api/v1/productos/{id}/stock")
    suspend fun stock(@Path("id") id: String): Response<StockDto>

    // Movimientos (RF-INV)
    @GET("api/v1/motivos-movimiento")
    suspend fun motivos(@Query("tipo") tipo: String? = null): Response<PaginaDto<MotivoDto>>

    @POST("api/v1/movimientos")
    suspend fun registrarMovimiento(
        @Header("Idempotency-Key") claveIdempotencia: String,
        @Body datos: MovimientoNuevoDto,
    ): Response<MovimientoDto>

    @GET("api/v1/productos/{id}/movimientos")
    suspend fun historial(
        @Path("id") productoId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limite: Int = 50,
    ): Response<PaginaDto<MovimientoDto>>
}
