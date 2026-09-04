package co.inventario.feature.compras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoFecha
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.DialogoConfirmacion
import co.inventario.designsystem.componentes.EsqueletoLista
import co.inventario.designsystem.componentes.EstadoError
import co.inventario.designsystem.componentes.EstadoVacio
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.componentes.PildoraEstadoOrden
import co.inventario.designsystem.componentes.TarjetaDatos
import co.inventario.designsystem.componentes.FilaDato
import co.inventario.designsystem.componentes.Formato
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos
import co.inventario.designsystem.tema.Tipografia
import java.time.LocalDate

@Composable
fun PantallaOrdenes(
    alAbrir: (String) -> Unit,
    alNueva: () -> Unit,
    alVolver: () -> Unit,
    vm: OrdenesListadoViewModel = hiltViewModel(),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.recargar() }

    PantallaInventario(
        titulo = "Órdenes de compra",
        alVolver = alVolver,
        acciones = { BotonPrincipal("Nueva orden", alNueva, icono = Iconos.anadir) },
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            val error = estado.error
            when {
                error != null && estado.ordenes.isEmpty() ->
                    EstadoError(error, vm::recargar, Modifier.weight(1f))

                estado.cargando && estado.ordenes.isEmpty() -> EsqueletoLista(Modifier.weight(1f))

                estado.ordenes.isEmpty() -> EstadoVacio(
                    titulo = "No hay órdenes",
                    explicacion = "Las órdenes son opcionales: puedes recibir mercancía sin haber pedido antes.",
                    icono = Iconos.orden,
                    textoAccion = "Crear una orden",
                    alAccionar = alNueva,
                    modifier = Modifier.weight(1f),
                )

                else -> LazyColumn(Modifier.weight(1f)) {
                    items(estado.ordenes, key = { it.id }) { orden ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = Dimensiones.alturaFilaLista)
                                .clickable { alAbrir(orden.id) }
                                .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
                            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                            ) {
                                Text(
                                    orden.numero,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                PildoraEstadoOrden(orden.estado.etiqueta)
                            }
                            Text(
                                orden.proveedor.nombre,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${orden.lineas.size} líneas" +
                                    (orden.totalEstimado?.let { " · " + Formato.monto(it.aApi().monto, it.moneda.toString()) } ?: ""),
                                style = Tipografia.numeroCuerpo,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
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

    PantallaInventario(
        titulo = orden?.numero ?: "Orden",
        alVolver = alVolver,
        acciones = if (orden != null) {
            {
                if (estado.puedeRecibir) {
                    BotonPrincipal("Recibir contra esta orden", { alRecibir(orden.id) }, icono = Iconos.reponer)
                }
                if (estado.puedeEmitir) {
                    BotonPrincipal("Emitir la orden", vm::emitir, habilitado = !estado.cargando, icono = Iconos.confirmar)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                    if (estado.puedeCerrarConFaltante) {
                        BotonSecundario("Cerrar con faltante", vm::pedirCierreConFaltante, Modifier.weight(1f))
                    }
                    if (estado.puedeCancelar) {
                        BotonSecundario(
                            "Cancelar la orden",
                            vm::pedirCancelacion,
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        } else {
            null
        },
    ) { relleno ->
        if (orden == null) {
            EstadoError(
                texto = estado.error ?: "No se pudo cargar la orden.",
                alReintentar = vm::recargar,
                modifier = Modifier.fillMaxSize().padding(relleno),
            )
            return@PantallaInventario
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensiones.espacio),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioAmplio),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
            ) {
                Text(orden.proveedor.nombre, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                PildoraEstadoOrden(orden.estado.etiqueta)
            }

            TarjetaDatos {
                orden.fechaEsperada?.let { FilaDato("Esperada para", it.toString()) }
                FilaDato("Líneas", "${orden.lineas.size}")
                orden.totalEstimado?.let {
                    FilaDato("Total estimado", Formato.monto(it.aApi().monto, it.moneda.toString()), ultima = orden.notas == null)
                }
                orden.notas?.let { FilaDato("Notas", it, ultima = true) }
            }

            orden.motivoCierre?.let {
                Text("Cierre: $it", style = MaterialTheme.typography.bodyLarge)
            }

            if (!estado.puedeEditarLineas) {
                Text(
                    "Las líneas ya no se editan: la orden está ${orden.estado.etiqueta.lowercase()}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                Text("Productos", style = MaterialTheme.typography.titleMedium)
                orden.lineas.forEach { linea ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = Dimensiones.areaTactilMinima)
                            .padding(vertical = Dimensiones.espacioCompacto),
                        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
                    ) {
                        Text(
                            linea.producto.nombre,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${linea.cantidadPendiente.valor.stripTrailingZeros().toPlainString()} / " +
                                linea.cantidadOrdenada.valor.stripTrailingZeros().toPlainString(),
                            style = Tipografia.numeroCuerpo,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Text(
                    "Pendiente / pedido",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            MensajeError(estado.error)
        }
    }

    estado.cierre?.let { tipo ->
        DialogoConfirmacion(
            titulo = if (tipo == TipoCierre.CANCELAR) "Cancelar la orden" else "Cerrar con faltante",
            texto = if (tipo == TipoCierre.CANCELAR) {
                "La orden no admitirá recepciones. Escribe el motivo."
            } else {
                "Lo pendiente no llegará; la orden no admitirá más recepciones. Escribe el motivo."
            },
            textoConfirmar = "Confirmar",
            alConfirmar = vm::confirmarCierre,
            alCancelar = vm::cancelarCierre,
            destructivo = true,
            textoCancelar = "Volver",
            contenidoExtra = {
                CampoTexto(estado.motivo, vm::cambiarMotivo, "Motivo", error = estado.erroresCampo["motivo"])
            },
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

    PantallaInventario(
        titulo = "Nueva orden de compra",
        alVolver = alCancelar,
        acciones = {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                BotonSecundario("Cancelar", alCancelar, Modifier.weight(1f), habilitado = !estado.cargando)
                Box(Modifier.weight(2f)) {
                    BotonPrincipal(
                        if (estado.cargando) "Guardando…" else "Guardar borrador",
                        vm::guardar,
                        habilitado = !estado.cargando,
                        icono = Iconos.confirmar,
                    )
                }
            }
        },
    ) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensiones.espacio),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioAmplio),
        ) {
            SelectorProveedor(estado.proveedores, estado.proveedorId, vm::elegirProveedor, estado.erroresCampo["proveedor"])

            CampoFecha(
                valor = estado.fechaEsperada.aFechaONulo(),
                alCambiar = { vm.cambiarFechaEsperada(it.toString()) },
                etiqueta = "Fecha esperada (opcional)",
                error = estado.erroresCampo["fechaEsperada"],
            )
            CampoTexto(estado.notas, vm::cambiarNotas, "Notas (opcional)", lineas = 3)

            Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio)) {
                Text("Productos", style = MaterialTheme.typography.titleMedium)
                estado.lineas.forEachIndexed { indice, linea ->
                    LineaEditable(
                        linea.producto,
                        linea.cantidad,
                        linea.costo,
                        "Costo estimado (${estado.moneda})",
                        null,
                        estado.erroresCampo["cantidad_$indice"],
                        null,
                        { vm.cambiarCantidad(indice, it) },
                        { vm.cambiarCosto(indice, it) },
                        { vm.quitarLinea(indice) },
                    )
                }
                estado.erroresCampo["lineas"]?.let { MensajeError(it) }
                SelectorProducto(alElegir = vm::agregarLinea)
            }

            MensajeError(estado.error)
        }
    }
}

/** El ViewModel guarda la fecha como texto ISO; el calendario habla `LocalDate`. */
internal fun String.aFechaONulo(): LocalDate? =
    takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
