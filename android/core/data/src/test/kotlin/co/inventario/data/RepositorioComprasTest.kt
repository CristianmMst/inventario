package co.inventario.data

import co.inventario.common.Resultado
import co.inventario.data.outbox.AlmacenBandejaEnMemoria
import co.inventario.data.outbox.BandejaSalida
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.Json
import co.inventario.data.repositorio.LineaRecepcionNueva
import co.inventario.data.repositorio.ProveedorDatos
import co.inventario.data.repositorio.RecepcionNueva
import co.inventario.data.repositorio.RepositorioComprasApi
import co.inventario.data.repositorio.ResultadoConfirmacion
import co.inventario.domain.modelo.EstadoOrden
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

/** RF-COM-001 / RF-COM-003 / RF-COM-004 / RF-COM-009 / RN-17: compras desde la app. */
class RepositorioComprasTest {

    private val servidor = MockWebServer()
    private val almacen = AlmacenBandejaEnMemoria()

    private fun api(): InventarioApi = Retrofit.Builder()
        .baseUrl(servidor.url("/"))
        .client(OkHttpClient())
        .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(InventarioApi::class.java)

    private fun repositorio(): RepositorioComprasApi {
        val api = api()
        return RepositorioComprasApi(api, BandejaSalida(almacen, api))
    }

    private val proveedorJson = """{"id":"pr1","nombre":"Distribuidora Norte","identificacion_fiscal":"900123","contacto":null,"telefono":null,"email":null,"direccion":null,"notas":null,"estado":"activo"}"""

    private fun recepcionJson(estado: String = "borrador") = """
        {"id":"r1","numero":"RC-000001","proveedor":{"id":"pr1","nombre":"Distribuidora Norte"},"orden":{"id":"o1","numero":"OC-000001"},
         "estado":"$estado","fecha":"2026-09-02","moneda":"COP","tasa_cambio":"1.00000000","notas":null,"confirmada_en":null,
         "lineas":[{"id":"l1","producto":{"id":"p1","nombre":"Cuaderno","sku":"C-1","unidad_codigo":"unidad"},"orden_linea_id":"ol1",
                    "cantidad_recibida":"12.000","costo_unitario":{"monto":"2500.0000","moneda":"COP"},"tasa_cambio":"1.00000000",
                    "costo_unitario_base":{"monto":"2500.0000","moneda":"COP"},"exceso":true}],
         "total":{"monto":"30000.0000","moneda":"COP"},"total_base":{"monto":"30000.0000","moneda":"COP"},"movimientos_generados":[],
         "created_at":"2026-09-02T10:00:00+00:00"}
    """.trimIndent()

    private fun ordenJson(estado: String) = """
        {"id":"o1","numero":"OC-000001","proveedor":{"id":"pr1","nombre":"Distribuidora Norte"},"estado":"$estado","fecha_esperada":null,
         "moneda":"COP","notas":null,"motivo_cierre":null,"emitida_en":null,"cerrada_en":null,
         "lineas":[{"id":"ol1","producto":{"id":"p1","nombre":"Cuaderno","sku":"C-1","unidad_codigo":"unidad"},"cantidad_ordenada":"10.000",
                    "costo_unitario_estimado":null,"cantidad_recibida":"0.000","cantidad_pendiente":"10.000"}],
         "total_estimado":null,"created_at":"2026-09-02T10:00:00+00:00"}
    """.trimIndent()

    @BeforeTest fun arrancar() = servidor.start()
    @AfterTest fun apagar() = servidor.shutdown()

    @Test
    fun `rf com 001 rn 17 un proveedor con documentos no se elimina y el error lo dice`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":{"code":"PROVEEDOR_CON_DOCUMENTOS","message":"x","details":{}}}"""))

        val resultado = repositorio().eliminarProveedor("pr1")

        val fallo = assertIs<Resultado.Fallo>(resultado)
        assertEquals("PROVEEDOR_CON_DOCUMENTOS", fallo.error.codigo)
        assertTrue(fallo.error.mensaje.contains("Archívalo"))
        assertEquals("DELETE", servidor.takeRequest().method)
    }

    @Test
    fun `rf com 001 el alta manda solo los campos con valor`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(201).setBody(proveedorJson))

        val creado = repositorio().crearProveedor(ProveedorDatos(nombre = "Distribuidora Norte", identificacionFiscal = "900123"))

        assertEquals("Distribuidora Norte", assertIs<Resultado.Exito<*>>(creado).valor.let { (it as co.inventario.domain.modelo.Proveedor).nombre })
        val cuerpo = servidor.takeRequest().body.readUtf8()
        assertTrue(cuerpo.contains("\"identificacion_fiscal\":\"900123\""), cuerpo)
        assertTrue(!cuerpo.contains("telefono"), "sin nulos explícitos: $cuerpo")
    }

    @Test
    fun `rf com 003 el estado de la orden se lee como enum`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(ordenJson("parcialmente_recibida")))

        val orden = assertIs<Resultado.Exito<co.inventario.domain.modelo.Orden>>(repositorio().orden("o1")).valor

        assertEquals(EstadoOrden.PARCIALMENTE_RECIBIDA, orden.estado)
        assertEquals("10.000", orden.lineas.single().cantidadPendiente.aApi())
        assertTrue(!orden.estado.editable, "emitida o posterior bloquea la edición de líneas")
    }

    @Test
    fun `rf com 004 una recepcion directa no manda orden_id`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(201).setBody(recepcionJson()))

        repositorio().crearRecepcion(
            RecepcionNueva(proveedorId = "pr1", ordenId = null, lineas = listOf(LineaRecepcionNueva("p1", "12", "2500")), moneda = "COP"),
        )

        val cuerpo = servidor.takeRequest().body.readUtf8()
        assertTrue(!cuerpo.contains("orden_id"), cuerpo)
        assertTrue(cuerpo.contains("\"costo_unitario\":{\"monto\":\"2500.0000\",\"moneda\":\"COP\"}"), cuerpo)
    }

    @Test
    fun `rf com 009 recibir de mas se advierte y solo entra con confirmacion explicita`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error":{"code":"EXCESO_SOBRE_ORDEN","message":"x","details":{"orden_id":"o1","orden_numero":"OC-000001","lineas_con_exceso":"1"}}}""",
            ),
        )
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(recepcionJson("confirmada")))
        val repo = repositorio()

        val primera = repo.confirmarRecepcion("r1", confirmarExceso = false)
        assertIs<ResultadoConfirmacion.ExcesoSobreOrden>(primera)
        val segunda = repo.confirmarRecepcion("r1", confirmarExceso = true)
        val confirmada = assertIs<ResultadoConfirmacion.Confirmada>(segunda)

        assertTrue(confirmada.recepcion.lineas.single().exceso)
        val peticiones = List(2) { servidor.takeRequest() }
        assertTrue(peticiones.all { it.path == "/api/v1/recepciones/r1/confirmar" })
        assertTrue(peticiones[0].body.readUtf8().contains("\"confirmar_exceso\":false"))
        assertTrue(peticiones[1].body.readUtf8().contains("\"confirmar_exceso\":true"))
        assertTrue(peticiones.all { it.getHeader("Idempotency-Key") != null }, "la confirmación va con clave de idempotencia (RNF-06)")
    }
}
