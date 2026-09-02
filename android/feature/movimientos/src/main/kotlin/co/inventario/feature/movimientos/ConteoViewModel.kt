package co.inventario.feature.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.RepositorioMovimientos
import co.inventario.data.repositorio.ResultadoConteo
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Conteo
import co.inventario.domain.modelo.Producto
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConteoUiState(
    val producto: Producto? = null,
    val stockActual: String = "",
    val contada: String = "",
    /** Diferencia calculada en la app para mostrarla antes de confirmar; el servidor la recalcula. */
    val diferencia: String? = null,
    val nota: String = "",
    val cargando: Boolean = false,
    val aviso: String? = null,
    val error: String? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
    val pendiente: String? = null,
)

sealed interface ConteoSuceso {
    data class Registrado(val conteo: Conteo) : ConteoSuceso
}

/** RF-INV-013 / RN-15: se declara lo contado; la diferencia se ve antes; cero no crea nada. */
@HiltViewModel(assistedFactory = ConteoViewModel.Fabrica::class)
class ConteoViewModel @AssistedInject constructor(
    private val repositorio: RepositorioMovimientos,
    @Assisted private val productoId: String,
) : ViewModel() {

    @AssistedFactory
    interface Fabrica {
        fun crear(productoId: String): ConteoViewModel
    }

    private val _estado = MutableStateFlow(ConteoUiState())
    val estado: StateFlow<ConteoUiState> = _estado.asStateFlow()

    private val _sucesos = Channel<ConteoSuceso>(Channel.BUFFERED)
    val sucesos = _sucesos.receiveAsFlow()

    init {
        viewModelScope.launch {
            when (val r = repositorio.producto(productoId)) {
                is Resultado.Exito -> _estado.update { it.copy(producto = r.valor, stockActual = r.valor.stockActual.texto()) }
                is Resultado.Fallo -> _estado.update { it.copy(error = r.error.mensaje) }
            }
        }
    }

    fun cambiarContada(v: String) = _estado.update {
        val contada = v.trim().toBigDecimalOrNull()
        val stock = it.producto?.stockActual
        it.copy(
            contada = v,
            diferencia = if (contada != null && stock != null) (Cantidad(contada) - stock).texto() else null,
            aviso = null,
            erroresCampo = it.erroresCampo - "contada",
        )
    }

    fun cambiarNota(v: String) = _estado.update { it.copy(nota = v, erroresCampo = it.erroresCampo - "nota") }

    fun confirmar() {
        val s = _estado.value
        if (s.cargando) return
        val contada = s.contada.trim().toBigDecimalOrNull()
        if (contada == null || contada.signum() < 0) {
            _estado.update { it.copy(erroresCampo = mapOf("contada" to "Escribe cuántas unidades contaste (cero o más).")) }
            return
        }
        val stock = s.producto?.stockActual
        if (stock != null && Cantidad(contada) == stock) {
            _estado.update { it.copy(aviso = "El conteo coincide con el stock actual: no hay nada que ajustar.") }
            return
        }
        if (s.nota.isBlank()) {
            // Un conteo es un ajuste y los ajustes exigen nota (RF-INV-010).
            _estado.update { it.copy(erroresCampo = mapOf("nota" to "Escribe una nota: dónde y cómo contaste.")) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val r = repositorio.contar(productoId, s.contada.trim(), s.nota)) {
                is ResultadoConteo.Confirmado -> {
                    _estado.update { it.copy(cargando = false, pendiente = null) }
                    _sucesos.send(ConteoSuceso.Registrado(r.conteo))
                }
                is ResultadoConteo.Pendiente -> _estado.update { it.copy(cargando = false, pendiente = r.clave, error = r.error.mensaje) }
                is ResultadoConteo.Rechazado -> _estado.update { it.copy(cargando = false, error = r.error.mensaje) }
            }
        }
    }

    private fun Cantidad.texto(): String = valor.stripTrailingZeros().toPlainString().let { if (it == "0.000" || it == "0" || it == "-0") "0" else it }
}
