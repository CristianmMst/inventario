package co.inventario.feature.facturas

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
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

/** RF-FAC-001 / RF-FAC-003 / RN-18 / RF-FAC-005: el cuadre se valida antes de enviar; la foto se sube aparte. */
class FacturaViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = RepositorioFacturasFalso()

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm() = FacturaViewModel(repo, CoroutineScope(dispatcher), monedaBase = "COP").also { dispatcher.scheduler.advanceUntilIdle() }

    private fun FacturaViewModel.llenar() {
        elegirProveedor("pr1"); cambiarNumero("FE-1001"); cambiarFechaEmision("2026-09-01")
        cambiarBase("100000"); cambiarImpuesto("19000")
    }

    @Test
    fun `rn 18 si base mas impuesto no da el total se muestra la diferencia y no se envia`() = runTest(dispatcher) {
        val vm = vm()
        vm.llenar(); vm.cambiarTotal("120000")
        assertEquals("1000.0000", vm.estado.value.diferenciaCuadre, "la diferencia se ve mientras se teclea")
        vm.guardar(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.estado.value.erroresCampo.containsKey("total"))
        assertTrue(repo.registradas.isEmpty())
    }

    @Test
    fun `rf fac 001 con cuadre exacto se registra y la foto se sube despues sin bloquear`() = runTest(dispatcher) {
        val vm = vm()
        vm.llenar(); vm.cambiarTotal("119000")
        assertEquals(null, vm.estado.value.diferenciaCuadre)
        vm.adjuntarFoto(byteArrayOf(9, 9))
        vm.sucesos.test {
            vm.guardar()
            val s = awaitItem() as FacturaSuceso.Registrada
            assertEquals("f3", s.facturaId)
        }
        assertEquals("119000", repo.registradas.single().total)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("f3", repo.imagenes.single().first)
    }

    @Test
    fun `rf fac 003 en otra moneda la tasa es obligatoria`() = runTest(dispatcher) {
        val vm = vm()
        vm.llenar(); vm.cambiarTotal("119000"); vm.cambiarMoneda("USD")
        vm.guardar(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("tasaCambio"), vm.estado.value.erroresCampo.keys)
        vm.cambiarTasa("4100")
        vm.sucesos.test { vm.guardar(); awaitItem() }
        assertEquals("4100", repo.registradas.single().tasaCambio)
    }
}
