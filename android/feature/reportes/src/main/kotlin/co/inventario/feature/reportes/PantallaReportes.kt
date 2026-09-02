package co.inventario.feature.reportes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoCantidad
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Dinero
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** T-094: los siete reportes (RF-REP-001..007). Bajo mínimo por urgencia; no valorizables aparte. */
@Composable
fun PantallaReportes(alAbrirProducto: (String) -> Unit, alVolver: () -> Unit, vm: ReportesViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var desde by remember { mutableStateOf(estado.desde.toString()) }
    var hasta by remember { mutableStateOf(estado.hasta.toString()) }
    var errorRango by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        val abierto = estado.abierto
        if (abierto == null) {
            Text("Reportes", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                CampoTexto(desde, { desde = it; errorRango = null }, "Desde", Modifier.weight(1f))
                CampoTexto(hasta, { hasta = it; errorRango = null }, "Hasta", Modifier.weight(1f))
            }
            CampoCantidad(estado.dias.toString(), { it.toIntOrNull()?.let(vm::cambiarDias) }, "Días sin movimiento", admiteDecimales = false)
            errorRango?.let { MensajeError(it) }
            Reporte.entries.forEach { r ->
                BotonSecundario(r.titulo, {
                    try {
                        vm.cambiarRango(LocalDate.parse(desde.trim()), LocalDate.parse(hasta.trim()))
                        vm.abrir(r)
                    } catch (_: DateTimeParseException) {
                        errorRango = "Fechas en formato AAAA-MM-DD."
                    }
                })
            }
            BotonSecundario("Volver", alVolver)
        } else {
            Text(abierto.titulo, style = MaterialTheme.typography.headlineMedium)
            if (abierto.requiereRango) Text("Del ${estado.desde} al ${estado.hasta}", style = MaterialTheme.typography.bodyLarge)
            MensajeError(estado.error)
            if (estado.cargando) CircularProgressIndicator() else Contenido(abierto, estado, alAbrirProducto)
            BotonSecundario("Otros reportes", vm::cerrar)
        }
    }
}

@Composable
private fun Contenido(reporte: Reporte, s: ReportesUiState, alAbrirProducto: (String) -> Unit) {
    when (reporte) {
        Reporte.BAJO_MINIMO -> {
            if (s.bajoMinimo.isEmpty()) Vacio("Ningún producto está bajo su mínimo.")
            s.bajoMinimo.forEach { f ->
                Fila(f.producto.nombre, "stock ${f.stockActual.texto()} · mínimo ${f.stockMinimo.texto()} · faltan ${f.deficit.texto()}", { alAbrirProducto(f.producto.id) })
            }
        }
        Reporte.AGOTADOS -> {
            if (s.agotados.isEmpty()) Vacio("No hay productos agotados.")
            s.agotados.forEach { f -> Fila(f.producto.nombre, "stock ${f.stockActual.texto()}", { alAbrirProducto(f.producto.id) }) }
        }
        Reporte.SIN_MOVIMIENTO -> {
            if (s.sinMovimiento.isEmpty()) Vacio("Todo se ha movido en los últimos ${s.dias} días.")
            s.sinMovimiento.forEach { f ->
                Fila(f.producto.nombre, "stock ${f.stockActual.texto()}" + (f.valorACosto?.let { " · ${it.texto()}" } ?: " · sin costo"), { alAbrirProducto(f.producto.id) })
            }
        }
        Reporte.VALORIZACION -> s.valorizacion?.let { v ->
            Text("Total: ${v.total.texto()} (${v.productosValorizados} productos)", style = MaterialTheme.typography.titleMedium)
            v.porCategoria.forEach { c -> Fila(c.categoria?.nombre ?: "Sin categoría", "${c.productos} productos · ${c.valor.texto()}") }
            if (v.noValorizables.datos.isNotEmpty()) {
                Text("Con stock pero sin costo (no valorizables)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                v.noValorizables.datos.forEach { f -> Fila(f.producto.nombre, "stock ${f.stockActual.texto()}", { alAbrirProducto(f.producto.id) }) }
            }
        }
        Reporte.COMPRAS -> s.compras?.let { c ->
            Text("Recibido ${c.totalRecibido.texto()} en ${c.recepciones} recepciones · Facturado ${c.totalFacturado.texto()} en ${c.facturas} facturas", style = MaterialTheme.typography.titleMedium)
            Text("Por proveedor", style = MaterialTheme.typography.titleMedium)
            c.porProveedor.forEach { p -> Fila(p.proveedor.nombre, "recibido ${p.totalRecibido.texto()} · facturado ${p.totalFacturado.texto()}") }
            Text("Por categoría", style = MaterialTheme.typography.titleMedium)
            c.porCategoria.forEach { k -> Fila(k.categoria?.nombre ?: "Sin categoría", "recibido ${k.totalRecibido.texto()}") }
        }
        Reporte.MERMAS -> s.mermas?.let { m ->
            Text("${m.totalCantidad.texto()} unidades · ${m.totalValor.texto()}", style = MaterialTheme.typography.titleMedium)
            Text("Por motivo", style = MaterialTheme.typography.titleMedium)
            m.porMotivo.forEach { k -> Fila(k.etiqueta, "${k.cantidad.texto()} · ${k.valor.texto()}") }
            Text("Por producto", style = MaterialTheme.typography.titleMedium)
            m.porProducto.datos.forEach { p -> Fila(p.producto.nombre, "${p.cantidad.texto()} · ${p.valor.texto()}", { alAbrirProducto(p.producto.id) }) }
        }
        Reporte.DISCREPANCIAS -> {
            if (s.discrepancias.isEmpty()) Vacio("No hay movimientos forzados.")
            s.discrepancias.forEach { d ->
                Fila("${d.producto.nombre} · ${d.tipo.codigo} ${d.cantidad.texto()}", "quedó ${d.stockResultante.texto()} · ${d.motivo}" + (d.nota?.let { " · $it" } ?: ""), { alAbrirProducto(d.producto.id) })
            }
        }
    }
}

@Composable
private fun Fila(titulo: String, detalle: String, alPulsar: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = Dimensiones.espacioCompacto)) {
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text(detalle, style = MaterialTheme.typography.bodyLarge)
        if (alPulsar != null) BotonSecundario("Abrir ficha", alPulsar)
    }
    HorizontalDivider()
}

@Composable
private fun Vacio(texto: String) = Text(texto, style = MaterialTheme.typography.bodyLarge)

private fun Cantidad.texto() = valor.stripTrailingZeros().toPlainString()
private fun Dinero.texto() = "${aApi().monto} ${moneda}"
