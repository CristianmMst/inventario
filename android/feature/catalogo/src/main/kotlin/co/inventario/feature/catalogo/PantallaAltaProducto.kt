package co.inventario.feature.catalogo

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoCantidad
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.domain.modelo.LimitesImagen
import co.inventario.imagenes.CompresorImagen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * T-082: alta y edición (RF-CAT-001, RF-CAT-006). La foto la toma la cámara del sistema y se
 * comprime aquí, en el celular, antes de subirla (RNF-05).
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

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Text(if (estado.edicion) "Editar producto" else "Nuevo producto", style = MaterialTheme.typography.headlineMedium)
        if (!estado.edicion && estado.codigoBarras.isNotBlank()) {
            Text("Con el código ${estado.codigoBarras} ya puesto.", style = MaterialTheme.typography.bodyLarge)
        }
        CampoTexto(estado.nombre, vm::cambiarNombre, "Nombre", error = estado.erroresCampo["nombre"], habilitado = !estado.cargando)
        Text("Unidad de medida", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            estado.unidades.forEach { u ->
                FilterChip(selected = u.codigo == estado.unidadCodigo, onClick = { vm.cambiarUnidad(u.codigo) }, label = { Text(u.nombre) })
            }
        }
        estado.erroresCampo["unidad"]?.let { MensajeError(it) }
        if (estado.categorias.isNotEmpty()) {
            Text("Categoría", style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                FilterChip(selected = estado.categoriaId == null, onClick = { vm.cambiarCategoria(null) }, label = { Text("Sin categoría") })
                estado.categorias.forEach { c ->
                    FilterChip(selected = c.id == estado.categoriaId, onClick = { vm.cambiarCategoria(c.id) }, label = { Text(c.nombre) })
                }
            }
        }
        CampoTexto(estado.sku, vm::cambiarSku, "SKU (opcional)", habilitado = !estado.cargando)
        CampoCantidad(estado.costo, vm::cambiarCosto, "Costo actual ($monedaBase)", error = estado.erroresCampo["costo"])
        CampoCantidad(estado.precio, vm::cambiarPrecio, "Precio de venta ($monedaBase)", error = estado.erroresCampo["precio"])
        CampoCantidad(estado.stockMinimo, vm::cambiarStockMinimo, "Stock mínimo", error = estado.erroresCampo["stockMinimo"])
        if (!estado.edicion) {
            CampoTexto(estado.codigoBarras, vm::cambiarCodigoBarras, "Código de barras", tipoTeclado = KeyboardType.Number, habilitado = !estado.cargando)
        }
        BotonSecundario(
            when {
                comprimiendo -> "Preparando foto…"
                estado.fotoJpeg != null -> "Foto lista (${estado.fotoJpeg!!.size / 1024} KB) · Tomar otra"
                else -> "Tomar foto"
            },
            {
                val archivo = File(File(contexto.cacheDir, "fotos").apply { mkdirs() }, "producto_${System.currentTimeMillis()}.jpg")
                archivoFoto = archivo
                tomarFoto.launch(uriPara(contexto, archivo))
            },
            habilitado = !comprimiendo && !estado.cargando,
        )
        MensajeError(estado.error)
        BotonPrincipal(if (estado.cargando) "Guardando…" else "Guardar", vm::guardar, habilitado = !estado.cargando && !comprimiendo)
        BotonSecundario("Cancelar", alCancelar, habilitado = !estado.cargando)
    }
}

private fun uriPara(contexto: Context, archivo: File): Uri =
    FileProvider.getUriForFile(contexto, "${contexto.packageName}.fotos", archivo)
