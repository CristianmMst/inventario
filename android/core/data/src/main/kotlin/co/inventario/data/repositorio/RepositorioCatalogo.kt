package co.inventario.data.repositorio

import co.inventario.common.ErrorApp
import co.inventario.common.Resultado
import co.inventario.data.mapeo.aDominio
import co.inventario.data.mapeo.aDto
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.dto.CategoriaNuevaDto
import co.inventario.data.red.dto.ProductoEdicionDto
import co.inventario.data.red.dto.ProductoNuevoDto
import co.inventario.data.red.llamada
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Categoria
import co.inventario.domain.modelo.Dinero
import co.inventario.domain.modelo.Moneda
import co.inventario.domain.modelo.Pagina
import co.inventario.domain.modelo.Producto
import co.inventario.domain.modelo.UnidadMedida
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** RF-CAT-008 / RF-CAT-009: qué pasó al resolver un código. `Desconocido` nunca crea nada (RN-14). */
sealed interface ResultadoCodigo {
    data class Encontrado(val producto: Producto) : ResultadoCodigo
    data class Desconocido(val codigo: String) : ResultadoCodigo
    data class Error(val error: ErrorApp) : ResultadoCodigo
}

/** RF-CAT-014: filtros del listado; todos opcionales. */
data class FiltrosProductos(
    val categoriaId: String? = null,
    val estado: String? = null,
    val condicionStock: String? = null,
)

/** RF-CAT-001: lo que teclea la usuaria, todavía como texto; aquí se normaliza al contrato. */
data class ProductoNuevo(
    val nombre: String,
    val unidadCodigo: String,
    val sku: String?,
    val categoriaId: String?,
    val costoActual: String?,
    val precioVenta: String?,
    val stockMinimo: String?,
    val codigosBarras: List<String>,
    val moneda: String,
)

data class ProductoEdicion(
    val nombre: String? = null,
    val unidadCodigo: String? = null,
    val sku: String? = null,
    val categoriaId: String? = null,
    val costoActual: String? = null,
    val precioVenta: String? = null,
    val stockMinimo: String? = null,
    val moneda: String,
)

interface RepositorioCatalogo {
    suspend fun porCodigo(codigo: String): ResultadoCodigo
    suspend fun producto(id: String): Resultado<Producto>
    suspend fun buscar(texto: String, cursor: String? = null): Resultado<Pagina<Producto>>
    suspend fun listar(filtros: FiltrosProductos = FiltrosProductos(), cursor: String? = null): Resultado<Pagina<Producto>>
    suspend fun crear(datos: ProductoNuevo): Resultado<Producto>
    suspend fun editar(id: String, datos: ProductoEdicion): Resultado<Producto>
    suspend fun archivar(id: String, archivar: Boolean): Resultado<Producto>
    suspend fun subirImagen(id: String, jpeg: ByteArray): Resultado<Producto>
    suspend fun categorias(): Resultado<List<Categoria>>
    suspend fun crearCategoria(nombre: String): Resultado<Categoria>
    suspend fun unidades(): Resultado<List<UnidadMedida>>
}

@Singleton
class RepositorioCatalogoApi @Inject constructor(private val api: InventarioApi) : RepositorioCatalogo {

    override suspend fun porCodigo(codigo: String): ResultadoCodigo =
        when (val r = llamada({ api.productoPorCodigo(codigo) }) { it.aDominio() }) {
            is Resultado.Exito -> ResultadoCodigo.Encontrado(r.valor)
            is Resultado.Fallo ->
                if (r.error.codigo == "PRODUCTO_NO_ENCONTRADO") ResultadoCodigo.Desconocido(codigo) else ResultadoCodigo.Error(r.error)
        }

    override suspend fun producto(id: String): Resultado<Producto> = llamada({ api.producto(id) }) { it.aDominio() }

    override suspend fun buscar(texto: String, cursor: String?): Resultado<Pagina<Producto>> =
        llamada({ api.buscar(texto, cursor) }) { it.aDominio { p -> p.aDominio() } }

    override suspend fun listar(filtros: FiltrosProductos, cursor: String?): Resultado<Pagina<Producto>> =
        llamada({ api.productos(filtros.categoriaId, filtros.estado, filtros.condicionStock, cursor) }) { it.aDominio { p -> p.aDominio() } }

    override suspend fun crear(datos: ProductoNuevo): Resultado<Producto> {
        val moneda = Moneda(datos.moneda)
        val dto = ProductoNuevoDto(
            nombre = datos.nombre.trim(),
            unidadCodigo = datos.unidadCodigo,
            sku = datos.sku?.trim()?.ifBlank { null },
            categoriaId = datos.categoriaId,
            costoActual = datos.costoActual.dinero(moneda),
            precioVenta = datos.precioVenta.dinero(moneda),
            stockMinimo = datos.stockMinimo.cantidad(),
            codigosBarras = datos.codigosBarras.map(String::trim).filter(String::isNotEmpty),
        )
        return llamada({ api.crearProducto(dto) }) { it.aDominio() }
    }

    override suspend fun editar(id: String, datos: ProductoEdicion): Resultado<Producto> {
        val moneda = Moneda(datos.moneda)
        val dto = ProductoEdicionDto(
            nombre = datos.nombre?.trim(),
            unidadCodigo = datos.unidadCodigo,
            sku = datos.sku?.trim(),
            categoriaId = datos.categoriaId,
            costoActual = datos.costoActual.dinero(moneda),
            precioVenta = datos.precioVenta.dinero(moneda),
            stockMinimo = datos.stockMinimo.cantidad(),
        )
        return llamada({ api.editarProducto(id, dto) }) { it.aDominio() }
    }

    override suspend fun archivar(id: String, archivar: Boolean): Resultado<Producto> =
        llamada({ if (archivar) api.archivarProducto(id) else api.desarchivarProducto(id) }) { it.aDominio() }

    override suspend fun subirImagen(id: String, jpeg: ByteArray): Resultado<Producto> {
        val parte = MultipartBody.Part.createFormData("archivo", "producto.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
        return llamada({ api.subirImagenProducto(id, parte) }) { it.aDominio() }
    }

    override suspend fun categorias(): Resultado<List<Categoria>> =
        llamada({ api.categorias() }) { pagina -> pagina.datos.map { it.aDominio() } }

    override suspend fun crearCategoria(nombre: String): Resultado<Categoria> =
        llamada({ api.crearCategoria(CategoriaNuevaDto(nombre.trim())) }) { it.aDominio() }

    override suspend fun unidades(): Resultado<List<UnidadMedida>> =
        llamada({ api.unidades() }) { pagina -> pagina.datos.map { it.aDominio() } }

    /** Texto tecleado → cadena decimal del contrato (E-01). Vacío es "sin valor", no cero. */
    private fun String?.dinero(moneda: Moneda) = this?.trim()?.ifBlank { null }?.let { Dinero.desde(it, moneda).aDto() }

    private fun String?.cantidad() = this?.trim()?.ifBlank { null }?.let { Cantidad.desde(it).aApi() }
}
