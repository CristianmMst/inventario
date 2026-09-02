package co.inventario.feature.movimientos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoCantidad
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.componentes.StockDestacado
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.domain.modelo.Movimiento
import co.inventario.domain.modelo.TipoMovimiento
import co.inventario.domain.modelo.TipoUnidad

/** T-084 / T-085: registrar entrada, salida, merma o ajuste con la lista cerrada de motivos. */
@Composable
fun PantallaMovimiento(
    productoId: String,
    tipo: TipoMovimiento,
    alRegistrar: (Movimiento) -> Unit,
    alCancelar: () -> Unit,
    vm: MovimientoViewModel = hiltViewModel<MovimientoViewModel, MovimientoViewModel.Fabrica>(
        creationCallback = { it.crear(productoId, tipo) },
    ),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.sucesos.collect { if (it is MovimientoSuceso.Registrado) alRegistrar(it.movimiento) } }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Text(titulo(tipo), style = MaterialTheme.typography.headlineMedium)
        estado.producto?.let { p ->
            Text(p.nombre, style = MaterialTheme.typography.titleMedium)
            StockDestacado(p.stockActual.valor.stripTrailingZeros().toPlainString(), "${p.unidad.nombre} en stock")
        }
        CampoCantidad(
            estado.cantidad, vm::cambiarCantidad, "Cantidad",
            error = estado.erroresCampo["cantidad"],
            admiteDecimales = estado.producto?.unidad?.tipo != TipoUnidad.DISCRETA,
        )
        if (tipo == TipoMovimiento.AJUSTE) {
            Text("El ajuste…", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                FilterChip(selected = estado.direccion == 1, onClick = { vm.cambiarDireccion(1) }, label = { Text("Suma") })
                FilterChip(selected = estado.direccion == -1, onClick = { vm.cambiarDireccion(-1) }, label = { Text("Resta") })
            }
            estado.erroresCampo["direccion"]?.let { MensajeError(it) }
        }
        Text("Motivo", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            estado.motivos.forEach { m ->
                FilterChip(selected = m.codigo == estado.motivo, onClick = { vm.cambiarMotivo(m.codigo) }, label = { Text(m.etiqueta) })
            }
        }
        estado.erroresCampo["motivo"]?.let { MensajeError(it) }
        CampoTexto(
            estado.nota, vm::cambiarNota,
            if (estado.notaObligatoria) "Nota (obligatoria)" else "Nota (opcional)",
            error = estado.erroresCampo["nota"],
        )
        MensajeError(estado.error)
        if (estado.pendiente != null) {
            BotonPrincipal("Reintentar ahora", vm::reintentar, habilitado = !estado.cargando)
        } else {
            BotonPrincipal(if (estado.cargando) "Guardando…" else "Confirmar", vm::confirmar, habilitado = !estado.cargando)
        }
        BotonSecundario("Cancelar", alCancelar, habilitado = !estado.cargando)
    }

    estado.override?.let { o ->
        val unidad = estado.producto?.unidad?.nombre.orEmpty()
        AlertDialog(
            onDismissRequest = vm::cancelarOverride,
            title = { Text("No alcanza el stock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                    Text(
                        "Hay ${o.disponible.valor.stripTrailingZeros().toPlainString()} $unidad y pides " +
                            "${o.solicitado.valor.stripTrailingZeros().toPlainString()}.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (o.puedeForzar) {
                        Text("Puedes forzarlo: el stock quedará en negativo y el movimiento se marcará como forzado. Escribe por qué.", style = MaterialTheme.typography.bodyLarge)
                        CampoTexto(estado.nota, vm::cambiarNota, "Motivo del forzado", error = estado.erroresCampo["nota"])
                    }
                }
            },
            confirmButton = {
                if (o.puedeForzar) TextButton(onClick = vm::forzar, enabled = !estado.cargando) { Text("Forzar de todos modos") }
            },
            dismissButton = { TextButton(onClick = vm::cancelarOverride) { Text("Corregir la cantidad") } },
        )
    }
}

private fun titulo(tipo: TipoMovimiento) = when (tipo) {
    TipoMovimiento.ENTRADA -> "Registrar entrada"
    TipoMovimiento.SALIDA -> "Registrar salida"
    TipoMovimiento.MERMA -> "Registrar merma"
    TipoMovimiento.AJUSTE -> "Registrar ajuste"
    TipoMovimiento.CONTRAMOVIMIENTO -> "Anulación"
}
