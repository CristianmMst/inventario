package co.inventario.feature.catalogo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.EsqueletoLista
import co.inventario.designsystem.componentes.EstadoError
import co.inventario.designsystem.componentes.EstadoStock
import co.inventario.designsystem.componentes.FilaDato
import co.inventario.designsystem.componentes.Formato
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.componentes.PildoraEstado
import co.inventario.designsystem.componentes.TarjetaDatos
import co.inventario.designsystem.componentes.TarjetaStock
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Estado
import co.inventario.designsystem.tema.Iconos
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

/**
 * T-081: ficha con el stock actual destacado (RF-CAT-008, RF-INV-003).
 *
 * Antes esto eran ocho botones idénticos apilados y había que desplazar la pantalla para llegar
 * al principal. Ahora «Registrar salida» vive anclado al pie —los dos toques medidos se
 * conservan—, entrada y merma quedan a la vista, y lo que se usa de vez en cuando (conteo,
 * historial, editar, archivar) se recoge en el menú de la barra.
 */
@Composable
fun PantallaFicha(
    productoId: String,
    acciones: AccionesFicha,
    alVolver: () -> Unit,
    vm: FichaProductoViewModel = hiltViewModel<FichaProductoViewModel, FichaProductoViewModel.Fabrica>(
        creationCallback = { it.crear(productoId) },
    ),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    // Al volver de registrar un movimiento la pantalla se recompone: el stock se pide de nuevo (RF-INV-003).
    LaunchedEffect(Unit) { vm.recargar() }

    val producto = estado.producto

    PantallaInventario(
        titulo = "Producto",
        alVolver = alVolver,
        accionesBarra = {
            if (producto != null && !producto.estaArchivado) {
                MenuFicha(producto, acciones, alArchivar = { vm.archivar(true) })
            }
        },
        acciones = if (producto != null && !producto.estaArchivado) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                    BotonSecundario(
                        "Entrada",
                        { acciones.registrarEntrada(producto.id) },
                        Modifier.weight(1f),
                        icono = Iconos.entrada,
                    )
                    BotonSecundario(
                        "Merma",
                        { acciones.registrarMerma(producto.id) },
                        Modifier.weight(1f),
                        icono = Iconos.merma,
                        color = Estado.bajoMinimo,
                    )
                }
                BotonPrincipal("Registrar salida", { acciones.registrarSalida(producto.id) }, icono = Iconos.salida)
            }
        } else {
            null
        },
    ) { relleno ->
        val error = estado.error
        when {
            estado.cargando && producto == null -> EsqueletoLista(Modifier.fillMaxSize().padding(relleno), filas = 4)
            producto == null -> EstadoError(
                texto = error ?: "No se pudo cargar el producto.",
                alReintentar = vm::recargar,
                modifier = Modifier.fillMaxSize().padding(relleno),
            )
            else -> ContenidoFicha(
                producto = producto,
                error = error,
                alDesarchivar = { vm.archivar(false) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun MenuFicha(producto: Producto, acciones: AccionesFicha, alArchivar: () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    IconButton(onClick = { abierto = true }) {
        Icon(Iconos.masOpciones, contentDescription = "Más acciones", modifier = Modifier.size(Dimensiones.icono))
    }
    DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
        OpcionMenu("Conteo físico", Iconos.conteo) { abierto = false; acciones.contar(producto.id) }
        OpcionMenu("Historial", Iconos.historial) { abierto = false; acciones.verHistorial(producto.id) }
        OpcionMenu("Editar", Iconos.editar) { abierto = false; acciones.editar(producto.id) }
        OpcionMenu("Archivar", Iconos.archivar) { abierto = false; alArchivar() }
    }
}

@Composable
private fun OpcionMenu(texto: String, icono: ImageVector, alPulsar: () -> Unit) {
    DropdownMenuItem(
        text = { Text(texto, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icono, contentDescription = null, modifier = Modifier.size(Dimensiones.icono)) },
        onClick = alPulsar,
    )
}

@Composable
private fun ContenidoFicha(
    producto: Producto,
    error: String?,
    alDesarchivar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioAmplio),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
        ) {
            if (producto.imagenUrl != null) {
                AsyncImage(
                    model = producto.imagenUrl,
                    contentDescription = "Foto de ${producto.nombre}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(Dimensiones.miniaturaGrande)
                        .clip(RoundedCornerShape(Dimensiones.radio)),
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
            ) {
                Text(producto.nombre, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(producto.sku, producto.categoria?.nombre).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (producto.codigosBarras.isEmpty()) {
                        "Sin código de barras"
                    } else {
                        producto.codigosBarras.joinToString(", ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        TarjetaStock(
            cantidad = producto.stockActual.aTexto(),
            unidad = producto.unidad.nombre,
            estado = producto.aEstadoStock(),
            minimo = producto.stockMinimo?.aTexto(),
            proporcionDelMinimo = producto.proporcionDelMinimo(),
        )

        TarjetaDatos {
            producto.precioVenta?.let {
                FilaDato("Precio de venta", Formato.monto(it.aApi().monto, it.moneda.toString()))
            }
            producto.costoActual?.let {
                FilaDato("Costo actual", Formato.monto(it.aApi().monto, it.moneda.toString()))
            }
            FilaDato("Unidad de medida", producto.unidad.nombre, ultima = true)
        }

        if (producto.estaArchivado) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio)) {
                PildoraEstado(EstadoStock.ARCHIVADO, texto = "Archivado: no admite movimientos")
                BotonSecundario("Desarchivar", alDesarchivar, icono = Iconos.desarchivar)
            }
        }

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(Dimensiones.espacioCompacto))
    }
}

/**
 * Dónde está el stock entre cero y el doble del mínimo, para que la barra diga «vas justo» sin
 * tener que leer dos números.
 *
 * Devuelve `null` —y entonces no se dibuja barra— cuando el stock está muy por encima del
 * mínimo: una barra clavada al 100 % no informa de nada, y la píldora «En rango» ya lo dice.
 */
private fun Producto.proporcionDelMinimo(): Float? {
    val minimo = stockMinimo?.valor?.toFloat()?.takeIf { it > 0f } ?: return null
    val actual = stockActual.valor.toFloat()
    if (actual > minimo * 2f) return null
    return (actual / (minimo * 2f)).coerceIn(0f, 1f)
}
