package co.inventario.data

import co.inventario.common.Resultado
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.Json
import co.inventario.data.repositorio.FiltrosProductos
import co.inventario.data.repositorio.ProductoNuevo
import co.inventario.data.repositorio.RepositorioCatalogoApi
import co.inventario.data.repositorio.ResultadoCodigo
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** RF-CAT-008 / RF-CAT-009 / RN-14 / RF-CAT-007 / RF-CAT-014: catálogo desde la app. */
class RepositorioCatalogoTest {

    private val servidor = MockWebServer()

    private fun repositorio(): RepositorioCatalogoApi {
        val api = Retrofit.Builder()
            .baseUrl(servidor.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(InventarioApi::class.java)
        return RepositorioCatalogoApi(api)
    }

    private fun productoJson(id: String = "p1", nombre: String = "Cuaderno 100 hojas") = """
        {"id":"$id","sku":"CUAD-100","nombre":"$nombre","categoria":{"id":"c1","nombre":"Papelería"},
         "unidad":{"codigo":"unidad","nombre":"Unidad","tipo":"discreta","decimales":0},
         "costo_actual":{"monto":"2500.0000","moneda":"COP"},"precio_venta":{"monto":"4000.0000","moneda":"COP"},
         "stock_minimo":"5.000","stock_actual":"12.000","estado":"activo","codigos_barras":["7701234567890"],"imagen":null}
    """.trimIndent()

    @BeforeTest fun arrancar() = servidor.start()
    @AfterTest fun apagar() = servidor.shutdown()

    @Test
    fun `rf cat 008 un codigo conocido devuelve el producto con su stock`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(productoJson()))

        val resultado = repositorio().porCodigo("7701234567890")

        val encontrado = assertIs<ResultadoCodigo.Encontrado>(resultado)
        assertEquals("Cuaderno 100 hojas", encontrado.producto.nombre)
        assertEquals("12.000", encontrado.producto.stockActual.aApi())
        assertEquals("/api/v1/productos/por-codigo/7701234567890", servidor.takeRequest().path)
    }

    @Test
    fun `rf cat 009 rn 14 un codigo desconocido se informa y no crea nada`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"error":{"code":"PRODUCTO_NO_ENCONTRADO","message":"x","details":{"codigo":"999"}}}""",
            ),
        )

        val resultado = repositorio().porCodigo("999")

        val desconocido = assertIs<ResultadoCodigo.Desconocido>(resultado)
        assertEquals("999", desconocido.codigo)
        assertEquals(1, servidor.requestCount, "solo la consulta: ningún POST automático")
        assertEquals("GET", servidor.takeRequest().method)
    }

    @Test
    fun `rf cat 007 la busqueda por texto pagina por cursor`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"datos":[${productoJson()},${productoJson("p2", "Cuadrícula A4")}],"cursor_siguiente":"abc","tiene_mas":true}""",
            ),
        )

        val pagina = assertIs<Resultado.Exito<*>>(repositorio().buscar("cuad")).valor as co.inventario.domain.modelo.Pagina<*>

        assertEquals(2, pagina.datos.size)
        assertEquals("abc", pagina.cursorSiguiente)
        assertTrue(pagina.tieneMas)
        val peticion = servidor.takeRequest()
        assertEquals("/api/v1/productos/buscar", peticion.requestUrl?.encodedPath)
        assertEquals("cuad", peticion.requestUrl?.queryParameter("q"))
    }

    @Test
    fun `rf cat 014 el listado manda los filtros de categoria estado y stock`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody("""{"datos":[],"cursor_siguiente":null,"tiene_mas":false}"""))

        repositorio().listar(FiltrosProductos(categoriaId = "c1", estado = "activo", condicionStock = "bajo_minimo"))

        val url = servidor.takeRequest().requestUrl!!
        assertEquals("c1", url.queryParameter("categoria_id"))
        assertEquals("activo", url.queryParameter("estado"))
        assertEquals("bajo_minimo", url.queryParameter("condicion_stock"))
    }

    @Test
    fun `rf cat 001 el alta manda dinero y cantidades como cadenas`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(201).setBody(productoJson()))

        val resultado = repositorio().crear(
            ProductoNuevo(
                nombre = "Cuaderno 100 hojas", unidadCodigo = "unidad", sku = null, categoriaId = "c1",
                costoActual = "2500", precioVenta = "4000", stockMinimo = "5", codigosBarras = listOf("7701234567890"), moneda = "COP",
            ),
        )

        assertIs<Resultado.Exito<*>>(resultado)
        val cuerpo = servidor.takeRequest().body.readUtf8()
        assertTrue(cuerpo.contains("\"costo_actual\":{\"monto\":\"2500.0000\",\"moneda\":\"COP\"}"), cuerpo)
        assertTrue(cuerpo.contains("\"stock_minimo\":\"5.000\""), cuerpo)
        assertTrue(cuerpo.contains("\"codigos_barras\":[\"7701234567890\"]"), cuerpo)
    }
}
