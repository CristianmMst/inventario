package co.inventario.feature.reportes

import co.inventario.common.Resultado
import co.inventario.data.repositorio.RepositorioReportes
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Categoria
import co.inventario.domain.modelo.Dinero
import co.inventario.domain.modelo.FilaAgotado
import co.inventario.domain.modelo.FilaBajoMinimo
import co.inventario.domain.modelo.FilaDiscrepancia
import co.inventario.domain.modelo.FilaNoValorizable
import co.inventario.domain.modelo.FilaSinMovimiento
import co.inventario.domain.modelo.Moneda
import co.inventario.domain.modelo.Pagina
import co.inventario.domain.modelo.ProductoBreve
import co.inventario.domain.modelo.ResumenCompras
import co.inventario.domain.modelo.ResumenMermas
import co.inventario.domain.modelo.ValorCategoria
import co.inventario.domain.modelo.Valorizacion
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

/** RF-REP-001 / RF-REP-003 / RF-REP-005 / RF-REP-006: los siete reportes desde la app. */
class ReportesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val cop = Moneda("COP")

    private fun producto(id: String) = ProductoBreve(id, "Producto $id", "S-$id", "unidad")

    /** El servidor ya ordena por criticidad (RF-REP-001); la app no debe reordenar. */
    private val repo = object : RepositorioReportes {
        var pedidos = mutableListOf<String>()
        override suspend fun bajoMinimo(cursor: String?) = Resultado.Exito(
            Pagina(
                listOf(
                    FilaBajoMinimo(producto("a"), Cantidad.desde("0"), Cantidad.desde("5"), Cantidad.desde("5"), "1.000"),
                    FilaBajoMinimo(producto("b"), Cantidad.desde("4"), Cantidad.desde("5"), Cantidad.desde("1"), "0.200"),
                ),
                null, false,
            ),
        )
        override suspend fun agotados(cursor: String?) = Resultado.Exito(Pagina(listOf(FilaAgotado(producto("a"), Cantidad.CERO, null)), null, false))
        override suspend fun sinMovimiento(dias: Int, cursor: String?): Resultado<Pagina<FilaSinMovimiento>> { pedidos += "sin-movimiento:$dias"; return Resultado.Exito(Pagina.vacia()) }
        override suspend fun valorizacion() = Resultado.Exito(
            Valorizacion(
                Dinero.desde("250000", cop), 3,
                listOf(ValorCategoria(Categoria("c1", "Papelería"), 3, Dinero.desde("250000", cop))),
                Pagina(listOf(FilaNoValorizable(producto("z"), Cantidad.desde("7"))), null, false),
            ),
        )
        override suspend fun compras(desde: LocalDate, hasta: LocalDate): Resultado<ResumenCompras> {
            pedidos += "compras:$desde:$hasta"
            return Resultado.Exito(ResumenCompras(desde.toString(), hasta.toString(), Dinero.desde("0", cop), Dinero.desde("0", cop), 0, 0, emptyList(), emptyList()))
        }
        override suspend fun mermas(desde: LocalDate, hasta: LocalDate) =
            Resultado.Exito(ResumenMermas(desde.toString(), hasta.toString(), Cantidad.CERO, Dinero.desde("0", cop), emptyList(), Pagina.vacia()))
        override suspend fun discrepancias(cursor: String?) = Resultado.Exito(Pagina.vacia<FilaDiscrepancia>())
    }

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm() = ReportesViewModel(repo).also { dispatcher.scheduler.advanceUntilIdle() }

    @Test
    fun `rf rep 001 bajo minimo conserva el orden por urgencia del servidor`() = runTest(dispatcher) {
        val vm = vm()
        vm.abrir(Reporte.BAJO_MINIMO); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("a", "b"), vm.estado.value.bajoMinimo.map { it.producto.id })
        assertEquals("5.000", vm.estado.value.bajoMinimo.first().deficit.aApi())
    }

    @Test
    fun `rf rep 003 los no valorizables se ven aparte y no suman cero`() = runTest(dispatcher) {
        val vm = vm()
        vm.abrir(Reporte.VALORIZACION); dispatcher.scheduler.advanceUntilIdle()
        val v = vm.estado.value.valorizacion!!
        assertEquals("250000.0000", v.total.aApi().monto)
        assertEquals(listOf("z"), v.noValorizables.datos.map { it.producto.id })
    }

    @Test
    fun `rf rep 005 el rango de fechas y los dias viajan como los teclea la usuaria`() = runTest(dispatcher) {
        val vm = vm()
        vm.cambiarRango(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        vm.abrir(Reporte.COMPRAS); dispatcher.scheduler.advanceUntilIdle()
        vm.cambiarDias(120)
        vm.abrir(Reporte.SIN_MOVIMIENTO); dispatcher.scheduler.advanceUntilIdle()
        assertTrue("compras:2026-08-01:2026-08-31" in repo.pedidos)
        assertTrue("sin-movimiento:120" in repo.pedidos)
    }
}
