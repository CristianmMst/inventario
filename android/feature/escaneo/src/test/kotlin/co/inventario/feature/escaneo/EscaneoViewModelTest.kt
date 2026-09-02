package co.inventario.feature.escaneo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RNF-15: denegar la cámara deja todos los flujos completables tecleando el código. */
class EscaneoViewModelTest {

    @Test
    fun `permiso denegado abre el teclado y no bloquea`() {
        val vm = EscaneoViewModel()
        vm.permisoResuelto(false)
        assertEquals(EstadoPermiso.DENEGADO, vm.estado.value.permiso)
        assertTrue(vm.estado.value.tecleando)
        vm.cambiarCodigo(" 7701234567890 ")
        assertEquals("7701234567890", vm.codigoConfirmado())
    }

    @Test
    fun `un codigo vacio no se busca y se explica`() {
        val vm = EscaneoViewModel()
        assertNull(vm.codigoConfirmado())
        assertTrue(vm.estado.value.errorCodigo!!.isNotBlank())
    }

    @Test
    fun `la linterna alterna`() {
        val vm = EscaneoViewModel()
        vm.alternarLinterna()
        assertTrue(vm.estado.value.linterna)
        vm.alternarLinterna()
        assertTrue(!vm.estado.value.linterna)
    }
}
