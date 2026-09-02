package co.inventario.feature.catalogo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.componentes.StockDestacado
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.domain.modelo.Producto
import coil3.compose.AsyncImage

/** Acciones que la ficha ofrece; las rutas las decide la app (T-084..T-087). */
data class AccionesFicha(
    val registrarSalida: (String) -> Unit,
    val registrarEntrada: (String) -> Unit,
    val registrarMerma: (String) -> Unit,
    val contar: (String) -> Unit,
    val verHistorial: (String) -> Unit,
    val editar: (String) -> Unit,
    val volverAEscanear: () -> Unit,
)

/** T-081: ficha con el stock actual destacado (RF-CAT-008, RF-INV-003). "Salida" es el primer botón: 3 toques. */
@Composable
fun PantallaFicha(
    productoId: String,
    acciones: AccionesFicha,
    vm: FichaProductoViewModel = hiltViewModel<FichaProductoViewModel, FichaProductoViewModel.Fabrica>(
        creationCallback = { it.crear(productoId) },
    ),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    // Al volver de registrar un movimiento la pantalla se recompone: el stock se pide de nuevo (RF-INV-003).
    LaunchedEffect(Unit) { vm.recargar() }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        val producto = estado.producto
        when {
            estado.cargando && producto == null -> CircularProgressIndicator()
            producto == null -> {
                MensajeError(estado.error)
                BotonPrincipal("Reintentar", vm::recargar)
                BotonSecundario("Volver", acciones.volverAEscanear)
            }
            else -> ContenidoFicha(producto, acciones, alArchivar = vm::archivar, error = estado.error)
        }
    }
}

@Composable
private fun ContenidoFicha(producto: Producto, acciones: AccionesFicha, alArchivar: (Boolean) -> Unit, error: String?) {
    Text(producto.nombre, style = MaterialTheme.typography.headlineMedium)
    Text(
        listOfNotNull(producto.sku, producto.categoria?.nombre).joinToString(" · "),
        style = MaterialTheme.typography.bodyLarge,
    )
    if (producto.imagenUrl != null) {
        AsyncImage(model = producto.imagenUrl, contentDescription = "Foto de ${producto.nombre}", modifier = Modifier.fillMaxWidth().height(180.dp))
    }
    StockDestacado(producto.stockActual.aApi().trimEnd('0').trimEnd('.'), producto.unidad.nombre)
    if (producto.bajoMinimo) {
        Text("Bajo mínimo (${producto.stockMinimo?.aApi()?.trimEnd('0')?.trimEnd('.')})", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
    }
    producto.precioVenta?.let { Text("Precio ${it.aApi().monto} ${it.moneda}", style = MaterialTheme.typography.bodyLarge) }
    if (producto.estaArchivado) {
        Text("Producto archivado: no admite movimientos.", style = MaterialTheme.typography.bodyLarge)
        BotonSecundario("Desarchivar", { alArchivar(false) })
    } else {
        BotonPrincipal("Registrar salida", { acciones.registrarSalida(producto.id) })
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            BotonSecundario("Entrada", { acciones.registrarEntrada(producto.id) }, Modifier.weight(1f))
            BotonSecundario("Merma", { acciones.registrarMerma(producto.id) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            BotonSecundario("Conteo", { acciones.contar(producto.id) }, Modifier.weight(1f))
            BotonSecundario("Historial", { acciones.verHistorial(producto.id) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            BotonSecundario("Editar", { acciones.editar(producto.id) }, Modifier.weight(1f))
            BotonSecundario("Archivar", { alArchivar(true) }, Modifier.weight(1f))
        }
    }
    MensajeError(error)
    BotonSecundario("Escanear otro", acciones.volverAEscanear)
}
