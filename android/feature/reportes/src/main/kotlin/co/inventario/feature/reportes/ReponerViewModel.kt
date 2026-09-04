package co.inventario.feature.reportes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.common.error.MapeadorErrores
import co.inventario.data.repositorio.RepositorioReportes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Qué lista se está mirando: lo que falta o lo que ya se acabó. */
enum class ListaReposicion { BAJO_MINIMO, AGOTADOS }

/** Una línea de reposición, ya resuelta a texto para la pantalla. */
data class FilaReposicion(
    val productoId: String,
    val nombre: String,
    val detalle: String,
    val faltan: String,
    val agotado: Boolean,
)

data class ReponerUiState(
    val lista: ListaReposicion = ListaReposicion.BAJO_MINIMO,
    val cargando: Boolean = true,
    val error: String? = null,
    val bajoMinimo: List<FilaReposicion> = emptyList(),
    val agotados: List<FilaReposicion> = emptyList(),
) {
    val filas: List<FilaReposicion>
        get() = if (lista == ListaReposicion.BAJO_MINIMO) bajoMinimo else agotados
}

/**
 * «Reponer»: el destino que faltaba. La spec describe el momento (§1.4, *reposición*: «mirando
 * estantes vacíos → lista de lo que está bajo mínimo, ordenada por urgencia»), pero estaba
 * enterrado tres niveles bajo «Compras y más».
 *
 * Se sirve de `reportes/bajo-minimo` y `reportes/agotados`, que ya existían: no hace falta
 * ningún endpoint nuevo, y así el contrato de paridad de RF-INT-008 sigue intacto. El orden por
 * criticidad lo decide el servidor (RF-REP-008) y aquí se respeta.
 */
@HiltViewModel
class ReponerViewModel @Inject constructor(private val reportes: RepositorioReportes) : ViewModel() {

    private val _estado = MutableStateFlow(ReponerUiState())
    val estado: StateFlow<ReponerUiState> = _estado.asStateFlow()

    init { recargar() }

    fun cambiarLista(lista: ListaReposicion) = _estado.update { it.copy(lista = lista) }

    fun recargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val bajo = reportes.bajoMinimo()) {
                is Resultado.Fallo -> {
                    _estado.update { it.copy(cargando = false, error = MapeadorErrores.paraLectura(bajo.error).mensaje) }
                    return@launch
                }
                is Resultado.Exito -> _estado.update { actual ->
                    actual.copy(
                        bajoMinimo = bajo.valor.datos.map { fila ->
                            val sinStock = fila.stockActual.valor.signum() <= 0
                            FilaReposicion(
                                productoId = fila.producto.id,
                                nombre = fila.producto.nombre,
                                detalle = "hay ${fila.stockActual.aTexto()} · mínimo ${fila.stockMinimo.aTexto()}",
                                // RN-22 toma el mínimo al pie de la letra: la bandera se enciende
                                // *al llegar* al mínimo, así que el déficit puede ser cero. Decir
                                // «Faltan 0» no significa nada; se dice qué pasa de verdad.
                                faltan = when {
                                    sinStock -> "Agotado"
                                    fila.deficit.valor.signum() <= 0 -> "Justo en el mínimo"
                                    else -> "Faltan ${fila.deficit.aTexto()}"
                                },
                                agotado = sinStock,
                            )
                        },
                    )
                }
            }
            when (val agotados = reportes.agotados()) {
                is Resultado.Fallo ->
                    _estado.update { it.copy(cargando = false, error = MapeadorErrores.paraLectura(agotados.error).mensaje) }
                is Resultado.Exito -> _estado.update { actual ->
                    actual.copy(
                        cargando = false,
                        agotados = agotados.valor.datos.map { fila ->
                            FilaReposicion(
                                productoId = fila.producto.id,
                                nombre = fila.producto.nombre,
                                detalle = fila.stockMinimo?.let { "mínimo ${it.aTexto()}" } ?: "sin mínimo definido",
                                faltan = fila.stockMinimo
                                    ?.takeIf { it.valor.signum() > 0 }
                                    ?.let { "Agotado · faltan ${it.aTexto()}" }
                                    ?: "Agotado",
                                agotado = true,
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Las cantidades viajan como texto (RN-07); aquí solo se les quitan los ceros de adorno. */
private fun co.inventario.domain.modelo.Cantidad.aTexto(): String =
    valor.stripTrailingZeros().toPlainString()
