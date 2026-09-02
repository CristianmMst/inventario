package co.inventario.feature.catalogo

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

/** RF-CAT-009 / RN-14: código desconocido → se informa y se ofrece el alta precargada; nada se crea solo. */
class ResolverCodigoViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = RepositorioCatalogoFalso()

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    @Test
    fun `rf cat 008 un codigo conocido abre la ficha del producto`() = runTest(dispatcher) {
        val vm = ResolverCodigoViewModel(repo)
        vm.sucesos.test {
            vm.resolver("7701234567890")
            assertEquals(ResolucionSuceso.AbrirFicha("p1"), awaitItem())
        }
    }

    @Test
    fun `rf cat 009 un codigo desconocido ofrece crear el producto con el codigo y no crea nada`() = runTest(dispatcher) {
        val vm = ResolverCodigoViewModel(repo)
        vm.resolver("999")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("999", vm.estado.value.codigoDesconocido)
        assertTrue(vm.estado.value.mensaje!!.contains("999"))
        assertTrue(repo.creados.isEmpty(), "RN-14: nada se crea automáticamente")
    }

    @Test
    fun `un fallo de red se muestra y permite reintentar`() = runTest(dispatcher) {
        repo.fallo = co.inventario.common.ErrorApp("SIN_RED", "No se guardó. Sin conexión: reintentando…")
        val vm = ResolverCodigoViewModel(repo)
        vm.resolver("7701234567890")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("SIN_RED", vm.estado.value.error?.codigo)
        repo.fallo = null
        vm.sucesos.test {
            vm.resolver("7701234567890")
            assertEquals(ResolucionSuceso.AbrirFicha("p1"), awaitItem())
        }
    }
}
