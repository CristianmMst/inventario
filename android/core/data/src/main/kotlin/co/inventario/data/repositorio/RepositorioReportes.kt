package co.inventario.data.repositorio

import co.inventario.common.Resultado
import co.inventario.data.mapeo.aDominio
import co.inventario.data.red.InventarioApi
import co.inventario.data.red.llamada
import co.inventario.domain.modelo.FilaAgotado
import co.inventario.domain.modelo.FilaBajoMinimo
import co.inventario.domain.modelo.FilaDiscrepancia
import co.inventario.domain.modelo.FilaSinMovimiento
import co.inventario.domain.modelo.Pagina
import co.inventario.domain.modelo.ResumenCompras
import co.inventario.domain.modelo.ResumenMermas
import co.inventario.domain.modelo.Valorizacion
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** RF-REP-001..007 / RF-REP-008: los mismos parámetros que la API; el orden lo decide el servidor. */
interface RepositorioReportes {
    suspend fun bajoMinimo(cursor: String? = null): Resultado<Pagina<FilaBajoMinimo>>
    suspend fun agotados(cursor: String? = null): Resultado<Pagina<FilaAgotado>>
    suspend fun sinMovimiento(dias: Int = 90, cursor: String? = null): Resultado<Pagina<FilaSinMovimiento>>
    suspend fun valorizacion(): Resultado<Valorizacion>
    suspend fun compras(desde: LocalDate, hasta: LocalDate): Resultado<ResumenCompras>
    suspend fun mermas(desde: LocalDate, hasta: LocalDate): Resultado<ResumenMermas>
    suspend fun discrepancias(cursor: String? = null): Resultado<Pagina<FilaDiscrepancia>>
}

@Singleton
class RepositorioReportesApi @Inject constructor(private val api: InventarioApi) : RepositorioReportes {
    override suspend fun bajoMinimo(cursor: String?) = llamada({ api.reporteBajoMinimo(cursor) }) { it.aDominio { f -> f.aDominio() } }
    override suspend fun agotados(cursor: String?) = llamada({ api.reporteAgotados(cursor) }) { it.aDominio { f -> f.aDominio() } }
    override suspend fun sinMovimiento(dias: Int, cursor: String?) = llamada({ api.reporteSinMovimiento(dias, cursor) }) { it.aDominio { f -> f.aDominio() } }
    override suspend fun valorizacion() = llamada({ api.reporteValorizacion() }) { it.aDominio() }
    override suspend fun compras(desde: LocalDate, hasta: LocalDate) = llamada({ api.reporteCompras(desde.toString(), hasta.toString()) }) { it.aDominio() }
    override suspend fun mermas(desde: LocalDate, hasta: LocalDate) = llamada({ api.reporteMermas(desde.toString(), hasta.toString()) }) { it.aDominio() }
    override suspend fun discrepancias(cursor: String?) = llamada({ api.reporteDiscrepancias(cursor) }) { it.aDominio { f -> f.aDominio() } }
}
