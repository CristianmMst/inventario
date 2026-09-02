package co.inventario.feature.escaneo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** RF-CAT-008 / RNF-10: apuntar 5 s a una etiqueta produce una sola lectura (antirrebote 1,5 s). */
class AntirreboteLecturasTest {

    private var ahora = 0L
    private val antirrebote = AntirreboteLecturas(ventanaMs = 1_500) { ahora }

    @Test
    fun `apuntar cinco segundos al mismo codigo produce una lectura`() {
        var aceptadas = 0
        while (ahora <= 5_000) {
            if (antirrebote.aceptar("7701234567890")) aceptadas++
            ahora += 100 // la cámara ve el código unas 10 veces por segundo
        }
        assertEquals(1, aceptadas)
    }

    @Test
    fun `el mismo codigo se vuelve a aceptar tras retirar la etiqueta mas de la ventana`() {
        assertTrue(antirrebote.aceptar("A"))
        ahora = 1_000
        assertTrue(!antirrebote.aceptar("A"))
        ahora = 1_000 + 1_600 // dejó de verse y pasó la ventana completa
        assertTrue(antirrebote.aceptar("A"))
    }

    @Test
    fun `un codigo distinto se acepta de inmediato`() {
        assertTrue(antirrebote.aceptar("A"))
        ahora = 200
        assertTrue(antirrebote.aceptar("B"))
    }
}
