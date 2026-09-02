package co.inventario.feature.facturas

import co.inventario.common.ErrorApp
import co.inventario.common.Resultado
import co.inventario.domain.modelo.EstadoPago
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** RF-FAC-004 / RF-FAC-006 / RF-FAC-007 / RF-FAC-008: listado con total, pago con fecha, exportación. */
class FacturasListadoViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = RepositorioFacturasFalso()

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm() = FacturasListadoViewModel(repo).also { dispatcher.scheduler.advanceUntilIdle() }

    @Test
    fun `rf fac 008 el listado muestra el total del filtro aplicado`() = runTest(dispatcher) {
        val vm = vm()
        assertEquals("169000.0000", vm.estado.value.totalFiltro?.aApi()?.monto)
        vm.filtrarPorEstado(EstadoPago.PENDIENTE); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("f1"), vm.estado.value.facturas.map { it.id })
        assertEquals("119000.0000", vm.estado.value.totalFiltro?.aApi()?.monto)
    }

    @Test
    fun `rf fac 004 pagar exige fecha y la factura sale del filtro de pendientes`() = runTest(dispatcher) {
        val vm = vm()
        vm.filtrarPorEstado(EstadoPago.PENDIENTE); dispatcher.scheduler.advanceUntilIdle()
        vm.pedirPago("f1")
        vm.confirmarPago(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("fechaPago"), vm.estado.value.erroresCampo.keys)
        assertTrue(repo.pagos.isEmpty())
        vm.cambiarFechaPago("2026-09-02")
        vm.confirmarPago(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("f1" to LocalDate.of(2026, 9, 2)), repo.pagos)
        assertTrue(vm.estado.value.facturas.none { it.id == "f1" }, "ya no está entre las pendientes")
    }

    @Test
    fun `rf fac 007 la exportacion deja el archivo listo para compartir y no finge exito si falla`() = runTest(dispatcher) {
        val vm = vm()
        vm.exportar(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("facturas_2026-09-01_2026-09-30.zip", vm.estado.value.archivoListo?.nombre)
        vm.archivoEntregado()
        assertEquals(null, vm.estado.value.archivoListo)

        repo.exportacion = Resultado.Fallo(ErrorApp("SIN_RED", "No se guardó. Sin conexión: reintentando…"))
        vm.exportar(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, vm.estado.value.archivoListo)
        assertTrue(vm.estado.value.error!!.contains("Sin conexión"))
    }
}
