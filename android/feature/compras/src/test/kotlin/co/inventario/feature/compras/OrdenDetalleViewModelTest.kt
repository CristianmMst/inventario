package co.inventario.feature.compras

import co.inventario.domain.modelo.EstadoOrden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** RF-COM-002 / RF-COM-003 / RF-COM-010: borrador, emitir, cancelar; emitida bloquea las líneas. */
class OrdenDetalleViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = RepositorioComprasFalso()

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm() = OrdenDetalleViewModel(repo, ordenId = "o1").also { dispatcher.scheduler.advanceUntilIdle() }

    @Test
    fun `rf com 003 emitir bloquea la edicion de lineas`() = runTest(dispatcher) {
        val vm = vm()
        assertTrue(vm.estado.value.puedeEditarLineas)
        vm.emitir()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(EstadoOrden.EMITIDA, vm.estado.value.orden?.estado)
        assertTrue(!vm.estado.value.puedeEditarLineas)
        assertTrue(vm.estado.value.puedeRecibir)
        assertEquals(listOf("o1"), repo.emitidas)
    }

    @Test
    fun `rf com 010 cancelar exige motivo y solo aplica sin recepciones`() = runTest(dispatcher) {
        val vm = vm()
        vm.emitir(); dispatcher.scheduler.advanceUntilIdle()
        vm.pedirCancelacion()
        vm.confirmarCierre()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("motivo"), vm.estado.value.erroresCampo.keys)
        vm.cambiarMotivo("El proveedor no tiene existencias")
        vm.confirmarCierre()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("o1" to "El proveedor no tiene existencias"), repo.canceladas)
        assertEquals(EstadoOrden.CANCELADA, vm.estado.value.orden?.estado)
    }

    @Test
    fun `rf com 008 una orden parcial no se cancela, se cierra con faltante`() = runTest(dispatcher) {
        repo.orden = ordenDePrueba(EstadoOrden.PARCIALMENTE_RECIBIDA, pendiente = "4")
        val vm = vm()
        assertTrue(!vm.estado.value.puedeCancelar)
        assertTrue(vm.estado.value.puedeCerrarConFaltante)
        assertEquals("4.000", vm.estado.value.orden?.lineas?.single()?.cantidadPendiente?.aApi())
        vm.pedirCierreConFaltante(); vm.cambiarMotivo("No llegará el resto"); vm.confirmarCierre()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("o1" to "No llegará el resto"), repo.cerradas)
        assertTrue(repo.canceladas.isEmpty())
    }
}
