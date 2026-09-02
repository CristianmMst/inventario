package co.inventario.data

import co.inventario.data.outbox.AlmacenBandejaEnMemoria
import co.inventario.data.outbox.BandejaSalida
import co.inventario.data.outbox.Escritura
import co.inventario.data.outbox.EstadoOperacion
import co.inventario.data.outbox.ResultadoEscritura
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.Json
import co.inventario.data.red.dto.MovimientoNuevoDto
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * RNF-06 / RF-INV-011 / RNF-07: la clave de idempotencia nace al confirmar, se guarda con la
 * operación y se reutiliza en cada reintento; la UI solo ve éxito con respuesta del servidor.
 */
class BandejaSalidaTest {

    private val servidor = MockWebServer()
    private val almacen = AlmacenBandejaEnMemoria()

    private fun api(): InventarioApi =
        Retrofit.Builder()
            .baseUrl(servidor.url("/"))
            .client(OkHttpClient.Builder().readTimeout(400, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build())
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(InventarioApi::class.java)

    private fun bandeja() = BandejaSalida(almacen, api())

    private val salida = Escritura.RegistrarMovimiento(
        MovimientoNuevoDto(productoId = "p1", tipo = "salida", cantidad = "2.000", motivo = "venta"),
    )

    private fun movimientoJson(id: String = "m1") = """
        {"id":"$id","producto_id":"p1","tipo":"salida","cantidad":"2.000","direccion":-1,"motivo":"venta",
         "nota":null,"forzado":false,"stock_resultante":"8.000","origen":"app",
         "autor":{"tipo":"usuario","id":"u1"},"ocurrido_en":"2026-09-02T10:00:00+00:00",
         "anulado_en":null,"anula_movimiento_id":null,"recepcion_id":null,"recepcion_linea_id":null}
    """.trimIndent()

    @BeforeTest fun arrancar() = servidor.start()
    @AfterTest fun apagar() = servidor.shutdown()

    @Test
    fun `rnf 06 la clave se genera al confirmar y se persiste aunque el envio falle`() = runTest {
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        val resultado = bandeja().confirmar(salida)

        val pendiente = assertIs<ResultadoEscritura.Pendiente>(resultado)
        UUID.fromString(pendiente.clave)
        val guardadas = almacen.pendientes()
        assertEquals(1, guardadas.size)
        assertEquals(pendiente.clave, guardadas.single().clave)
        assertEquals(EstadoOperacion.PENDIENTE, guardadas.single().estado)
        assertEquals(1, guardadas.single().intentos)
        assertEquals("SIN_RED", pendiente.error.codigo)
        assertEquals(pendiente.clave, servidor.takeRequest().getHeader("Idempotency-Key"), "la clave viaja desde el primer intento")

        servidor.enqueue(MockResponse().setResponseCode(201).setBody(movimientoJson()))
        val reintentos = bandeja().reintentarPendientes()

        assertEquals(1, reintentos.size)
        val confirmada = assertIs<ResultadoEscritura.Confirmada>(reintentos.single())
        assertEquals(pendiente.clave, confirmada.clave)
        assertEquals(pendiente.clave, servidor.takeRequest().getHeader("Idempotency-Key"), "el reintento reutiliza la clave")
        assertTrue(almacen.pendientes().isEmpty())
        assertEquals(EstadoOperacion.CONFIRMADA, almacen.obtener(pendiente.clave)?.estado)
        assertEquals("m1", confirmada.movimiento()?.id)
    }

    @Test
    fun `rnf 06 al reabrir tras morir a mitad del envio se reintenta con la misma clave`() = runTest {
        // El servidor recibe la petición pero la respuesta nunca llega: la app "muere" esperando.
        servidor.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val primera = assertIs<ResultadoEscritura.Pendiente>(bandeja().confirmar(salida))
        assertEquals("TIEMPO_AGOTADO", primera.error.codigo)

        // "Reabrir": otra instancia sobre el mismo almacén, como al arrancar la app.
        servidor.enqueue(MockResponse().setResponseCode(201).setBody(movimientoJson()))
        val alArrancar = BandejaSalida(almacen, api()).reintentarPendientes()

        assertIs<ResultadoEscritura.Confirmada>(alArrancar.single())
        assertEquals(2, servidor.requestCount)
        val claves = List(2) { servidor.takeRequest().getHeader("Idempotency-Key") }
        assertEquals(setOf(primera.clave), claves.toSet(), "las dos peticiones llevan la misma clave: el servidor no duplica")
        assertEquals(1, almacen.todas().count { it.estado == EstadoOperacion.CONFIRMADA })
        assertTrue(almacen.pendientes().isEmpty())
    }

    @Test
    fun `rnf 07 un rechazo de negocio no queda pendiente y trae el error traducido`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(409).setHeader("X-Request-Id", "req-1").setBody(
                """{"error":{"code":"STOCK_INSUFICIENTE","message":"crudo","details":{"disponible":"2.000","solicitado":"5.000","puede_forzar":true}}}""",
            ),
        )

        val resultado = bandeja().confirmar(salida)

        val rechazada = assertIs<ResultadoEscritura.Rechazada>(resultado)
        assertEquals("STOCK_INSUFICIENTE", rechazada.error.codigo)
        assertEquals("Solo hay 2.000 en stock.", rechazada.error.mensaje)
        assertEquals("true", rechazada.error.detalles["puede_forzar"])
        assertTrue(almacen.pendientes().isEmpty(), "un rechazo definitivo no se reintenta")
        assertEquals(EstadoOperacion.RECHAZADA, almacen.obtener(rechazada.clave)?.estado)
    }

    @Test
    fun `una respuesta repetida del servidor con 200 tambien confirma`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(movimientoJson("m9")))

        val confirmada = assertIs<ResultadoEscritura.Confirmada>(bandeja().confirmar(salida))

        assertEquals("m9", confirmada.movimiento()?.id)
        val cuerpo = servidor.takeRequest().body.readUtf8()
        assertTrue(cuerpo.contains("\"cantidad\":\"2.000\""), "la cantidad viaja como cadena (E-01)")
    }
}
