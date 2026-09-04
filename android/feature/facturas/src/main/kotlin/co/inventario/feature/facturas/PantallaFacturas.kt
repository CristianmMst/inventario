package co.inventario.feature.facturas

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.BotonTexto
import co.inventario.designsystem.componentes.CampoFecha
import co.inventario.designsystem.componentes.ChipFiltro
import co.inventario.designsystem.componentes.DialogoConfirmacion
import co.inventario.designsystem.componentes.EsqueletoLista
import co.inventario.designsystem.componentes.EstadoError
import co.inventario.designsystem.componentes.EstadoVacio
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.componentes.PildoraEstadoOrden
import co.inventario.designsystem.componentes.Formato
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Estado
import co.inventario.designsystem.tema.Iconos
import co.inventario.designsystem.tema.Tipografia
import co.inventario.domain.modelo.EstadoPago
import co.inventario.domain.modelo.Factura
import java.io.File
import java.time.LocalDate

/** T-092 / T-093: listado con total del filtro, pago con fecha y exportación por el menú de compartir. */
@Composable
fun PantallaFacturas(alNueva: () -> Unit, alVolver: () -> Unit, vm: FacturasListadoViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    var desde by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var hasta by remember { mutableStateOf(LocalDate.now()) }
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

    PantallaInventario(
        titulo = "Facturas de compra",
        alVolver = alVolver,
        acciones = { BotonPrincipal("Registrar factura", alNueva, icono = Iconos.anadir) },
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            FlowRow(
                Modifier.padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioCompacto),
                horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
            ) {
                ChipFiltro(
                    texto = "Todas",
                    activo = estado.filtros.estadoPago == null,
                    alPulsar = { vm.filtrarPorEstado(null) },
                )
                EstadoPago.entries.forEach { estadoPago ->
                    ChipFiltro(
                        texto = estadoPago.etiqueta,
                        activo = estado.filtros.estadoPago == estadoPago,
                        alPulsar = { vm.filtrarPorEstado(estadoPago) },
                    )
                }
            }

            estado.totalFiltro?.let { total ->
                Column(
                    Modifier.padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioCompacto),
                    verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
                ) {
                    Text(
                        Formato.monto(total.aApi().monto, total.moneda.toString()),
                        style = Tipografia.stockSecundario,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${estado.cantidadFiltro} facturas en este filtro",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MensajeError(estado.error)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val error = estado.error
            when {
                error != null && estado.facturas.isEmpty() ->
                    EstadoError(error, vm::recargar, Modifier.weight(1f))

                estado.cargando && estado.facturas.isEmpty() -> EsqueletoLista(Modifier.weight(1f))

                estado.facturas.isEmpty() -> EstadoVacio(
                    titulo = "No hay facturas con ese filtro",
                    explicacion = "Aquí se guardan las facturas de compra, con su foto, para pasárselas al contador.",
                    icono = Iconos.factura,
                    textoAccion = "Registrar una factura",
                    alAccionar = alNueva,
                    modifier = Modifier.weight(1f),
                )

                else -> LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(estado.facturas, key = { _, f -> f.id }) { indice, factura ->
                        if (indice >= estado.facturas.size - 5) {
                            LaunchedEffect(indice) { vm.cargarMas() }
                        }
                        FilaFactura(factura, alPagar = { vm.pedirPago(factura.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            Exportacion(
                desde = desde,
                hasta = hasta,
                exportando = estado.exportando,
                alCambiarDesde = { desde = it },
                alCambiarHasta = { hasta = it },
                alExportar = { vm.exportar(desde, hasta) },
            )
        }
    }

    if (estado.pagando != null) {
        DialogoConfirmacion(
            titulo = "Marcar como pagada",
            texto = "Elige la fecha en que se pagó (RF-FAC-004).",
            textoConfirmar = "Pagada",
            alConfirmar = vm::confirmarPago,
            alCancelar = vm::cancelarPago,
            contenidoExtra = {
                CampoFecha(
                    valor = estado.fechaPago.aFechaONulo(),
                    alCambiar = { vm.cambiarFechaPago(it.toString()) },
                    etiqueta = "Fecha de pago",
                    error = estado.erroresCampo["fechaPago"],
                )
            },
        )
    }
}

/** RF-FAC-007: el paquete que se le pasa al contador a fin de mes. */
@Composable
private fun Exportacion(
    desde: LocalDate,
    hasta: LocalDate,
    exportando: Boolean,
    alCambiarDesde: (LocalDate) -> Unit,
    alCambiarHasta: (LocalDate) -> Unit,
    alExportar: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
    ) {
        Text(
            "Exportar para el contador",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            CampoFecha(desde, alCambiarDesde, "Desde", Modifier.weight(1f))
            CampoFecha(hasta, alCambiarHasta, "Hasta", Modifier.weight(1f))
        }
        BotonSecundario(
            if (exportando) "Descargando…" else "Exportar y compartir",
            alExportar,
            habilitado = !exportando,
        )
    }
}

@Composable
private fun FilaFactura(factura: Factura, alPagar: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimensiones.alturaFilaLista)
            .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(factura.numero, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                Formato.monto(factura.total.aApi().monto, factura.moneda.toString()),
                style = Tipografia.numeroFila,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                factura.proveedor.nombre,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            PildoraEstadoOrden(
                texto = factura.estadoPago.etiqueta,
                contenedor = when (factura.estadoPago) {
                    EstadoPago.PAGADA -> Estado.enRangoContenedor
                    EstadoPago.PENDIENTE -> Estado.bajoMinimoContenedor
                    else -> Estado.neutroContenedor
                },
                sobreContenedor = when (factura.estadoPago) {
                    EstadoPago.PAGADA -> Estado.sobreEnRangoContenedor
                    EstadoPago.PENDIENTE -> Estado.sobreBajoMinimoContenedor
                    else -> Estado.neutro
                },
            )
        }
        Text(
            "${factura.fechaEmision}" + (factura.fechaPago?.let { " · pagada el $it" } ?: "") +
                " · ${factura.imagenes.size} imagen(es)" +
                if (factura.recepciones.isNotEmpty()) {
                    " · recepciones ${factura.recepciones.joinToString { it.numero }}"
                } else {
                    ""
                },
            style = Tipografia.numeroCuerpo,
            color = MaterialTheme.colorScheme.outline,
        )
        if (factura.estadoPago == EstadoPago.PENDIENTE) {
            BotonTexto("Marcar pagada", alPagar)
        }
    }
}

internal fun String.aFechaONulo(): LocalDate? =
    takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
