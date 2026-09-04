package co.inventario.feature.reportes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.CampoCantidad
import co.inventario.designsystem.componentes.CampoFecha
import co.inventario.designsystem.componentes.EsqueletoLista
import co.inventario.designsystem.componentes.EstadoError
import co.inventario.designsystem.componentes.EstadoVacio
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.componentes.Formato
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Estado
import co.inventario.designsystem.tema.Iconos
import co.inventario.designsystem.tema.Tipografia
import co.inventario.domain.modelo.Cantidad
import co.inventario.domain.modelo.Dinero

/** T-094: los siete reportes (RF-REP-001..007). Bajo mínimo por urgencia; no valorizables aparte. */
@Composable
fun PantallaReportes(alAbrirProducto: (String) -> Unit, alVolver: () -> Unit, vm: ReportesViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val abierto = estado.abierto

    // Dentro de un reporte, «atrás» vuelve al selector, no abandona la pantalla.
    BackHandler(enabled = abierto != null) { vm.cerrar() }

    PantallaInventario(
        titulo = abierto?.titulo ?: "Reportes",
        alVolver = if (abierto != null) vm::cerrar else alVolver,
    ) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensiones.espacio),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
        ) {
            if (abierto == null) {
                Selector(estado, vm)
            } else {
                if (abierto.requiereRango) {
                    Text(
                        "Del ${estado.desde} al ${estado.hasta}",
                        style = Tipografia.numeroCuerpo,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val error = estado.error
                when {
                    estado.cargando -> EsqueletoLista(filas = 5)
                    error != null -> EstadoError(error, { vm.abrir(abierto) })
                    else -> Contenido(abierto, estado, alAbrirProducto)
                }
            }
        }
    }
}

/**
 * El selector era una columna de siete botones con borde, todos iguales. Ahora cada reporte es
 * una tarjeta con su icono y una línea que dice qué responde, y los parámetros que hacen falta
 * (rango de fechas, días) se eligen antes, con un calendario en vez de tecleando AAAA-MM-DD.
 */
@Composable
private fun Selector(estado: ReportesUiState, vm: ReportesViewModel) {
    Text(
        "Parámetros",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        CampoFecha(
            valor = estado.desde,
            alCambiar = { vm.cambiarRango(it, estado.hasta) },
            etiqueta = "Desde",
            modifier = Modifier.weight(1f),
        )
        CampoFecha(
            valor = estado.hasta,
            alCambiar = { vm.cambiarRango(estado.desde, it) },
            etiqueta = "Hasta",
            modifier = Modifier.weight(1f),
        )
    }
    CampoCantidad(
        estado.dias.toString(),
        { it.toIntOrNull()?.let(vm::cambiarDias) },
        "Días sin movimiento",
        admiteDecimales = false,
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Reporte.entries.forEach { reporte ->
        TarjetaReporte(reporte) { vm.abrir(reporte) }
    }
}

@Composable
private fun TarjetaReporte(reporte: Reporte, alPulsar: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = alPulsar),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.padding(Dimensiones.espacio),
            horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                iconoDe(reporte),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimensiones.icono),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo)) {
                Text(reporte.titulo, style = MaterialTheme.typography.titleMedium)
                Text(
                    explicacionDe(reporte),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Iconos.avanzar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(Dimensiones.icono),
            )
        }
    }
}

private fun iconoDe(reporte: Reporte): ImageVector = when (reporte) {
    Reporte.BAJO_MINIMO -> Iconos.reponer
    Reporte.AGOTADOS -> Iconos.merma
    Reporte.SIN_MOVIMIENTO -> Iconos.historial
    Reporte.VALORIZACION -> Iconos.reporte
    Reporte.COMPRAS -> Iconos.proveedor
    Reporte.MERMAS -> Iconos.merma
    Reporte.DISCREPANCIAS -> Iconos.conteo
}

private fun explicacionDe(reporte: Reporte): String = when (reporte) {
    Reporte.BAJO_MINIMO -> "Qué hay que pedir, ordenado por urgencia."
    Reporte.AGOTADOS -> "Lo que ya está en cero."
    Reporte.SIN_MOVIMIENTO -> "Plata quieta en el estante."
    Reporte.VALORIZACION -> "Cuánto vale el inventario, a costo."
    Reporte.COMPRAS -> "Cuánto se recibió y cuánto se facturó."
    Reporte.MERMAS -> "Lo que se perdió y por qué."
    Reporte.DISCREPANCIAS -> "Salidas forzadas por encima del stock."
}

@Composable
private fun Contenido(reporte: Reporte, s: ReportesUiState, alAbrirProducto: (String) -> Unit) {
    when (reporte) {
        Reporte.BAJO_MINIMO ->
            if (s.bajoMinimo.isEmpty()) {
                Vacio("Ningún producto está bajo su mínimo.")
            } else {
                s.bajoMinimo.forEach { f ->
                    Fila(
                        f.producto.nombre,
                        "stock ${f.stockActual.texto()} · mínimo ${f.stockMinimo.texto()} · faltan ${f.deficit.texto()}",
                        Estado.bajoMinimo,
                    ) { alAbrirProducto(f.producto.id) }
                }
            }

        Reporte.AGOTADOS ->
            if (s.agotados.isEmpty()) {
                Vacio("No hay productos agotados.")
            } else {
                s.agotados.forEach { f ->
                    Fila(f.producto.nombre, "stock ${f.stockActual.texto()}", Estado.agotado) {
                        alAbrirProducto(f.producto.id)
                    }
                }
            }

        Reporte.SIN_MOVIMIENTO ->
            if (s.sinMovimiento.isEmpty()) {
                Vacio("Todo se ha movido en los últimos ${s.dias} días.")
            } else {
                s.sinMovimiento.forEach { f ->
                    Fila(
                        f.producto.nombre,
                        "stock ${f.stockActual.texto()}" + (f.valorACosto?.let { " · ${it.texto()}" } ?: " · sin costo"),
                    ) { alAbrirProducto(f.producto.id) }
                }
            }

        Reporte.VALORIZACION -> s.valorizacion?.let { v ->
            Total("${v.total.texto()}", "${v.productosValorizados} productos valorizados")
            Subtitulo("Por categoría")
            v.porCategoria.forEach { c ->
                Fila(c.categoria?.nombre ?: "Sin categoría", "${c.productos} productos · ${c.valor.texto()}")
            }
            if (v.noValorizables.datos.isNotEmpty()) {
                Subtitulo("Con stock pero sin costo", MaterialTheme.colorScheme.error)
                v.noValorizables.datos.forEach { f ->
                    Fila(f.producto.nombre, "stock ${f.stockActual.texto()}") { alAbrirProducto(f.producto.id) }
                }
            }
        }

        Reporte.COMPRAS -> s.compras?.let { c ->
            Total(c.totalRecibido.texto(), "recibido en ${c.recepciones} recepciones")
            Total(c.totalFacturado.texto(), "facturado en ${c.facturas} facturas")
            Subtitulo("Por proveedor")
            c.porProveedor.forEach { p ->
                Fila(p.proveedor.nombre, "recibido ${p.totalRecibido.texto()} · facturado ${p.totalFacturado.texto()}")
            }
            Subtitulo("Por categoría")
            c.porCategoria.forEach { k ->
                Fila(k.categoria?.nombre ?: "Sin categoría", "recibido ${k.totalRecibido.texto()}")
            }
        }

        Reporte.MERMAS -> s.mermas?.let { m ->
            Total(m.totalValor.texto(), "${m.totalCantidad.texto()} unidades perdidas")
            Subtitulo("Por motivo")
            m.porMotivo.forEach { k -> Fila(k.etiqueta, "${k.cantidad.texto()} · ${k.valor.texto()}") }
            Subtitulo("Por producto")
            m.porProducto.datos.forEach { p ->
                Fila(p.producto.nombre, "${p.cantidad.texto()} · ${p.valor.texto()}") { alAbrirProducto(p.producto.id) }
            }
        }

        Reporte.DISCREPANCIAS ->
            if (s.discrepancias.isEmpty()) {
                Vacio("No hay movimientos forzados.")
            } else {
                s.discrepancias.forEach { d ->
                    Fila(
                        "${d.producto.nombre} · ${d.tipo.codigo} ${d.cantidad.texto()}",
                        "quedó ${d.stockResultante.texto()} · ${d.motivo}" + (d.nota?.let { " · $it" } ?: ""),
                    ) { alAbrirProducto(d.producto.id) }
                }
            }
    }
}

/** La cifra que resume el reporte, para no tener que leer la lista entera. */
@Composable
private fun Total(cifra: String, explicacion: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimensiones.espacioCompacto),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
    ) {
        Text(cifra, style = Tipografia.stockSecundario, color = MaterialTheme.colorScheme.primary)
        Text(
            explicacion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Subtitulo(texto: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        texto,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        modifier = Modifier.padding(top = Dimensiones.espacioCompacto),
    )
}

@Composable
private fun Fila(
    titulo: String,
    detalle: String,
    colorCifra: androidx.compose.ui.graphics.Color? = null,
    alPulsar: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (alPulsar != null) Modifier.clickable(onClick = alPulsar) else Modifier)
            .padding(vertical = Dimensiones.espacioMedio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
    ) {
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text(
            detalle,
            style = Tipografia.numeroCuerpo,
            color = colorCifra ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Vacio(texto: String) {
    EstadoVacio(titulo = "Nada que mostrar", explicacion = texto, icono = Iconos.reporte)
}

private fun Cantidad.texto() = Formato.cantidad(valor)
private fun Dinero.texto() = Formato.monto(aApi().monto, moneda.toString())
