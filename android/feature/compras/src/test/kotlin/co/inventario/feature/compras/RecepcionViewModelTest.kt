package co.inventario.feature.compras

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RF-COM-004 / RF-COM-005 / RF-COM-008 / RF-COM-009: recepción directa y contra orden, parcial y exceso. */
class RecepcionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = RepositorioComprasFalso()

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm(ordenId: String? = null) = RecepcionViewModel(repo, ordenId = ordenId, monedaBase = "COP").also { dispatcher.scheduler.advanceUntilIdle() }

    @Test
    fun `rf com 004 recibir sin orden funciona en el flujo principal`() = runTest(dispatcher) {
        val vm = vm()
        vm.elegirProveedor("pr1")
        vm.agregarLinea(PRODUCTO)
        vm.cambiarCantidad(0, "12"); vm.cambiarCosto(0, "2500")
        vm.sucesos.test {
            vm.confirmar()
            val s = awaitItem() as RecepcionSuceso.Confirmada
            assertEquals("RC-000001", s.recepcion.numero)
        }
        val creada = repo.recepcionesCreadas.single()
        assertNull(creada.ordenId)
        assertEquals("12", creada.lineas.single().cantidad)
        assertEquals(listOf("r1" to false), repo.confirmaciones)
    }

    @Test
    fun `rf com 005 contra orden las lineas llegan precargadas con lo pendiente`() = runTest(dispatcher) {
        val vm = vm(ordenId = "o1")
        assertEquals("pr1", vm.estado.value.proveedorId)
        assertEquals("10", vm.estado.value.lineas.single().cantidad)
        assertEquals("2500", vm.estado.value.lineas.single().costo, "el costo estimado de la orden se propone")
    }

    @Test
    fun `rf com 009 recibir de mas pide confirmacion explicita y solo entonces entra`() = runTest(dispatcher) {
        repo.excesoHastaConfirmar = true
        val vm = vm(ordenId = "o1")
        vm.cambiarCantidad(0, "12")
        vm.confirmar()
        dispatcher.scheduler.advanceUntilIdle()
        val aviso = assertNotNull(vm.estado.value.avisoExceso)
        assertEquals("OC-000001", aviso.ordenNumero)
        assertEquals(listOf("r1" to false), repo.confirmaciones)

        vm.sucesos.test {
            vm.confirmarExceso()
            val s = awaitItem() as RecepcionSuceso.Confirmada
            assertTrue(s.recepcion.lineas.single().exceso)
        }
        assertEquals(listOf("r1" to false, "r1" to true), repo.confirmaciones)
        assertEquals(1, repo.recepcionesCreadas.size, "el borrador no se vuelve a crear")
    }

    @Test
    fun `una recepcion sin lineas o sin costo no se envia`() = runTest(dispatcher) {
        val vm = vm()
        vm.elegirProveedor("pr1")
        vm.confirmar(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("lineas"), vm.estado.value.erroresCampo.keys)
        vm.agregarLinea(PRODUCTO); vm.cambiarCantidad(0, "3")
        vm.confirmar(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.estado.value.erroresCampo.keys.any { it.startsWith("costo") })
        assertTrue(repo.recepcionesCreadas.isEmpty())
    }
}
