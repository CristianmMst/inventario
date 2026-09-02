package co.inventario.data

import co.inventario.data.mapeo.aDominio
import co.inventario.data.mapeo.aDto
import co.inventario.data.red.Json
import co.inventario.data.red.dto.DineroDto
import co.inventario.data.red.dto.ErrorEnvoltorioDto
import co.inventario.data.red.dto.MovimientoDto
import co.inventario.data.red.dto.PaginaDto
import co.inventario.data.red.dto.ProductoDto
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Dinero
import co.inventario.domain.modelo.Moneda
import co.inventario.domain.modelo.TipoMovimiento
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-070: mapeo ida y vuelta; el dinero se deserializa desde cadena, nunca a Double (E-01). */
class MapeadoresTest {

    private val productoJson = """
        {
          "id": "8b1c0000-0000-0000-0000-000000000001",
          "sku": "CUAD-100",
          "nombre": "Cuaderno 100 hojas",
          "categoria": {"id": "c1", "nombre": "Cuadernos"},
          "unidad": {"codigo": "unidad", "nombre": "Unidad", "tipo": "discreta", "decimales": 0},
          "costo_actual": {"monto": "2500.5000", "moneda": "COP"},
          "precio_venta": null,
          "stock_minimo": "10.000",
          "stock_actual": "7.000",
          "estado": "activo",
          "codigos_barras": ["7701234567890"],
          "imagen": {"id": "i1", "url": "/api/v1/imagenes/abc?t=x", "mime": "image/jpeg", "ancho": 800, "alto": 600, "bytes": 1234},
          "campo_nuevo_del_servidor": true
        }
    """.trimIndent()

    @Test
    fun `el producto de la api se mapea al dominio con dinero y cantidades exactas`() {
        val dto = Json.decodeFromString(ProductoDto.serializer(), productoJson)
        val producto = dto.aDominio()
        assertEquals("CUAD-100", producto.sku)
        assertEquals(Dinero(BigDecimal("2500.5000"), Moneda("COP")), producto.costoActual)
        assertNull(producto.precioVenta)
        assertEquals(Cantidad.desde("10"), producto.stockMinimo)
        assertEquals(Cantidad.desde("7"), producto.stockActual)
        assertTrue(producto.bajoMinimo)
        assertEquals(listOf("7701234567890"), producto.codigosBarras)
        assertEquals("/api/v1/imagenes/abc?t=x", producto.imagenUrl)
        assertEquals("Cuadernos", producto.categoria?.nombre)
    }

    @Test
    fun `el dinero va y vuelve como cadena decimal con su moneda`() {
        val dinero = Dinero.desde("12.5", Moneda("COP"))
        val json = Json.encodeToString(DineroDto.serializer(), dinero.aDto())
        assertEquals("""{"monto":"12.5000","moneda":"COP"}""", json)
        assertEquals(dinero, Json.decodeFromString(DineroDto.serializer(), json).aDominio())
    }

    @Test
    fun `un monto numerico en el json se rechaza en vez de convertirse a double`() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString(DineroDto.serializer(), """{"monto": 12.5, "moneda": "COP"}""")
        }
    }

    @Test
    fun `el movimiento de la api se mapea con tipo origen y momento`() {
        val json = """
            {
              "id": "m1", "producto_id": "p1", "tipo": "salida", "cantidad": "3.000", "direccion": -1,
              "motivo": "venta", "nota": null, "forzado": false, "stock_resultante": "7.000",
              "origen": "app", "autor": {"tipo": "usuario", "id": "u1"},
              "ocurrido_en": "2026-09-02T10:15:30.123456+00:00", "anulado_en": null,
              "anula_movimiento_id": null, "recepcion_id": null, "recepcion_linea_id": null
            }
        """.trimIndent()
        val movimiento = Json.decodeFromString(MovimientoDto.serializer(), json).aDominio()
        assertEquals(TipoMovimiento.SALIDA, movimiento.tipo)
        assertEquals(Cantidad.desde("3"), movimiento.cantidad)
        assertEquals(2026, movimiento.ocurridoEn.atZone(java.time.ZoneOffset.UTC).year)
        assertTrue(!movimiento.anulado)
    }

    @Test
    fun `la pagina generica conserva cursor y tiene_mas`() {
        val json = """{"datos": ["a", "b"], "cursor_siguiente": "xyz", "tiene_mas": true}"""
        val pagina = Json.decodeFromString(PaginaDto.serializer(String.serializer()), json)
        assertEquals(listOf("a", "b"), pagina.datos)
        assertEquals("xyz", pagina.cursorSiguiente)
        assertTrue(pagina.tieneMas)
    }

    @Test
    fun `el sobre de error de la api se lee con code message y details`() {
        val json = """
            {"error": {"code": "STOCK_INSUFICIENTE", "message": "Solo hay 2.", "details": {"disponible": "2.000", "puede_forzar": true}}}
        """.trimIndent()
        val error = Json.decodeFromString(ErrorEnvoltorioDto.serializer(), json).error
        assertEquals("STOCK_INSUFICIENTE", error.code)
        assertEquals("2.000", error.detalle("disponible"))
        assertEquals("true", error.detalle("puede_forzar"))
    }
}
