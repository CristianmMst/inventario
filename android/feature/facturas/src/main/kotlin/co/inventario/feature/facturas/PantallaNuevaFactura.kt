package co.inventario.feature.facturas

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.FiltrosRecepciones
import co.inventario.data.repositorio.RepositorioCompras
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoCantidad
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.domain.modelo.LimitesImagen
import co.inventario.domain.modelo.Proveedor
import co.inventario.domain.modelo.Recepcion
import co.inventario.imagenes.CompresorImagen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Datos auxiliares del formulario: proveedores y recepciones confirmadas del proveedor elegido (RF-FAC-006). */
@HiltViewModel
class AuxiliaresFacturaViewModel @Inject constructor(private val compras: RepositorioCompras) : ViewModel() {
    data class Estado(val proveedores: List<Proveedor> = emptyList(), val recepciones: List<Recepcion> = emptyList())

    private val _estado = MutableStateFlow(Estado())
    val estado = _estado.asStateFlow()

    init { viewModelScope.launch { (compras.proveedores() as? Resultado.Exito)?.let { r -> _estado.update { it.copy(proveedores = r.valor) } } } }

    fun cargarRecepciones(proveedorId: String) {
        viewModelScope.launch {
            (compras.recepciones(FiltrosRecepciones(proveedorId = proveedorId, estado = "confirmada")) as? Resultado.Exito)
                ?.let { r -> _estado.update { it.copy(recepciones = r.valor.datos) } }
        }
    }
}

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
                val comprimida = withContext(Dispatchers.Default) { runCatching { CompresorImagen.comprimir(archivo.readBytes(), LimitesImagen.FACTURA) }.getOrNull() }
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
        Text("Registrar factura de compra", style = MaterialTheme.typography.headlineMedium)
        Text("Proveedor", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            aux.proveedores.forEach { p ->
                FilterChip(selected = p.id == estado.proveedorId, onClick = { vm.elegirProveedor(p.id); auxiliares.cargarRecepciones(p.id) }, label = { Text(p.nombre) })
            }
        }
        estado.erroresCampo["proveedor"]?.let { MensajeError(it) }
        CampoTexto(estado.numero, vm::cambiarNumero, "Número de factura", error = estado.erroresCampo["numero"])
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            CampoTexto(estado.fechaEmision, vm::cambiarFechaEmision, "Emisión (AAAA-MM-DD)", Modifier.weight(1f), error = estado.erroresCampo["fechaEmision"])
            CampoTexto(estado.fechaVencimiento, vm::cambiarFechaVencimiento, "Vence (opcional)", Modifier.weight(1f), error = estado.erroresCampo["fechaVencimiento"])
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            CampoTexto(estado.moneda, vm::cambiarMoneda, "Moneda", Modifier.weight(1f), error = estado.erroresCampo["moneda"])
            if (estado.moneda != monedaBase) CampoCantidad(estado.tasaCambio, vm::cambiarTasa, "Tasa a $monedaBase", Modifier.weight(2f), error = estado.erroresCampo["tasaCambio"])
        }
        CampoCantidad(estado.base, vm::cambiarBase, "Base gravable", error = estado.erroresCampo["base"])
        CampoCantidad(estado.impuesto, vm::cambiarImpuesto, "Impuesto (IVA)", error = estado.erroresCampo["impuesto"])
        CampoCantidad(estado.total, vm::cambiarTotal, "Total", error = estado.erroresCampo["total"])
        estado.diferenciaCuadre?.let {
            Text("Base más impuesto no da el total: diferencia de $it.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        }
        if (aux.recepciones.isNotEmpty()) {
            Text("Recepciones de este proveedor (opcional)", style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                aux.recepciones.forEach { r ->
                    FilterChip(selected = r.id in estado.recepciones, onClick = { vm.alternarRecepcion(r.id) }, label = { Text("${r.numero} · ${r.total.aApi().monto}") })
                }
            }
        }
        CampoTexto(estado.notas, vm::cambiarNotas, "Notas (opcional)")
        BotonSecundario(
            when {
                comprimiendo -> "Preparando foto…"
                estado.fotos.isEmpty() -> "Fotografiar la factura"
                else -> "${estado.fotos.size} foto(s) · ${estado.fotos.sumOf { it.size } / 1024} KB · Tomar otra"
            },
            {
                val archivo = File(File(contexto.cacheDir, "fotos").apply { mkdirs() }, "factura_${System.currentTimeMillis()}.jpg")
                archivoFoto = archivo
                tomarFoto.launch(uriPara(contexto, archivo))
            },
            habilitado = !comprimiendo && !estado.cargando,
        )
        MensajeError(estado.error)
        BotonPrincipal(if (estado.cargando) "Guardando…" else "Registrar", vm::guardar, habilitado = !estado.cargando && !comprimiendo)
        BotonSecundario("Cancelar", alCancelar, habilitado = !estado.cargando)
    }
}

private fun uriPara(contexto: Context, archivo: File): Uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fotos", archivo)
