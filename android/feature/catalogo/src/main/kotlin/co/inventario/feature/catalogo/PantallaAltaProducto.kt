package co.inventario.feature.catalogo

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoCantidad
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.ChipFiltro
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos
import co.inventario.domain.modelo.LimitesImagen
import co.inventario.imagenes.CompresorImagen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * T-082: alta y edición (RF-CAT-001, RF-CAT-006). La foto la toma la cámara del sistema y se
 * comprime aquí, en el celular, antes de subirla (RNF-05).
 *
 * El formulario era una lista plana de once campos seguidos. Ahora va por secciones —qué es,
 * cómo se mide, cuánto cuesta, foto—, que es como se responde de verdad al dar de alta algo
 * mientras el proveedor espera.
 */
@Composable
fun PantallaAltaProducto(
    codigoBarras: String?,
    productoId: String?,
    monedaBase: String,
    alGuardar: (String) -> Unit,
    alCancelar: () -> Unit,
    vm: AltaProductoViewModel = hiltViewModel<AltaProductoViewModel, AltaProductoViewModel.Fabrica>(
        creationCallback = { it.crear(codigoBarras, productoId, monedaBase) },
    ),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val ambito = rememberCoroutineScope()
    var archivoFoto by remember { mutableStateOf<File?>(null) }
    var comprimiendo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.sucesos.collect { if (it is AltaSuceso.ProductoGuardado) alGuardar(it.productoId) } }

    val tomarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val archivo = archivoFoto
        if (ok && archivo != null) {
            comprimiendo = true
            ambito.launch {
                val comprimida = withContext(Dispatchers.Default) {
                    runCatching { CompresorImagen.comprimir(archivo.readBytes(), LimitesImagen.PRODUCTO) }.getOrNull()
                }
                archivo.delete()
                comprimiendo = false
                comprimida?.let(vm::adjuntarFoto)
            }
        }
    }

    PantallaInventario(
        titulo = if (estado.edicion) "Editar producto" else "Nuevo producto",
        alVolver = alCancelar,
        acciones = {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                BotonSecundario("Cancelar", alCancelar, Modifier.weight(1f), habilitado = !estado.cargando)
                Box(Modifier.weight(2f)) {
                    BotonPrincipal(
                        if (estado.cargando) "Guardando…" else "Guardar",
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
            Seccion("Identificación") {
                if (!estado.edicion && estado.codigoBarras.isNotBlank()) {
                    Text(
                        "Con el código ${estado.codigoBarras} ya puesto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CampoTexto(
                    estado.nombre,
                    vm::cambiarNombre,
                    "Nombre",
                    error = estado.erroresCampo["nombre"],
                    habilitado = !estado.cargando,
                )
                CampoTexto(estado.sku, vm::cambiarSku, "SKU (opcional)", habilitado = !estado.cargando)
                if (!estado.edicion) {
                    CampoTexto(
                        estado.codigoBarras,
                        vm::cambiarCodigoBarras,
                        "Código de barras",
                        tipoTeclado = KeyboardType.Number,
                        habilitado = !estado.cargando,
                    )
                }
            }

            Seccion("Cómo se mide") {
                Etiqueta("Unidad de medida")
                Chips {
                    estado.unidades.forEach { unidad ->
                        ChipFiltro(
                            texto = unidad.nombre,
                            activo = unidad.codigo == estado.unidadCodigo,
                            alPulsar = { vm.cambiarUnidad(unidad.codigo) },
                        )
                    }
                }
                estado.erroresCampo["unidad"]?.let { MensajeError(it) }

                if (estado.categorias.isNotEmpty()) {
                    Etiqueta("Categoría")
                    Chips {
                        ChipFiltro(
                            texto = "Sin categoría",
                            activo = estado.categoriaId == null,
                            alPulsar = { vm.cambiarCategoria(null) },
                        )
                        estado.categorias.forEach { categoria ->
                            ChipFiltro(
                                texto = categoria.nombre,
                                activo = categoria.id == estado.categoriaId,
                                alPulsar = { vm.cambiarCategoria(categoria.id) },
                            )
                        }
                    }
                }
            }

            Seccion("Precios y mínimo") {
                CampoCantidad(estado.costo, vm::cambiarCosto, "Costo actual ($monedaBase)", error = estado.erroresCampo["costo"])
                CampoCantidad(estado.precio, vm::cambiarPrecio, "Precio de venta ($monedaBase)", error = estado.erroresCampo["precio"])
                CampoCantidad(
                    estado.stockMinimo,
                    vm::cambiarStockMinimo,
                    "Stock mínimo",
                    error = estado.erroresCampo["stockMinimo"],
                )
                Text(
                    "Cuando el stock llegue al mínimo, el producto aparecerá en «Reponer».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Seccion("Foto") {
                BotonSecundario(
                    texto = when {
                        comprimiendo -> "Preparando la foto…"
                        estado.fotoJpeg != null -> "Foto lista (${estado.fotoJpeg!!.size / 1024} KB) · Tomar otra"
                        else -> "Tomar foto"
                    },
                    alPulsar = {
                        val archivo = File(
                            File(contexto.cacheDir, "fotos").apply { mkdirs() },
                            "producto_${System.currentTimeMillis()}.jpg",
                        )
                        archivoFoto = archivo
                        tomarFoto.launch(uriPara(contexto, archivo))
                    },
                    habilitado = !comprimiendo && !estado.cargando,
                    icono = Iconos.camara,
                )
            }

            MensajeError(estado.error)
        }
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

@Composable
private fun Etiqueta(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Chips(contenido: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
    ) {
        contenido()
    }
}

private fun uriPara(contexto: Context, archivo: File): Uri =
    FileProvider.getUriForFile(contexto, "${contexto.packageName}.fotos", archivo)
