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
import kotlin.test.assertTrue

/** RF-COM-002: orden opcional con proveedor, líneas y costo estimado; nace en borrador. */
class NuevaOrdenViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = RepositorioComprasFalso()

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm() = NuevaOrdenViewModel(repo, monedaBase = "COP").also { dispatcher.scheduler.advanceUntilIdle() }

    @Test
    fun `una orden sin proveedor o sin lineas no se guarda`() = runTest(dispatcher) {
        val vm = vm()
        vm.guardar(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("proveedor", "lineas"), vm.estado.value.erroresCampo.keys)
        assertTrue(repo.ordenesCreadas.isEmpty())
    }

    @Test
    fun `rf com 002 la orden se crea con sus lineas y costo estimado`() = runTest(dispatcher) {
        val vm = vm()
        vm.elegirProveedor("pr1")
        vm.agregarLinea(PRODUCTO)
        vm.cambiarCantidad(0, "10"); vm.cambiarCosto(0, "2500")
        vm.cambiarNotas("Pedido de septiembre")
        vm.sucesos.test {
            vm.guardar()
            val s = awaitItem() as NuevaOrdenSuceso.Creada
            assertEquals("o1", s.ordenId)
        }
        val creada = repo.ordenesCreadas.single()
        assertEquals("pr1", creada.proveedorId)
        assertEquals("2500", creada.lineas.single().costoUnitarioEstimado)
        assertEquals("Pedido de septiembre", creada.notas)
    }
}
