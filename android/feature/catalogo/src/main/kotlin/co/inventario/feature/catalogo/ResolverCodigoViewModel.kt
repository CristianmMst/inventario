package co.inventario.feature.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.ErrorApp
import co.inventario.common.error.MapeadorErrores
import co.inventario.data.repositorio.RepositorioCatalogo
import co.inventario.data.repositorio.ResultadoCodigo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResolucionUiState(
    val cargando: Boolean = false,
    val codigo: String? = null,
    val codigoDesconocido: String? = null,
    val mensaje: String? = null,
    val error: ErrorApp? = null,
)

sealed interface ResolucionSuceso {
    data class AbrirFicha(val productoId: String) : ResolucionSuceso
}

/**
 * RF-CAT-008 / RF-CAT-009 / RN-14: resuelve un código (escaneado o tecleado). Conocido → ficha.
 * Desconocido → lo dice con claridad y ofrece el alta con el código precargado; jamás crea solo.
 */
@HiltViewModel
class ResolverCodigoViewModel @Inject constructor(
    private val catalogo: RepositorioCatalogo,
) : ViewModel() {

    private val _estado = MutableStateFlow(ResolucionUiState())
    val estado: StateFlow<ResolucionUiState> = _estado.asStateFlow()

    private val _sucesos = Channel<ResolucionSuceso>(Channel.BUFFERED)
    val sucesos = _sucesos.receiveAsFlow()

    fun resolver(codigo: String) {
        if (_estado.value.cargando) return
        _estado.update { ResolucionUiState(cargando = true, codigo = codigo) }
        viewModelScope.launch {
            when (val r = catalogo.porCodigo(codigo)) {
                is ResultadoCodigo.Encontrado -> {
                    _estado.update { it.copy(cargando = false) }
                    _sucesos.send(ResolucionSuceso.AbrirFicha(r.producto.id))
                }
                is ResultadoCodigo.Desconocido -> _estado.update {
                    it.copy(
                        cargando = false,
                        codigoDesconocido = r.codigo,
                        mensaje = "Ningún producto tiene el código ${r.codigo}. Puedes crearlo con ese código ya puesto.",
                    )
                }
                is ResultadoCodigo.Error -> _estado.update { it.copy(cargando = false, error = MapeadorErrores.paraLectura(r.error)) }
            }
        }
    }
}
