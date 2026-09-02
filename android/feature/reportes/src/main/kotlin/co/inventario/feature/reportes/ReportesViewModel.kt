package co.inventario.feature.reportes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.common.error.MapeadorErrores
import co.inventario.data.repositorio.RepositorioReportes
import co.inventario.domain.modelo.FilaAgotado
import co.inventario.domain.modelo.FilaBajoMinimo
import co.inventario.domain.modelo.FilaDiscrepancia
import co.inventario.domain.modelo.FilaSinMovimiento
import co.inventario.domain.modelo.ResumenCompras
import co.inventario.domain.modelo.ResumenMermas
import co.inventario.domain.modelo.Valorizacion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Los siete reportes de la spec (RF-REP-001..007; el historial por producto vive en la ficha). */
enum class Reporte(val titulo: String, val requiereRango: Boolean = false) {
    BAJO_MINIMO("Bajo stock mínimo"),
    AGOTADOS("Agotados"),
    SIN_MOVIMIENTO("Sin movimiento"),
    VALORIZACION("Valorización a costo"),
    COMPRAS("Compras por período", requiereRango = true),
    MERMAS("Mermas por período", requiereRango = true),
    DISCREPANCIAS("Movimientos forzados"),
}

data class ReportesUiState(
    val abierto: Reporte? = null,
    val desde: LocalDate = LocalDate.now().withDayOfMonth(1),
    val hasta: LocalDate = LocalDate.now(),
    val dias: Int = 90,
    val cargando: Boolean = false,
    val error: String? = null,
    val bajoMinimo: List<FilaBajoMinimo> = emptyList(),
    val agotados: List<FilaAgotado> = emptyList(),
    val sinMovimiento: List<FilaSinMovimiento> = emptyList(),
    val valorizacion: Valorizacion? = null,
    val compras: ResumenCompras? = null,
    val mermas: ResumenMermas? = null,
    val discrepancias: List<FilaDiscrepancia> = emptyList(),
)

/** RF-REP-001..007: la app pide con los mismos parámetros que la API y respeta su orden (RF-REP-008). */
@HiltViewModel
class ReportesViewModel @Inject constructor(private val reportes: RepositorioReportes) : ViewModel() {

    private val _estado = MutableStateFlow(ReportesUiState())
    val estado: StateFlow<ReportesUiState> = _estado.asStateFlow()

    fun cambiarRango(desde: LocalDate, hasta: LocalDate) = _estado.update { it.copy(desde = desde, hasta = hasta) }
    fun cambiarDias(dias: Int) = _estado.update { it.copy(dias = dias.coerceIn(1, 3650)) }
    fun cerrar() = _estado.update { it.copy(abierto = null, error = null) }

    fun abrir(reporte: Reporte) {
        _estado.update { it.copy(abierto = reporte, cargando = true, error = null) }
        viewModelScope.launch {
            val s = _estado.value
            val resultado: Resultado<ReportesUiState.() -> ReportesUiState> = when (reporte) {
                Reporte.BAJO_MINIMO -> reportes.bajoMinimo().map { p -> { copy(bajoMinimo = p.datos) } }
                Reporte.AGOTADOS -> reportes.agotados().map { p -> { copy(agotados = p.datos) } }
                Reporte.SIN_MOVIMIENTO -> reportes.sinMovimiento(s.dias).map { p -> { copy(sinMovimiento = p.datos) } }
                Reporte.VALORIZACION -> reportes.valorizacion().map { v -> { copy(valorizacion = v) } }
                Reporte.COMPRAS -> reportes.compras(s.desde, s.hasta).map { c -> { copy(compras = c) } }
                Reporte.MERMAS -> reportes.mermas(s.desde, s.hasta).map { m -> { copy(mermas = m) } }
                Reporte.DISCREPANCIAS -> reportes.discrepancias().map { p -> { copy(discrepancias = p.datos) } }
            }
            when (resultado) {
                is Resultado.Exito -> _estado.update { resultado.valor(it).copy(cargando = false) }
                is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = MapeadorErrores.paraLectura(resultado.error).mensaje) }
            }
        }
    }

    private fun <T, R> Resultado<T>.map(f: (T) -> R): Resultado<R> = when (this) {
        is Resultado.Exito -> Resultado.Exito(f(valor))
        is Resultado.Fallo -> this
    }
}
