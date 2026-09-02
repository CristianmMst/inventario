package co.inventario.data

import co.inventario.common.Resultado
import co.inventario.data.outbox.AlmacenBandejaEnMemoria
import co.inventario.data.outbox.BandejaSalida
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.Json
import co.inventario.data.repositorio.FacturaNueva
import co.inventario.data.repositorio.FiltrosFacturas
import co.inventario.data.repositorio.RepositorioFacturasApi
import co.inventario.data.repositorio.ResultadoFactura
import co.inventario.domain.modelo.EstadoPago
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** RF-FAC-001 / RF-FAC-004 / RF-FAC-007 / RF-FAC-008 / RNF-06: facturas de compra desde la app. */
class RepositorioFacturasTest {

    private val servidor = MockWebServer()

    private fun repositorio(): RepositorioFacturasApi {
        val api = Retrofit.Builder()
            .baseUrl(servidor.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(InventarioApi::class.java)
        return RepositorioFacturasApi(api, BandejaSalida(AlmacenBandejaEnMemoria(), api))
    }

    private fun facturaJson(estado: String = "pendiente") = """
        {"id":"f1","proveedor":{"id":"pr1","nombre":"Distribuidora Norte"},"numero":"FE-1001","fecha_emision":"2026-09-01","fecha_vencimiento":null,
         "moneda":"COP","tasa_cambio":"1.00000000","base_gravable":{"monto":"100000.0000","moneda":"COP"},"impuesto":{"monto":"19000.0000","moneda":"COP"},
         "total":{"monto":"119000.0000","moneda":"COP"},"total_base":{"monto":"119000.0000","moneda":"COP"},"estado_pago":"$estado","fecha_pago":null,
         "motivo_anulacion":null,"notas":null,"recepciones":[],"imagenes":[],"created_at":"2026-09-02T10:00:00+00:00"}
    """.trimIndent()

    @BeforeTest fun arrancar() = servidor.start()
    @AfterTest fun apagar() = servidor.shutdown()

    @Test
    fun `rf fac 001 la factura se registra con clave de idempotencia y montos como cadenas`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(201).setBody(facturaJson()))

        val resultado = repositorio().registrar(
            FacturaNueva(
                proveedorId = "pr1", numero = "FE-1001", fechaEmision = LocalDate.of(2026, 9, 1), fechaVencimiento = null,
                moneda = "COP", tasaCambio = null, baseGravable = "100000", impuesto = "19000", total = "119000", notas = null, recepciones = emptyList(),
            ),
        )

        assertIs<ResultadoFactura.Confirmada>(resultado)
        val peticion = servidor.takeRequest()
        assertTrue(peticion.getHeader("Idempotency-Key") != null)
        val cuerpo = peticion.body.readUtf8()
        assertTrue(cuerpo.contains("\"base_gravable\":{\"monto\":\"100000.0000\",\"moneda\":\"COP\"}"), cuerpo)
        assertTrue(cuerpo.contains("\"fecha_emision\":\"2026-09-01\""), cuerpo)
    }

    @Test
    fun `rf fac 008 el listado trae el total acumulado del filtro`() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"datos":[${facturaJson()}],"cursor_siguiente":null,"tiene_mas":false,"total_filtro":{"monto":"119000.0000","moneda":"COP"},"cantidad_filtro":1}""",
            ),
        )

        val pagina = assertIs<Resultado.Exito<*>>(repositorio().listar(FiltrosFacturas(estadoPago = EstadoPago.PENDIENTE))).valor as co.inventario.data.repositorio.PaginaFacturas

        assertEquals("119000.0000", pagina.totalFiltro.aApi().monto)
        assertEquals(1, pagina.pagina.datos.size)
        assertEquals("pendiente", servidor.takeRequest().requestUrl?.queryParameter("estado_pago"))
    }

    @Test
    fun `rf fac 004 pagar manda la fecha de pago`() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(facturaJson("pagada")))

        val factura = assertIs<Resultado.Exito<co.inventario.domain.modelo.Factura>>(repositorio().pagar("f1", LocalDate.of(2026, 9, 2))).valor

        assertEquals(EstadoPago.PAGADA, factura.estadoPago)
        assertTrue(servidor.takeRequest().body.readUtf8().contains("\"fecha_pago\":\"2026-09-02\""))
    }

    @Test
    fun `rf fac 007 la exportacion devuelve los bytes del zip y su nombre`() = runTest {
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3)
        servidor.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/zip")
                .setHeader("Content-Disposition", "attachment; filename=\"facturas_2026-09-01_2026-09-30.zip\"")
                .setBody(Buffer().write(zip)),
        )

        val exportacion = assertIs<Resultado.Exito<co.inventario.data.repositorio.ArchivoExportado>>(
            repositorio().exportar(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
        ).valor

        assertContentEquals(zip, exportacion.bytes)
        assertEquals("facturas_2026-09-01_2026-09-30.zip", exportacion.nombre)
        val url = servidor.takeRequest().requestUrl!!
        assertEquals("/api/v1/facturas/exportacion", url.encodedPath)
        assertEquals("2026-09-01", url.queryParameter("desde"))
    }
}
