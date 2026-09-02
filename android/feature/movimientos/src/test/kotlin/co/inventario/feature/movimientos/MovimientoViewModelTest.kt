package co.inventario.feature.movimientos

import app.cash.turbine.test
import co.inventario.common.ErrorApp
import co.inventario.data.repositorio.ResultadoMovimiento
import co.inventario.domain.modelo.Cantidad
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RF-INV-005 / RF-INV-006 / RF-INV-001 / RF-INV-010 / RNF-07 / RNF-08: registrar desde la ficha. */
class MovimientoViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = RepositorioMovimientosFalso()

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm(tipo: TipoMovimiento = TipoMovimiento.SALIDA) = MovimientoViewModel(repo, productoId = "p1", tipo = tipo).also {
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `rnf 08 una salida sale con cantidad 1 y el primer motivo, un toque para confirmar`() = runTest(dispatcher) {
        val vm = vm()
        assertEquals("1", vm.estado.value.cantidad)
        assertEquals("venta", vm.estado.value.motivo)
        assertEquals(listOf("venta", "consumo_interno", "otro"), vm.estado.value.motivos.map { it.codigo })
        vm.sucesos.test {
            vm.confirmar()
            val suceso = awaitItem() as MovimientoSuceso.Registrado
            assertEquals("1.000", suceso.movimiento.cantidad.aApi())
        }
        val registro = repo.registros.single()
        assertEquals(TipoMovimiento.SALIDA, registro.tipo)
        assertEquals("1", registro.cantidad)
        assertEquals("venta", registro.motivo)
        assertTrue(!registro.forzar)
    }

    @Test
    fun `rf inv 005 006 el 409 se convierte en dialogo que dice cuanto hay y ofrece forzar con nota`() = runTest(dispatcher) {
        repo.respuestaRegistro = { r ->
            if (r.forzar) ResultadoMovimiento.Confirmado(movimientoDePrueba(cantidad = r.cantidad, forzado = true, stockResultante = "-3"))
            else ResultadoMovimiento.StockInsuficiente(disponible = Cantidad.desde("2"), solicitado = Cantidad.desde(r.cantidad), puedeForzar = true)
        }
        val vm = vm()
        vm.cambiarCantidad("5")
        vm.confirmar()
        dispatcher.scheduler.advanceUntilIdle()

        val dialogo = assertNotNull(vm.estado.value.override)
        assertEquals("2", dialogo.disponible.valor.stripTrailingZeros().toPlainString())
        assertTrue(dialogo.puedeForzar)

        vm.forzar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("nota", vm.estado.value.erroresCampo.keys.single(), "forzar exige motivo escrito (RF-INV-006)")
        assertEquals(1, repo.registros.size)

        vm.cambiarNota("Se vendieron sin registrar ayer")
        vm.sucesos.test {
            vm.forzar()
            val suceso = awaitItem() as MovimientoSuceso.Registrado
            assertTrue(suceso.movimiento.forzado)
        }
        assertTrue(repo.registros.last().forzar)
        assertNull(vm.estado.value.override)
    }

    @Test
    fun `rf inv 010 una merma exige nota aunque el motivo no la exija`() = runTest(dispatcher) {
        val vm = vm(TipoMovimiento.MERMA)
        assertEquals("rotura", vm.estado.value.motivo)
        vm.confirmar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("nota"), vm.estado.value.erroresCampo.keys)
        assertTrue(repo.registros.isEmpty())
    }

    @Test
    fun `rf inv 010 el motivo otro exige nota en cualquier tipo`() = runTest(dispatcher) {
        val vm = vm(TipoMovimiento.SALIDA)
        vm.cambiarMotivo("otro")
        vm.confirmar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("nota"), vm.estado.value.erroresCampo.keys)
        assertTrue(repo.registros.isEmpty())
    }

    @Test
    fun `rf inv 001 un ajuste exige elegir si suma o resta`() = runTest(dispatcher) {
        val vm = vm(TipoMovimiento.AJUSTE)
        vm.cambiarNota("Corrección de la carga inicial")
        vm.confirmar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("direccion"), vm.estado.value.erroresCampo.keys)
        vm.cambiarDireccion(-1)
        vm.sucesos.test {
            vm.confirmar()
            awaitItem()
        }
        assertEquals(-1, repo.registros.single().direccion)
    }

    @Test
    fun `rnf 07 sin red no hay exito falso, queda pendiente con la misma clave y se puede reintentar`() = runTest(dispatcher) {
        repo.respuestaRegistro = { ResultadoMovimiento.Pendiente("clave-1", ErrorApp("SIN_RED", "No se guardó. Sin conexión: reintentando…")) }
        val vm = vm()
        vm.sucesos.test {
            vm.confirmar()
            dispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals("clave-1", vm.estado.value.pendiente)
        assertTrue(vm.estado.value.error!!.startsWith("No se guardó"))

        vm.sucesos.test {
            vm.reintentar()
            awaitItem()
        }
        assertEquals(listOf("clave-1"), repo.reintentos)
        assertEquals(1, repo.registros.size, "el reintento no vuelve a confirmar: reutiliza la operación")
    }

    @Test
    fun `una cantidad con decimales en unidad discreta se rechaza antes de enviar`() = runTest(dispatcher) {
        val vm = vm()
        vm.cambiarCantidad("1.5")
        vm.confirmar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("cantidad"), vm.estado.value.erroresCampo.keys)
        assertTrue(repo.registros.isEmpty())
    }
}
