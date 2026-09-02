package co.inventario.feature.catalogo

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

/** RF-CAT-001 / RF-CAT-006 / RNF-05: alta con foto; el producto existe aunque la foto siga subiendo. */
class AltaProductoViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = RepositorioCatalogoFalso()
    private val ambitoSubida = CoroutineScope(dispatcher)

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm(codigo: String? = null, productoId: String? = null) =
        AltaProductoViewModel(repo, ambitoSubida, codigoBarras = codigo, productoId = productoId, monedaBase = "COP")

    @Test
    fun `rf cat 009 el alta llega con el codigo escaneado precargado`() = runTest(dispatcher) {
        val vm = vm(codigo = "999")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("999", vm.estado.value.codigoBarras)
        assertEquals(listOf("unidad", "kg"), vm.estado.value.unidades.map { it.codigo })
    }

    @Test
    fun `rf cat 001 nombre y unidad son obligatorios`() = runTest(dispatcher) {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.cambiarUnidad("")
        vm.guardar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("nombre", "unidad"), vm.estado.value.erroresCampo.keys)
        assertTrue(repo.creados.isEmpty())
    }

    @Test
    fun `rnf 05 el producto se crea y navega aunque la foto siga subiendo`() = runTest(dispatcher) {
        val vm = vm(codigo = "999")
        dispatcher.scheduler.advanceUntilIdle()
        vm.cambiarNombre("Cuaderno rayado")
        vm.cambiarUnidad("unidad")
        vm.cambiarCosto("2500")
        vm.adjuntarFoto(byteArrayOf(1, 2, 3))

        vm.sucesos.test {
            vm.guardar()
            val suceso = awaitItem() as AltaSuceso.ProductoGuardado
            assertEquals("nuevo-1", suceso.productoId)
        }
        assertEquals(1, repo.creados.size)
        assertEquals("999", repo.creados.single().codigosBarras.single())
        assertEquals("2500", repo.creados.single().costoActual)
        assertTrue(repo.imagenesSubidas.isEmpty(), "la subida sigue en curso y no bloqueó la navegación")

        repo.subidaTermina.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("nuevo-1", repo.imagenesSubidas.single().first)
    }

    @Test
    fun `rf cat 001 en edicion se cargan los datos y se envia un patch`() = runTest(dispatcher) {
        val vm = vm(productoId = "p1")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Cuaderno 100 hojas", vm.estado.value.nombre)
        vm.cambiarNombre("Cuaderno 100 hojas rayado")
        vm.sucesos.test {
            vm.guardar()
            assertEquals(AltaSuceso.ProductoGuardado("p1"), awaitItem())
        }
        assertEquals("Cuaderno 100 hojas rayado", repo.editados.single().second.nombre)
        assertTrue(repo.creados.isEmpty())
    }
}
