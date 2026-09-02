package co.inventario.app

import co.inventario.app.navegacion.Ruta
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** T-074: las rutas son objetos serializables; los argumentos van tipados. */
class RutasTest {

    @Test
    fun `las rutas con argumentos van y vuelven por serializacion`() {
        val ruta = Ruta.Movimiento(productoId = "p-1", tipo = "salida")
        val json = Json.encodeToString(Ruta.Movimiento.serializer(), ruta)
        assertEquals(ruta, Json.decodeFromString(Ruta.Movimiento.serializer(), json))
    }

    @Test
    fun `el alta precargada lleva el codigo escaneado como argumento opcional`() {
        val sin = Ruta.AltaProducto()
        val con = Ruta.AltaProducto(codigoBarras = "7701")
        assertEquals(null, sin.codigoBarras)
        assertEquals("7701", con.codigoBarras)
    }

    @Test
    fun `las rutas sin argumentos son singletons`() {
        assertEquals(Ruta.Login, Json.decodeFromString(Ruta.Login.serializer(), Json.encodeToString(Ruta.Login.serializer(), Ruta.Login)))
    }
}
