package co.inventario.feature.movimientos

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

/** RF-INV-013: se declara lo contado; la diferencia se ve antes de confirmar; cero no crea nada. */
class ConteoViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = RepositorioMovimientosFalso()

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm() = ConteoViewModel(repo, productoId = "p1").also { dispatcher.scheduler.advanceUntilIdle() }

    @Test
    fun `rf inv 013 contar 8 sobre 10 muestra menos 2 antes de confirmar y registra el ajuste`() = runTest(dispatcher) {
        val vm = vm()
        assertEquals("10", vm.estado.value.stockActual)
        vm.cambiarContada("8")
        assertEquals("-2", vm.estado.value.diferencia)
        assertTrue(repo.conteos.isEmpty(), "nada se envía hasta confirmar")
        vm.confirmar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("nota"), vm.estado.value.erroresCampo.keys, "RF-INV-010: un conteo es un ajuste y exige nota")
        vm.cambiarNota("Conteo de cierre en la estantería 3")
        vm.sucesos.test {
            vm.confirmar()
            val suceso = awaitItem() as ConteoSuceso.Registrado
            assertEquals("-2.000", suceso.conteo.diferencia.aApi())
        }
        assertEquals("p1" to "8", repo.conteos.single())
    }

    @Test
    fun `rf inv 013 contar 10 avisa que coincidia y no envia nada`() = runTest(dispatcher) {
        val vm = vm()
        vm.cambiarContada("10")
        assertEquals("0", vm.estado.value.diferencia)
        vm.confirmar()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.estado.value.aviso!!.contains("coincide"))
        assertTrue(repo.conteos.isEmpty())
    }

    @Test
    fun `una cantidad vacia o negativa no se acepta`() = runTest(dispatcher) {
        val vm = vm()
        vm.confirmar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("contada"), vm.estado.value.erroresCampo.keys)
    }
}
