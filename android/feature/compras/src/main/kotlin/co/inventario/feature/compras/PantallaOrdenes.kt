package co.inventario.feature.compras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.RepositorioCompras
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones
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
    data class Estado(val ordenes: List<Orden> = emptyList(), val cargando: Boolean = true, val error: String? = null)

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

@Composable
fun PantallaOrdenes(alAbrir: (String) -> Unit, alNueva: () -> Unit, alVolver: () -> Unit, vm: OrdenesListadoViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.recargar() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Dimensiones.espacio), verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        Text("Órdenes de compra", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = Dimensiones.espacio))
        MensajeError(estado.error)
        LazyColumn(Modifier.weight(1f)) {
            items(estado.ordenes, key = { it.id }) { o ->
                Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimensiones.areaTactilMinima).clickable { alAbrir(o.id) }.padding(vertical = Dimensiones.espacioCompacto)) {
                    Text("${o.numero} · ${o.proveedor.nombre}", style = MaterialTheme.typography.titleMedium)
                    Text("${o.estado.etiqueta} · ${o.lineas.size} líneas" + (o.totalEstimado?.let { " · ${it.aApi().monto} ${it.moneda}" } ?: ""), style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
            }
            if (!estado.cargando && estado.ordenes.isEmpty()) {
                item { Text("No hay órdenes. Las órdenes son opcionales: puedes recibir sin ellas.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Dimensiones.espacio)) }
            }
        }
        BotonPrincipal("Nueva orden", alNueva)
        BotonSecundario("Volver", alVolver, Modifier.padding(bottom = Dimensiones.espacio))
    }
}

/** T-089: detalle con transiciones; emitida bloquea las líneas (RF-COM-003). */
@Composable
fun PantallaOrdenDetalle(
    ordenId: String,
    alRecibir: (String) -> Unit,
    alVolver: () -> Unit,
    vm: OrdenDetalleViewModel = hiltViewModel<OrdenDetalleViewModel, OrdenDetalleViewModel.Fabrica>(creationCallback = { it.crear(ordenId) }),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.recargar() }
    val orden = estado.orden
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Text(orden?.numero ?: "Orden", style = MaterialTheme.typography.headlineMedium)
        if (orden != null) {
            Text("${orden.proveedor.nombre} · ${orden.estado.etiqueta}", style = MaterialTheme.typography.titleMedium)
            orden.fechaEsperada?.let { Text("Esperada para $it", style = MaterialTheme.typography.bodyLarge) }
            orden.notas?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            orden.motivoCierre?.let { Text("Cierre: $it", style = MaterialTheme.typography.bodyLarge) }
            if (!estado.puedeEditarLineas) Text("Las líneas ya no se editan: la orden está ${orden.estado.etiqueta.lowercase()}.", style = MaterialTheme.typography.bodyLarge)
            orden.lineas.forEach { l ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(l.producto.nombre, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        "${l.cantidadOrdenada.valor.stripTrailingZeros().toPlainString()} pedidas · ${l.cantidadPendiente.valor.stripTrailingZeros().toPlainString()} pendientes",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                HorizontalDivider()
            }
            MensajeError(estado.error)
            if (estado.puedeEmitir) BotonPrincipal("Emitir la orden", vm::emitir, habilitado = !estado.cargando)
            if (estado.puedeRecibir) BotonPrincipal("Recibir contra esta orden", { alRecibir(orden.id) })
            if (estado.puedeCancelar) BotonSecundario("Cancelar la orden", vm::pedirCancelacion)
            if (estado.puedeCerrarConFaltante) BotonSecundario("Cerrar con faltante", vm::pedirCierreConFaltante)
        } else {
            MensajeError(estado.error)
        }
        BotonSecundario("Volver", alVolver)
    }

    estado.cierre?.let { tipo ->
        AlertDialog(
            onDismissRequest = vm::cancelarCierre,
            title = { Text(if (tipo == TipoCierre.CANCELAR) "Cancelar la orden" else "Cerrar con faltante") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                    Text(
                        if (tipo == TipoCierre.CANCELAR) "La orden no admitirá recepciones. Escribe el motivo."
                        else "Lo pendiente no llegará; la orden no admitirá más recepciones. Escribe el motivo.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    CampoTexto(estado.motivo, vm::cambiarMotivo, "Motivo", error = estado.erroresCampo["motivo"])
                }
            },
            confirmButton = { TextButton(onClick = vm::confirmarCierre, enabled = !estado.cargando) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = vm::cancelarCierre) { Text("Volver") } },
        )
    }
}

/** RF-COM-002: nueva orden en borrador. */
@Composable
fun PantallaNuevaOrden(
    monedaBase: String,
    alCrear: (String) -> Unit,
    alCancelar: () -> Unit,
    vm: NuevaOrdenViewModel = hiltViewModel<NuevaOrdenViewModel, NuevaOrdenViewModel.Fabrica>(creationCallback = { it.crear(monedaBase) }),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.sucesos.collect { if (it is NuevaOrdenSuceso.Creada) alCrear(it.ordenId) } }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Text("Nueva orden de compra", style = MaterialTheme.typography.headlineMedium)
        SelectorProveedor(estado.proveedores, estado.proveedorId, vm::elegirProveedor, estado.erroresCampo["proveedor"])
        CampoTexto(estado.fechaEsperada, vm::cambiarFechaEsperada, "Fecha esperada (AAAA-MM-DD, opcional)", error = estado.erroresCampo["fechaEsperada"])
        CampoTexto(estado.notas, vm::cambiarNotas, "Notas (opcional)")
        Text("Líneas", style = MaterialTheme.typography.titleMedium)
        estado.lineas.forEachIndexed { i, l ->
            LineaEditable(
                l.producto, l.cantidad, l.costo, "Costo estimado (${estado.moneda})", null,
                estado.erroresCampo["cantidad_$i"], null, { vm.cambiarCantidad(i, it) }, { vm.cambiarCosto(i, it) }, { vm.quitarLinea(i) },
            )
        }
        estado.erroresCampo["lineas"]?.let { MensajeError(it) }
        SelectorProducto(alElegir = vm::agregarLinea)
        MensajeError(estado.error)
        BotonPrincipal(if (estado.cargando) "Guardando…" else "Guardar borrador", vm::guardar, habilitado = !estado.cargando)
        BotonSecundario("Cancelar", alCancelar)
    }
}
