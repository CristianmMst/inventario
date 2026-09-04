package co.inventario.feature.facturas

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoCantidad
import co.inventario.designsystem.componentes.CampoFecha
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.ChipFiltro
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.componentes.Formato
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Estado
import co.inventario.designsystem.tema.Iconos
import co.inventario.domain.modelo.LimitesImagen
import co.inventario.imagenes.CompresorImagen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** T-091: registro de factura con base, IVA y total; el cuadre se valida antes de enviar; fotos ≤ 1,5 MB. */
@Composable
fun PantallaNuevaFactura(
    monedaBase: String,
    alRegistrar: (String) -> Unit,
    alCancelar: () -> Unit,
    vm: FacturaViewModel = hiltViewModel<FacturaViewModel, FacturaViewModel.Fabrica>(creationCallback = { it.crear(monedaBase) }),
    auxiliares: AuxiliaresFacturaViewModel = hiltViewModel(),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val aux by auxiliares.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val ambito = rememberCoroutineScope()
    var archivoFoto by remember { mutableStateOf<File?>(null) }
    var comprimiendo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.sucesos.collect { if (it is FacturaSuceso.Registrada) alRegistrar(it.facturaId) } }

    val tomarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val archivo = archivoFoto
        if (ok && archivo != null) {
            comprimiendo = true
            ambito.launch {
                // RNF-05: factura hasta 2048 px y 1,5 MB, sin recorte: el número debe leerse al 100 %.
                val comprimida = withContext(Dispatchers.Default) {
                    runCatching { CompresorImagen.comprimir(archivo.readBytes(), LimitesImagen.FACTURA) }.getOrNull()
                }
                archivo.delete()
                comprimiendo = false
                comprimida?.let(vm::adjuntarFoto)
            }
        }
    }

    PantallaInventario(
        titulo = "Registrar factura",
        alVolver = alCancelar,
        acciones = {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                BotonSecundario("Cancelar", alCancelar, Modifier.weight(1f), habilitado = !estado.cargando)
                Box(Modifier.weight(2f)) {
                    BotonPrincipal(
                        if (estado.cargando) "Guardando…" else "Registrar",
                        vm::guardar,
                        habilitado = !estado.cargando && !comprimiendo,
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
            Seccion("De quién y cuándo") {
                Text(
                    "Proveedor",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                    verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                ) {
                    aux.proveedores.forEach { proveedor ->
                        ChipFiltro(
                            texto = proveedor.nombre,
                            activo = proveedor.id == estado.proveedorId,
                            alPulsar = {
                                vm.elegirProveedor(proveedor.id)
                                auxiliares.cargarRecepciones(proveedor.id)
                            },
                        )
                    }
                }
                estado.erroresCampo["proveedor"]?.let { MensajeError(it) }

                CampoTexto(
                    estado.numero,
                    vm::cambiarNumero,
                    "Número de factura",
                    error = estado.erroresCampo["numero"],
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                    CampoFecha(
                        valor = estado.fechaEmision.aFechaONulo(),
                        alCambiar = { vm.cambiarFechaEmision(it.toString()) },
                        etiqueta = "Emisión",
                        modifier = Modifier.weight(1f),
                        error = estado.erroresCampo["fechaEmision"],
                    )
                    CampoFecha(
                        valor = estado.fechaVencimiento.aFechaONulo(),
                        alCambiar = { vm.cambiarFechaVencimiento(it.toString()) },
                        etiqueta = "Vence (opcional)",
                        modifier = Modifier.weight(1f),
                        error = estado.erroresCampo["fechaVencimiento"],
                    )
                }
            }

            Seccion("Importes") {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                    CampoTexto(
                        estado.moneda,
                        vm::cambiarMoneda,
                        "Moneda",
                        Modifier.weight(1f),
                        error = estado.erroresCampo["moneda"],
                    )
                    if (estado.moneda != monedaBase) {
                        CampoCantidad(
                            estado.tasaCambio,
                            vm::cambiarTasa,
                            "Tasa a $monedaBase",
                            Modifier.weight(2f),
                            error = estado.erroresCampo["tasaCambio"],
                        )
                    }
                }
                CampoCantidad(estado.base, vm::cambiarBase, "Base gravable", error = estado.erroresCampo["base"])
                CampoCantidad(estado.impuesto, vm::cambiarImpuesto, "Impuesto (IVA)", error = estado.erroresCampo["impuesto"])
                CampoCantidad(estado.total, vm::cambiarTotal, "Total", error = estado.erroresCampo["total"])
                Cuadre(estado.diferenciaCuadre, estado.base, estado.impuesto, estado.total)
            }

            if (aux.recepciones.isNotEmpty()) {
                Seccion("Recepciones de este proveedor (opcional)") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                    ) {
                        aux.recepciones.forEach { recepcion ->
                            ChipFiltro(
                                texto = recepcion.numero + " · " + Formato.monto(recepcion.total.aApi().monto),
                                activo = recepcion.id in estado.recepciones,
                                alPulsar = { vm.alternarRecepcion(recepcion.id) },
                            )
                        }
                    }
                }
            }

            Seccion("Foto y notas") {
                BotonSecundario(
                    texto = when {
                        comprimiendo -> "Preparando la foto…"
                        estado.fotos.isEmpty() -> "Fotografiar la factura"
                        else -> "${estado.fotos.size} foto(s) · ${estado.fotos.sumOf { it.size } / 1024} KB · Tomar otra"
                    },
                    alPulsar = {
                        val archivo = File(
                            File(contexto.cacheDir, "fotos").apply { mkdirs() },
                            "factura_${System.currentTimeMillis()}.jpg",
                        )
                        archivoFoto = archivo
                        tomarFoto.launch(uriPara(contexto, archivo))
                    },
                    habilitado = !comprimiendo && !estado.cargando,
                    icono = Iconos.camara,
                )
                Text(
                    "El contador tiene que poder leer número, fecha y monto sin ampliar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CampoTexto(estado.notas, vm::cambiarNotas, "Notas (opcional)", lineas = 3)
            }

            MensajeError(estado.error)
        }
    }
}

/**
 * Base + impuesto tiene que dar el total; la base de datos lo exige. Antes solo aparecía el
 * descuadre en rojo cuando ya lo había: ahora también se ve cuando cuadra, que es la señal de
 * que se puede seguir sin miedo.
 */
@Composable
private fun Cuadre(diferencia: String?, base: String, impuesto: String, total: String) {
    val hayDatos = listOf(base, impuesto, total).any { it.isNotBlank() }
    if (!hayDatos) return
    val cuadra = diferencia == null
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (cuadra) Estado.enRangoContenedor else Estado.agotadoContenedor,
                MaterialTheme.shapes.medium,
            )
            .padding(Dimensiones.espacioMedio),
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (cuadra) Iconos.confirmar else Iconos.error,
            contentDescription = null,
            tint = if (cuadra) Estado.enRango else Estado.agotado,
            modifier = Modifier.size(Dimensiones.icono),
        )
        Text(
            if (cuadra) {
                "Base más impuesto da el total."
            } else {
                "Base más impuesto no da el total: diferencia de $diferencia."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (cuadra) Estado.sobreEnRangoContenedor else Estado.sobreAgotadoContenedor,
        )
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio)) {
        Text(
            titulo,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        contenido()
    }
}

private fun uriPara(contexto: Context, archivo: File): Uri =
    FileProvider.getUriForFile(contexto, "${contexto.packageName}.fotos", archivo)
