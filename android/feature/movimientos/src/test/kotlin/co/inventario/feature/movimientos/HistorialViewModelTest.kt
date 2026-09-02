package co.inventario.feature.movimientos

import co.inventario.domain.modelo.TipoMovimiento
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

/** RF-INV-012 / RF-INV-008: historial con stock resultante; anular exige motivo; no hay edición. */
class HistorialViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = RepositorioMovimientosFalso()

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm() = HistorialViewModel(repo, productoId = "p1").also { dispatcher.scheduler.advanceUntilIdle() }

    @Test
    fun `rf inv 012 el historial muestra cada movimiento con su stock resultante`() = runTest(dispatcher) {
        val vm = vm()
        val fila = vm.estado.value.movimientos.single()
        assertEquals("m1", fila.id)
        assertEquals("9.000", fila.stockResultante.aApi())
    }

    @Test
    fun `rf inv 008 anular exige nota y deja anulado y contramovimiento juntos`() = runTest(dispatcher) {
        val vm = vm()
        vm.pedirAnulacion("m1")
        vm.confirmarAnulacion()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("nota", vm.estado.value.erroresCampo.keys.single())
        assertTrue(repo.anulaciones.isEmpty())

        vm.cambiarNota("Se registró dos veces")
        vm.confirmarAnulacion()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("m1" to "Se registró dos veces", repo.anulaciones.single())
        val filas = vm.estado.value.movimientos
        assertEquals(2, filas.size)
        assertEquals(TipoMovimiento.CONTRAMOVIMIENTO, filas[0].tipo)
        assertEquals("m1", filas[0].anulaMovimientoId)
        assertTrue(filas[1].anulado)
        assertTrue(vm.estado.value.anulando == null)
    }

    @Test
    fun `rf inv 008 un anulado o un contramovimiento no ofrecen anulacion`() = runTest(dispatcher) {
        repo.historial = listOf(
            movimientoDePrueba(id = "c1", tipo = TipoMovimiento.CONTRAMOVIMIENTO, anulaMovimientoId = "m1"),
            movimientoDePrueba(id = "m1", anuladoEn = java.time.Instant.parse("2026-09-02T11:00:00Z")),
            movimientoDePrueba(id = "m2"),
        )
        val vm = vm()
        assertEquals(listOf(false, false, true), vm.estado.value.movimientos.map { vm.sePuedeAnular(it) })
    }
}
