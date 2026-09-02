package co.inventario.feature.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.domain.modelo.Movimiento
import co.inventario.domain.modelo.TipoMovimiento
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** T-087: historial (RF-INV-012) y anulación con motivo (RF-INV-008). No hay forma de editar. */
@Composable
fun PantallaHistorial(
    productoId: String,
    alVolver: () -> Unit,
    vm: HistorialViewModel = hiltViewModel<HistorialViewModel, HistorialViewModel.Fabrica>(creationCallback = { it.crear(productoId) }),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Dimensiones.espacio), verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        Text("Historial", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = Dimensiones.espacio))
        MensajeError(estado.error)
        if (estado.error != null) BotonSecundario("Reintentar", vm::recargar)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(estado.movimientos, key = { _, m -> m.id }) { indice, m ->
                if (indice >= estado.movimientos.size - 5) LaunchedEffect(indice) { vm.cargarMas() }
                FilaMovimiento(m, formato, anulable = vm.sePuedeAnular(m), alAnular = { vm.pedirAnulacion(m.id) })
                HorizontalDivider()
            }
            if (!estado.cargando && estado.movimientos.isEmpty()) {
                item { Text("Este producto todavía no tiene movimientos.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Dimensiones.espacio)) }
            }
        }
        BotonSecundario("Volver", alVolver, Modifier.padding(bottom = Dimensiones.espacio))
    }

    if (estado.anulando != null) {
        AlertDialog(
            onDismissRequest = vm::cancelarAnulacion,
            title = { Text("Anular movimiento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                    Text("Se creará un contramovimiento que deshace este. El original queda marcado como anulado; nada se borra.", style = MaterialTheme.typography.bodyLarge)
                    CampoTexto(estado.nota, vm::cambiarNota, "Motivo de la anulación", error = estado.erroresCampo["nota"])
                }
            },
            confirmButton = { TextButton(onClick = vm::confirmarAnulacion, enabled = !estado.cargando) { Text("Anular") } },
            dismissButton = { TextButton(onClick = vm::cancelarAnulacion) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FilaMovimiento(m: Movimiento, formato: DateTimeFormatter, anulable: Boolean, alAnular: () -> Unit) {
    val cantidad = m.cantidad.valor.stripTrailingZeros().toPlainString()
    val signo = if (m.direccion < 0) "−" else "+"
    Column(Modifier.fillMaxWidth().padding(vertical = Dimensiones.espacioCompacto), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                "${etiqueta(m.tipo)} · ${m.motivo}${if (m.forzado) " · forzado" else ""}",
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (m.anulado) TextDecoration.LineThrough else null,
            )
            Text("$signo$cantidad", style = MaterialTheme.typography.titleMedium, color = if (m.direccion < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        Text("${formato.format(m.ocurridoEn)} · quedó ${m.stockResultante.valor.stripTrailingZeros().toPlainString()}", style = MaterialTheme.typography.bodyLarge)
        m.nota?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        when {
            m.anulado -> Text("Anulado", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            m.anulaMovimientoId != null -> Text("Anula un movimiento anterior", style = MaterialTheme.typography.bodyLarge)
            anulable -> TextButton(onClick = alAnular) { Text("Anular") }
        }
    }
}

private fun etiqueta(tipo: TipoMovimiento) = when (tipo) {
    TipoMovimiento.ENTRADA -> "Entrada"
    TipoMovimiento.SALIDA -> "Salida"
    TipoMovimiento.MERMA -> "Merma"
    TipoMovimiento.AJUSTE -> "Ajuste"
    TipoMovimiento.CONTRAMOVIMIENTO -> "Anulación"
}
