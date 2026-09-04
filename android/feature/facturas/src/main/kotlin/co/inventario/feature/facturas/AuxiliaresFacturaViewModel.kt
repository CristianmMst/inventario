package co.inventario.feature.facturas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.FiltrosRecepciones
import co.inventario.data.repositorio.RepositorioCompras
import co.inventario.domain.modelo.Proveedor
import co.inventario.domain.modelo.Recepcion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Datos auxiliares del formulario: proveedores y recepciones confirmadas del proveedor elegido (RF-FAC-006). */
@HiltViewModel
class AuxiliaresFacturaViewModel @Inject constructor(private val compras: RepositorioCompras) : ViewModel() {

    data class Estado(
        val proveedores: List<Proveedor> = emptyList(),
        val recepciones: List<Recepcion> = emptyList(),
    )

    private val _estado = MutableStateFlow(Estado())
    val estado = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            (compras.proveedores() as? Resultado.Exito)?.let { r -> _estado.update { it.copy(proveedores = r.valor) } }
        }
    }

    fun cargarRecepciones(proveedorId: String) {
        viewModelScope.launch {
            (compras.recepciones(FiltrosRecepciones(proveedorId = proveedorId, estado = "confirmada")) as? Resultado.Exito)
                ?.let { r -> _estado.update { it.copy(recepciones = r.valor.datos) } }
        }
    }
}
