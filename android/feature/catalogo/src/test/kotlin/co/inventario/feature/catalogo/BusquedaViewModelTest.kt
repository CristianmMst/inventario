package co.inventario.feature.catalogo

import co.inventario.data.repositorio.FiltrosProductos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** RF-CAT-007 / RF-CAT-014: búsqueda por texto con antirrebote y listado con filtros. */
class BusquedaViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = RepositorioCatalogoFalso()

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    @Test
    fun `rf cat 007 teclear rapido produce una sola busqueda tras el antirrebote`() = runTest(dispatcher) {
        val vm = BusquedaViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        repo.listados.clear()
        vm.cambiarTexto("c"); vm.cambiarTexto("cu"); vm.cambiarTexto("cua"); vm.cambiarTexto("cuad")
        dispatcher.scheduler.advanceTimeBy(100)
        assertEquals(emptyList(), repo.busquedas)
        dispatcher.scheduler.advanceTimeBy(400)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf("cuad"), repo.busquedas)
        assertEquals(listOf("Cuaderno 100 hojas"), vm.estado.value.resultados.map { it.nombre })
    }

    @Test
    fun `rf cat 014 sin texto se lista con los filtros elegidos`() = runTest(dispatcher) {
        val vm = BusquedaViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.cambiarFiltros(FiltrosProductos(condicionStock = "bajo_minimo", estado = "activo"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FiltrosProductos(estado = "activo", condicionStock = "bajo_minimo"), repo.listados.last())
        assertEquals(1, vm.estado.value.resultados.size)
    }
}
