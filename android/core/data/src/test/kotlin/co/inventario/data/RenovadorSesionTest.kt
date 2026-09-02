package co.inventario.data

import co.inventario.data.red.AlmacenSesionEnMemoria
import co.inventario.data.red.InterceptorAutenticacion
import co.inventario.data.red.RenovadorSesion
import co.inventario.data.red.Tokens
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RF-AUT-003: ante un 401 se renueva y se reintenta una sola vez; dos 401 seguidos cierran sesión. */
class RenovadorSesionTest {

    private val servidor = MockWebServer()
    private val almacen = AlmacenSesionEnMemoria(Tokens("acceso-viejo", "renovacion-1"))
    private var sesionCerrada = false

    private fun cliente(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(InterceptorAutenticacion(almacen))
            .authenticator(RenovadorSesion(almacen, servidor.url("/").toString(), { sesionCerrada = true }))
            .build()

    private fun sesionJson(acceso: String, renovacion: String) = """
        {"token_acceso":"$acceso","tipo":"Bearer","expira_en_segundos":900,"token_renovacion":"$renovacion",
         "usuario":{"id":"u1","email":"m@p.co","nombre":"Marta"},
         "negocio":{"id":"n1","nombre":"P","moneda_base":"COP","zona_horaria":"UTC"}}
    """.trimIndent()

    @BeforeTest fun arrancar() = servidor.start()
    @AfterTest fun apagar() = servidor.shutdown()

    @Test
    fun `ante un 401 renueva y reintenta una sola vez con el token nuevo`() {
        servidor.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"CREDENCIAL_INVALIDA","message":"x","details":{}}}"""))
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(sesionJson("acceso-nuevo", "renovacion-2")))
        servidor.enqueue(MockResponse().setResponseCode(200).setBody("""{"estado":"ok"}"""))

        val respuesta = cliente().newCall(Request.Builder().url(servidor.url("/api/v1/negocio")).build()).execute()

        assertEquals(200, respuesta.code)
        assertEquals(3, servidor.requestCount)
        val primera = servidor.takeRequest()
        assertEquals("Bearer acceso-viejo", primera.getHeader("Authorization"))
        val renovacion = servidor.takeRequest()
        assertEquals("/api/v1/auth/refresh", renovacion.path)
        assertTrue(renovacion.body.readUtf8().contains("renovacion-1"))
        assertNull(renovacion.getHeader("Authorization"), "la renovación no lleva el token caducado")
        val reintento = servidor.takeRequest()
        assertEquals("Bearer acceso-nuevo", reintento.getHeader("Authorization"))
        assertEquals(Tokens("acceso-nuevo", "renovacion-2"), almacen.tokens())
        assertTrue(!sesionCerrada)
    }

    @Test
    fun `dos 401 seguidos cierran la sesion sin bucle`() {
        servidor.enqueue(MockResponse().setResponseCode(401))
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(sesionJson("acceso-nuevo", "renovacion-2")))
        servidor.enqueue(MockResponse().setResponseCode(401))

        val respuesta = cliente().newCall(Request.Builder().url(servidor.url("/api/v1/negocio")).build()).execute()

        assertEquals(401, respuesta.code)
        assertEquals(3, servidor.requestCount, "no hay un cuarto intento")
        assertTrue(sesionCerrada)
        assertNull(almacen.tokens())
    }

    @Test
    fun `si la renovacion falla se cierra la sesion`() {
        servidor.enqueue(MockResponse().setResponseCode(401))
        servidor.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"CREDENCIAL_INVALIDA","message":"x","details":{}}}"""))

        val respuesta = cliente().newCall(Request.Builder().url(servidor.url("/api/v1/negocio")).build()).execute()

        assertEquals(401, respuesta.code)
        assertEquals(2, servidor.requestCount)
        assertTrue(sesionCerrada)
    }

    @Test
    fun `sin sesion no se anade cabecera ni se intenta renovar`() {
        almacen.borrar()
        servidor.enqueue(MockResponse().setResponseCode(401))
        val respuesta = cliente().newCall(Request.Builder().url(servidor.url("/api/v1/negocio")).build()).execute()
        assertEquals(401, respuesta.code)
        assertEquals(1, servidor.requestCount)
        assertNull(servidor.takeRequest().getHeader("Authorization"))
    }
}
