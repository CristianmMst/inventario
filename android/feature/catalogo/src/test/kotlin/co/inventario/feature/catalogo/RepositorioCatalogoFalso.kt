package co.inventario.feature.catalogo

import co.inventario.common.ErrorApp
import co.inventario.common.Resultado
import co.inventario.data.repositorio.FiltrosProductos
import co.inventario.data.repositorio.ProductoEdicion
import co.inventario.data.repositorio.ProductoNuevo
import co.inventario.data.repositorio.RepositorioCatalogo
import co.inventario.data.repositorio.ResultadoCodigo
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Categoria
import co.inventario.domain.modelo.EstadoProducto
import co.inventario.domain.modelo.Pagina
import co.inventario.domain.modelo.Producto
import co.inventario.domain.modelo.TipoUnidad
import co.inventario.domain.modelo.UnidadMedida
import kotlinx.coroutines.CompletableDeferred

val UNIDAD = UnidadMedida("unidad", "Unidad", TipoUnidad.DISCRETA, 0)

fun productoDePrueba(id: String = "p1", nombre: String = "Cuaderno 100 hojas", stock: String = "12") = Producto(
    id = id, sku = "CUAD-100", nombre = nombre, categoria = Categoria("c1", "Papelería"), unidad = UNIDAD,
    costoActual = null, precioVenta = null, stockMinimo = Cantidad.desde("5"), stockActual = Cantidad.desde(stock),
    estado = EstadoProducto.ACTIVO, codigosBarras = listOf("7701234567890"), imagenUrl = null,
)

/** Doble de prueba explícito: registra llamadas y deja controlar cuándo termina la subida de la foto. */
open class RepositorioCatalogoFalso : RepositorioCatalogo {
    var productos = mutableMapOf("p1" to productoDePrueba())
    var codigos = mutableMapOf("7701234567890" to "p1")
    var creados = mutableListOf<ProductoNuevo>()
    var editados = mutableListOf<Pair<String, ProductoEdicion>>()
    var imagenesSubidas = mutableListOf<Pair<String, ByteArray>>()
    var busquedas = mutableListOf<String>()
    var listados = mutableListOf<FiltrosProductos>()
    var fallo: ErrorApp? = null
    val subidaTermina = CompletableDeferred<Unit>()

    override suspend fun porCodigo(codigo: String): ResultadoCodigo {
        fallo?.let { return ResultadoCodigo.Error(it) }
        val id = codigos[codigo] ?: return ResultadoCodigo.Desconocido(codigo)
        return ResultadoCodigo.Encontrado(productos.getValue(id))
    }

    override suspend fun producto(id: String): Resultado<Producto> =
        fallo?.let { Resultado.Fallo(it) } ?: productos[id]?.let { Resultado.Exito(it) } ?: Resultado.Fallo(ErrorApp("PRODUCTO_NO_ENCONTRADO", "Ese producto no existe."))

    override suspend fun buscar(texto: String, cursor: String?): Resultado<Pagina<Producto>> {
        busquedas += texto
        return Resultado.Exito(Pagina(productos.values.filter { it.nombre.contains(texto, ignoreCase = true) }, null, false))
    }

    override suspend fun listar(filtros: FiltrosProductos, cursor: String?): Resultado<Pagina<Producto>> {
        listados += filtros
        return Resultado.Exito(Pagina(productos.values.toList(), null, false))
    }

    override suspend fun crear(datos: ProductoNuevo): Resultado<Producto> {
        fallo?.let { return Resultado.Fallo(it) }
        creados += datos
        val nuevo = productoDePrueba(id = "nuevo-${creados.size}", nombre = datos.nombre, stock = "0")
        productos[nuevo.id] = nuevo
        return Resultado.Exito(nuevo)
    }

    override suspend fun editar(id: String, datos: ProductoEdicion): Resultado<Producto> {
        editados += id to datos
        return Resultado.Exito(productos.getValue(id).copy(nombre = datos.nombre ?: productos.getValue(id).nombre))
    }

    override suspend fun archivar(id: String, archivar: Boolean): Resultado<Producto> = Resultado.Exito(productos.getValue(id))

    override suspend fun subirImagen(id: String, jpeg: ByteArray): Resultado<Producto> {
        subidaTermina.await()
        imagenesSubidas += id to jpeg
        return Resultado.Exito(productos.getValue(id).copy(imagenUrl = "http://x/imagenes/1"))
    }

    override suspend fun categorias(): Resultado<List<Categoria>> = Resultado.Exito(listOf(Categoria("c1", "Papelería")))

    override suspend fun crearCategoria(nombre: String): Resultado<Categoria> = Resultado.Exito(Categoria("c-nueva", nombre))

    override suspend fun unidades(): Resultado<List<UnidadMedida>> = Resultado.Exito(listOf(UNIDAD, UnidadMedida("kg", "Kilogramo", TipoUnidad.CONTINUA, 3)))
}
