package co.inventario.feature.compras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.RepositorioCompras
import co.inventario.domain.modelo.Orden
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TipoCierre { CANCELAR, CERRAR_CON_FALTANTE }

data class OrdenDetalleUiState(
    val orden: Orden? = null,
    val cargando: Boolean = true,
    val error: String? = null,
    val cierre: TipoCierre? = null,
    val motivo: String = "",
    val erroresCampo: Map<String, String> = emptyMap(),
) {
    val puedeEditarLineas: Boolean get() = orden?.estado?.editable == true
    val puedeEmitir: Boolean get() = orden?.estado?.editable == true
    val puedeRecibir: Boolean get() = orden?.estado?.recibible == true
    val puedeCancelar: Boolean get() = orden?.estado?.cancelable == true
    val puedeCerrarConFaltante: Boolean get() = orden?.estado?.cerrableConFaltante == true
}

/** RF-COM-003 / RF-COM-008 / RF-COM-010: las transiciones válidas las dicta el estado. */
@HiltViewModel(assistedFactory = OrdenDetalleViewModel.Fabrica::class)
class OrdenDetalleViewModel @AssistedInject constructor(
    private val compras: RepositorioCompras,
    @Assisted private val ordenId: String,
) : ViewModel() {

    @AssistedFactory
    interface Fabrica {
        fun crear(ordenId: String): OrdenDetalleViewModel
    }

    private val _estado = MutableStateFlow(OrdenDetalleUiState())
    val estado: StateFlow<OrdenDetalleUiState> = _estado.asStateFlow()

    init { recargar() }

    fun recargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch { aplicar(compras.orden(ordenId)) }
    }

    fun emitir() = transicion { compras.emitirOrden(ordenId) }

    fun pedirCancelacion() = _estado.update { it.copy(cierre = TipoCierre.CANCELAR, motivo = "", erroresCampo = emptyMap()) }
    fun pedirCierreConFaltante() = _estado.update { it.copy(cierre = TipoCierre.CERRAR_CON_FALTANTE, motivo = "", erroresCampo = emptyMap()) }
    fun cancelarCierre() = _estado.update { it.copy(cierre = null, erroresCampo = emptyMap()) }
    fun cambiarMotivo(v: String) = _estado.update { it.copy(motivo = v, erroresCampo = it.erroresCampo - "motivo") }

    fun confirmarCierre() {
        val s = _estado.value
        val tipo = s.cierre ?: return
        if (s.motivo.isBlank()) {
            _estado.update { it.copy(erroresCampo = mapOf("motivo" to "Escribe el motivo.")) }
            return
        }
        transicion {
            when (tipo) {
                TipoCierre.CANCELAR -> compras.cancelarOrden(ordenId, s.motivo)
                TipoCierre.CERRAR_CON_FALTANTE -> compras.cerrarOrdenConFaltante(ordenId, s.motivo)
            }
        }
    }

    private fun transicion(operacion: suspend () -> Resultado<Orden>) {
        if (_estado.value.cargando) return
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch { aplicar(operacion()) }
    }

    private fun aplicar(r: Resultado<Orden>) {
        when (r) {
            is Resultado.Exito -> _estado.update { it.copy(cargando = false, orden = r.valor, cierre = null, motivo = "") }
            is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = r.error.mensaje) }
        }
    }
}
