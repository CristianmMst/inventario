package co.inventario.feature.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.common.error.MapeadorErrores
import co.inventario.data.repositorio.FiltrosProductos
import co.inventario.data.repositorio.RepositorioCatalogo
import co.inventario.domain.modelo.Categoria
import co.inventario.domain.modelo.Producto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BusquedaUiState(
    val texto: String = "",
    val filtros: FiltrosProductos = FiltrosProductos(),
    val categorias: List<Categoria> = emptyList(),
    val resultados: List<Producto> = emptyList(),
    val cursorSiguiente: String? = null,
    val tieneMas: Boolean = false,
    val cargando: Boolean = false,
    val error: String? = null,
)

/**
 * RF-CAT-007 / RF-CAT-014: con texto busca (antirrebote de 300 ms para no disparar una petición
 * por letra); sin texto lista con los filtros. Paginación por cursor al llegar al final.
 */
@HiltViewModel
class BusquedaViewModel @Inject constructor(
    private val catalogo: RepositorioCatalogo,
) : ViewModel() {

    private val _estado = MutableStateFlow(BusquedaUiState())
    val estado: StateFlow<BusquedaUiState> = _estado.asStateFlow()
    private var trabajo: Job? = null

    init {
        viewModelScope.launch {
            (catalogo.categorias() as? Resultado.Exito)?.let { r -> _estado.update { it.copy(categorias = r.valor) } }
        }
        consultar(inmediato = true)
    }

    fun cambiarTexto(texto: String) {
        _estado.update { it.copy(texto = texto) }
        consultar(inmediato = false)
    }

    fun cambiarFiltros(filtros: FiltrosProductos) {
        _estado.update { it.copy(filtros = filtros) }
        consultar(inmediato = true)
    }

    fun cargarMas() {
        val s = _estado.value
        if (!s.tieneMas || s.cargando || s.cursorSiguiente == null) return
        viewModelScope.launch { pedir(s.texto, s.filtros, s.cursorSiguiente) }
    }

    fun reintentar() = consultar(inmediato = true)

    private fun consultar(inmediato: Boolean) {
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            if (!inmediato) delay(ANTIRREBOTE_MS)
            val s = _estado.value
            pedir(s.texto, s.filtros, cursor = null)
        }
    }

    private suspend fun pedir(texto: String, filtros: FiltrosProductos, cursor: String?) {
        _estado.update { it.copy(cargando = true, error = null) }
        val consulta = texto.trim()
        val resultado = if (consulta.isEmpty()) catalogo.listar(filtros, cursor) else catalogo.buscar(consulta, cursor)
        when (resultado) {
            is Resultado.Exito -> _estado.update {
                val pagina = resultado.valor
                it.copy(
                    cargando = false,
                    resultados = if (cursor == null) pagina.datos else it.resultados + pagina.datos,
                    cursorSiguiente = pagina.cursorSiguiente,
                    tieneMas = pagina.tieneMas,
                )
            }
            is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = MapeadorErrores.paraLectura(resultado.error).mensaje) }
        }
    }

    private companion object {
        const val ANTIRREBOTE_MS = 300L
    }
}
