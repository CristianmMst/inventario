package co.inventario.feature.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.common.error.MapeadorErrores
import co.inventario.data.repositorio.RepositorioCatalogo
import co.inventario.domain.modelo.Producto
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FichaUiState(
    val cargando: Boolean = true,
    val producto: Producto? = null,
    val error: String? = null,
)

/** RF-CAT-008 / RF-INV-003: la ficha muestra el stock actual, que siempre viene del servidor. */
@HiltViewModel(assistedFactory = FichaProductoViewModel.Fabrica::class)
class FichaProductoViewModel @AssistedInject constructor(
    private val catalogo: RepositorioCatalogo,
    @Assisted private val productoId: String,
) : ViewModel() {

    @AssistedFactory
    interface Fabrica {
        fun crear(productoId: String): FichaProductoViewModel
    }

    private val _estado = MutableStateFlow(FichaUiState())
    val estado: StateFlow<FichaUiState> = _estado.asStateFlow()

    fun recargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val r = catalogo.producto(productoId)) {
                is Resultado.Exito -> _estado.update { it.copy(cargando = false, producto = r.valor) }
                is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = MapeadorErrores.paraLectura(r.error).mensaje) }
            }
        }
    }

    fun archivar(archivar: Boolean) {
        viewModelScope.launch {
            when (val r = catalogo.archivar(productoId, archivar)) {
                is Resultado.Exito -> _estado.update { it.copy(producto = r.valor) }
                is Resultado.Fallo -> _estado.update { it.copy(error = r.error.mensaje) }
            }
        }
    }
}
