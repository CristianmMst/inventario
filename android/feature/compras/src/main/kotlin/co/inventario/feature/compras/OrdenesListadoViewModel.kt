package co.inventario.feature.compras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.RepositorioCompras
import co.inventario.domain.modelo.Orden
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Listado de órdenes (RF-COM-013). */
@HiltViewModel
class OrdenesListadoViewModel @Inject constructor(private val compras: RepositorioCompras) : ViewModel() {

    data class Estado(
        val ordenes: List<Orden> = emptyList(),
        val cargando: Boolean = true,
        val error: String? = null,
    )

    private val _estado = MutableStateFlow(Estado())
    val estado = _estado.asStateFlow()

    fun recargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val r = compras.ordenes()) {
                is Resultado.Exito -> _estado.update { it.copy(cargando = false, ordenes = r.valor.datos) }
                is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = r.error.mensaje) }
            }
        }
    }
}
