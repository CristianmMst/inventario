package co.inventario.app.navegacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.data.outbox.BandejaSalida
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Cuántas escrituras siguen esperando en la bandeja de salida. La bandeja reintenta sola al
 * arrancar, en silencio; sin esto la única señal de que algo no ha salido era un botón dentro
 * de la pantalla de movimiento, y el miedo de Marta es justamente que el número no cuadre.
 *
 * Se relee cada pocos segundos: es una consulta a Room, no a la red.
 */
@HiltViewModel
class PendientesViewModel @Inject constructor(private val bandeja: BandejaSalida) : ViewModel() {

    private val _pendientes = MutableStateFlow(0)
    val pendientes: StateFlow<Int> = _pendientes.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _pendientes.value = runCatching { bandeja.pendientes().size }.getOrDefault(0)
                delay(INTERVALO_MS)
            }
        }
    }

    private companion object {
        const val INTERVALO_MS = 5_000L
    }
}
