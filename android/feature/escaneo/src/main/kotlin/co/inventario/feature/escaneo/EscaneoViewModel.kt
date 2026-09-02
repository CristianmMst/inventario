package co.inventario.feature.escaneo

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** RNF-15: el permiso se pide al usar la cámara, con explicación previa; denegado no bloquea nada. */
enum class EstadoPermiso { SIN_PEDIR, CONCEDIDO, DENEGADO }

data class EscaneoUiState(
    val permiso: EstadoPermiso = EstadoPermiso.SIN_PEDIR,
    val linterna: Boolean = false,
    val tecleando: Boolean = false,
    val codigoTecleado: String = "",
    val errorCodigo: String? = null,
)

@HiltViewModel
class EscaneoViewModel @Inject constructor() : ViewModel() {

    private val _estado = MutableStateFlow(EscaneoUiState())
    val estado: StateFlow<EscaneoUiState> = _estado.asStateFlow()

    fun permisoResuelto(concedido: Boolean) = _estado.update {
        // Sin cámara la única vía es teclear: se abre sola (RNF-15).
        it.copy(permiso = if (concedido) EstadoPermiso.CONCEDIDO else EstadoPermiso.DENEGADO, tecleando = it.tecleando || !concedido)
    }

    fun alternarLinterna() = _estado.update { it.copy(linterna = !it.linterna) }

    fun teclear(abrir: Boolean) = _estado.update { it.copy(tecleando = abrir, errorCodigo = null) }

    fun cambiarCodigo(valor: String) = _estado.update { it.copy(codigoTecleado = valor.trim(), errorCodigo = null) }

    /** Devuelve el código listo para buscar, o null y marca el error si está vacío. */
    fun codigoConfirmado(): String? {
        val codigo = _estado.value.codigoTecleado
        if (codigo.isBlank()) {
            _estado.update { it.copy(errorCodigo = "Escribe el código que está debajo de las barras.") }
            return null
        }
        _estado.update { it.copy(codigoTecleado = "") }
        return codigo
    }
}
