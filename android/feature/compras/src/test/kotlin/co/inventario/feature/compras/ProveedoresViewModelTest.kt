package co.inventario.feature.compras

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

/** RF-COM-001 / RN-17: alta, edición, archivado; con documentos no se elimina, se archiva. */
class ProveedoresViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = RepositorioComprasFalso()

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm() = ProveedoresViewModel(repo).also { dispatcher.scheduler.advanceUntilIdle() }

    @Test
    fun `rn 17 un proveedor con documentos no se elimina y se ofrece archivarlo`() = runTest(dispatcher) {
        val vm = vm()
        vm.eliminar("pr1")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("pr1", vm.estado.value.sugerirArchivar)
        assertTrue(vm.estado.value.error!!.contains("Archívalo"))
        assertTrue(repo.eliminados.isEmpty())
        assertEquals(1, vm.estado.value.proveedores.size)

        vm.archivar("pr1")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("pr1" to true), repo.archivados)
        assertTrue(vm.estado.value.proveedores.none { it.id == "pr1" }, "archivado sale de la lista de activos")
        assertEquals(null, vm.estado.value.sugerirArchivar)
    }

    @Test
    fun `rf com 001 el alta exige nombre y guarda el resto de campos`() = runTest(dispatcher) {
        val vm = vm()
        vm.nuevo()
        vm.cambiarCampo("telefono", "3001234567")
        vm.guardar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("nombre"), vm.estado.value.erroresCampo.keys)
        vm.cambiarCampo("nombre", "Papeles del Sur")
        vm.guardar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Papeles del Sur", repo.creados.single().nombre)
        assertEquals("3001234567", repo.creados.single().telefono)
        assertEquals(null, vm.estado.value.formulario, "al guardar se cierra el formulario")
        assertEquals(2, vm.estado.value.proveedores.size)
    }
}
