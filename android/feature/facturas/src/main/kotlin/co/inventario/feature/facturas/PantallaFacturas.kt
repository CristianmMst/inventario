package co.inventario.feature.facturas

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.domain.modelo.EstadoPago
import co.inventario.domain.modelo.Factura
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** T-092 / T-093: listado con total del filtro, pago con fecha y exportación por el menú de compartir. */
@Composable
fun PantallaFacturas(alNueva: () -> Unit, alVolver: () -> Unit, vm: FacturasListadoViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var desde by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var hasta by remember { mutableStateOf(LocalDate.now().toString()) }
    var errorRango by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.recargar() }

    // RF-FAC-007: el ZIP descargado se escribe en caché y se entrega al menú de compartir del sistema.
    LaunchedEffect(estado.archivoListo) {
        val archivo = estado.archivoListo ?: return@LaunchedEffect
        val destino = File(File(contexto.cacheDir, "fotos").apply { mkdirs() }, archivo.nombre)
        destino.writeBytes(archivo.bytes)
        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fotos", destino)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, archivo.nombre)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        contexto.startActivity(Intent.createChooser(intent, "Enviar facturas").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        vm.archivoEntregado()
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Dimensiones.espacio), verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        Text("Facturas de compra", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = Dimensiones.espacio))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            FilterChip(selected = estado.filtros.estadoPago == null, onClick = { vm.filtrarPorEstado(null) }, label = { Text("Todas") })
            EstadoPago.entries.forEach { e ->
                FilterChip(selected = estado.filtros.estadoPago == e, onClick = { vm.filtrarPorEstado(e) }, label = { Text(e.etiqueta) })
            }
        }
        estado.totalFiltro?.let { Text("Total del filtro: ${it.aApi().monto} ${it.moneda} (${estado.cantidadFiltro})", style = MaterialTheme.typography.titleMedium) }
        MensajeError(estado.error)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(estado.facturas, key = { _, f -> f.id }) { indice, f ->
                if (indice >= estado.facturas.size - 5) LaunchedEffect(indice) { vm.cargarMas() }
                FilaFactura(f, alPagar = { vm.pedirPago(f.id) })
                HorizontalDivider()
            }
            if (!estado.cargando && estado.facturas.isEmpty()) {
                item { Text("No hay facturas con ese filtro.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Dimensiones.espacio)) }
            }
        }
        Text("Exportar para el contador (ZIP con CSV e imágenes)", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            CampoTexto(desde, { desde = it; errorRango = null }, "Desde", Modifier.weight(1f))
            CampoTexto(hasta, { hasta = it; errorRango = null }, "Hasta", Modifier.weight(1f))
        }
        errorRango?.let { MensajeError(it) }
        BotonSecundario(
            if (estado.exportando) "Descargando…" else "Exportar y compartir",
            {
                try {
                    vm.exportar(LocalDate.parse(desde.trim()), LocalDate.parse(hasta.trim()))
                } catch (_: DateTimeParseException) {
                    errorRango = "Fechas en formato AAAA-MM-DD."
                }
            },
            habilitado = !estado.exportando,
        )
        BotonPrincipal("Registrar factura", alNueva)
        BotonSecundario("Volver", alVolver, Modifier.padding(bottom = Dimensiones.espacio))
    }

    if (estado.pagando != null) {
        AlertDialog(
            onDismissRequest = vm::cancelarPago,
            title = { Text("Marcar como pagada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                    Text("Escribe la fecha en que se pagó (RF-FAC-004).", style = MaterialTheme.typography.bodyLarge)
                    CampoTexto(estado.fechaPago, vm::cambiarFechaPago, "Fecha de pago (AAAA-MM-DD)", error = estado.erroresCampo["fechaPago"])
                }
            },
            confirmButton = { TextButton(onClick = vm::confirmarPago) { Text("Pagada") } },
            dismissButton = { TextButton(onClick = vm::cancelarPago) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FilaFactura(f: Factura, alPagar: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = Dimensiones.espacioCompacto), verticalArrangement = Arrangement.spacedBy(2.dp())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${f.numero} · ${f.proveedor.nombre}", style = MaterialTheme.typography.titleMedium)
            Text("${f.total.aApi().monto} ${f.moneda}", style = MaterialTheme.typography.titleMedium)
        }
        Text("${f.fechaEmision} · ${f.estadoPago.etiqueta}" + (f.fechaPago?.let { " el $it" } ?: "") + " · ${f.imagenes.size} imagen(es)", style = MaterialTheme.typography.bodyLarge)
        if (f.recepciones.isNotEmpty()) Text("Recepciones: ${f.recepciones.joinToString { it.numero }}", style = MaterialTheme.typography.bodyLarge)
        if (f.estadoPago == EstadoPago.PENDIENTE) TextButton(onClick = alPagar) { Text("Marcar pagada") }
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
