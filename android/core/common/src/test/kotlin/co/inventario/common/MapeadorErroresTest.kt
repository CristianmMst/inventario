package co.inventario.common

import co.inventario.common.error.MapeadorErrores
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** RNF-07 y RNF-12: cada `code` de la API tiene su texto en español; nada crudo llega a la UI. */
class MapeadorErroresTest {

    /** Los códigos que emite el backend, extraídos de su código fuente (docs de la constitución §3). */
    private fun codigosDelBackend(): Set<String> {
        val raiz = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "backend").isDirectory && File(it, "docs").isDirectory }
        val fuente = File(raiz, "backend/app").walkTopDown().filter { it.extension == "py" }
        val patron = Regex("""(?:Conflicto|NoEncontrado|ValidacionInvalida|NoAutenticado|SinPermiso)\(\s*"([A-Z_]+)"""")
        return fuente.flatMap { archivo -> patron.findAll(archivo.readText()).map { it.groupValues[1] } }.toSet()
    }

    @Test
    fun `cada code del backend tiene texto propio en espanol`() {
        val sinTexto = codigosDelBackend() - MapeadorErrores.codigosConocidos
        assertEquals(emptySet(), sinTexto, "códigos del backend sin texto en la app")
    }

    @Test
    fun `los textos no son el message crudo ni contienen jerga`() {
        val jerga = listOf("Traceback", "SELECT", "Exception", "null", "None", "uuid", "HTTP")
        for (codigo in MapeadorErrores.codigosConocidos) {
            val texto = MapeadorErrores.mensajePara(codigo)
            assertTrue(texto.isNotBlank(), codigo)
            assertTrue(jerga.none { texto.contains(it) }, "$codigo -> $texto")
            assertTrue(texto.first().isUpperCase() || texto.first() == '¿', "$codigo empieza en minúscula")
        }
    }

    @Test
    fun `un codigo desconocido recibe un texto generico y nunca el mensaje del servidor`() {
        val texto = MapeadorErrores.mensajePara("CODIGO_QUE_NO_EXISTE")
        assertEquals("No se pudo completar la operación. Intenta de nuevo.", texto)
    }

    @Test
    fun `los detalles completan el texto cuando aportan`() {
        assertEquals("Solo hay 2.000 en stock.", MapeadorErrores.mensajePara("STOCK_INSUFICIENTE", mapOf("disponible" to "2.000")))
        val e = MapeadorErrores.error("ERROR_INTERNO", mapOf("request_id" to "abc-123"), requestId = "abc-123")
        assertTrue(e.mensaje.contains("abc-123"))
        assertEquals("abc-123", e.requestId)
        assertTrue(MapeadorErrores.mensajePara("PRODUCTO_NO_ENCONTRADO", mapOf("codigo" to "7701")).contains("7701"))
    }

    @Test
    fun `los fallos de red dicen que no se guardo y que se reintenta`() {
        assertTrue(MapeadorErrores.mensajePara(MapeadorErrores.SIN_RED).startsWith("No se guardó"))
        assertTrue(MapeadorErrores.mensajePara(MapeadorErrores.TIEMPO_AGOTADO).contains("reintentando"))
    }
}
