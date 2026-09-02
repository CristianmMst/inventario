package co.inventario.feature.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.RepositorioMovimientos
import co.inventario.data.repositorio.ResultadoMovimiento
import co.inventario.domain.modelo.Movimiento
import co.inventario.domain.modelo.TipoMovimiento
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistorialUiState(
    val movimientos: List<Movimiento> = emptyList(),
    val cursorSiguiente: String? = null,
    val tieneMas: Boolean = false,
    val cargando: Boolean = false,
    val error: String? = null,
    /** Id del movimiento cuya anulación se está confirmando (diálogo con nota). */
    val anulando: String? = null,
    val nota: String = "",
    val erroresCampo: Map<String, String> = emptyMap(),
    val pendiente: String? = null,
)

/**
 * RF-INV-012 / RF-INV-008: historial en orden cronológico inverso con el stock resultante; la
 * única acción es anular con motivo escrito. No existe editar (RN-02).
 */
@HiltViewModel(assistedFactory = HistorialViewModel.Fabrica::class)
class HistorialViewModel @AssistedInject constructor(
    private val repositorio: RepositorioMovimientos,
    @Assisted private val productoId: String,
) : ViewModel() {

    @AssistedFactory
    interface Fabrica {
        fun crear(productoId: String): HistorialViewModel
    }

    private val _estado = MutableStateFlow(HistorialUiState())
    val estado: StateFlow<HistorialUiState> = _estado.asStateFlow()

    init { recargar() }

    fun recargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch { cargar(cursor = null) }
    }

    fun cargarMas() {
        val s = _estado.value
        if (!s.tieneMas || s.cargando || s.cursorSiguiente == null) return
        _estado.update { it.copy(cargando = true) }
        viewModelScope.launch { cargar(s.cursorSiguiente) }
    }

    /** Un anulado no se vuelve a anular y un contramovimiento no se anula (RF-INV-008). */
    fun sePuedeAnular(m: Movimiento): Boolean = !m.anulado && m.tipo != TipoMovimiento.CONTRAMOVIMIENTO

    fun pedirAnulacion(movimientoId: String) = _estado.update { it.copy(anulando = movimientoId, nota = "", erroresCampo = emptyMap()) }
    fun cancelarAnulacion() = _estado.update { it.copy(anulando = null, erroresCampo = emptyMap()) }
    fun cambiarNota(v: String) = _estado.update { it.copy(nota = v, erroresCampo = it.erroresCampo - "nota") }

    fun confirmarAnulacion() {
        val s = _estado.value
        val id = s.anulando ?: return
        if (s.nota.isBlank()) {
            _estado.update { it.copy(erroresCampo = mapOf("nota" to "Escribe por qué se anula.")) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val r = repositorio.anular(id, s.nota)) {
                is ResultadoMovimiento.Confirmado -> {
                    _estado.update { it.copy(anulando = null, nota = "", pendiente = null) }
                    cargar(cursor = null)
                }
                is ResultadoMovimiento.Pendiente -> _estado.update { it.copy(cargando = false, pendiente = r.clave, error = r.error.mensaje) }
                is ResultadoMovimiento.Rechazado -> _estado.update { it.copy(cargando = false, error = r.error.mensaje) }
                is ResultadoMovimiento.StockInsuficiente -> _estado.update { it.copy(cargando = false, error = "El stock no alcanza para deshacer este movimiento.") }
            }
        }
    }

    private suspend fun cargar(cursor: String?) {
        when (val r = repositorio.historial(productoId, cursor)) {
            is Resultado.Exito -> _estado.update {
                it.copy(
                    cargando = false,
                    movimientos = if (cursor == null) r.valor.datos else it.movimientos + r.valor.datos,
                    cursorSiguiente = r.valor.cursorSiguiente,
                    tieneMas = r.valor.tieneMas,
                )
            }
            is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = r.error.mensaje) }
        }
    }
}
