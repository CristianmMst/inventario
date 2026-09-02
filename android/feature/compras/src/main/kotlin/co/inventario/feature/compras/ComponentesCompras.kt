package co.inventario.feature.compras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.CampoCantidad
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.domain.modelo.ProductoBreve
import co.inventario.domain.modelo.Proveedor

/** Chips de proveedor; el activo se resalta. Reutilizado por órdenes, recepciones y facturas. */
@Composable
fun SelectorProveedor(proveedores: List<Proveedor>, elegido: String?, alElegir: (String) -> Unit, error: String? = null) {
    Text("Proveedor", style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        proveedores.forEach { p -> FilterChip(selected = p.id == elegido, onClick = { alElegir(p.id) }, label = { Text(p.nombre) }) }
    }
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

/** Buscador incrustado: escribe, elige, y la línea se añade. */
@Composable
fun SelectorProducto(alElegir: (ProductoBreve) -> Unit, vm: BuscadorProductosViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    CampoTexto(estado.texto, vm::cambiarTexto, "Agregar producto (nombre o SKU)")
    estado.resultados.take(6).forEach { p ->
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimensiones.areaTactilMinima).clickable { alElegir(p.breve()); vm.limpiar() }.padding(vertical = Dimensiones.espacioCompacto),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(p.nombre, style = MaterialTheme.typography.bodyLarge)
            Text("stock ${p.stockActual.valor.stripTrailingZeros().toPlainString()}", style = MaterialTheme.typography.bodyLarge)
        }
        HorizontalDivider()
    }
}

/** Una línea editable (producto, cantidad, costo) con su botón de quitar. */
@Composable
fun LineaEditable(
    producto: ProductoBreve,
    cantidad: String,
    costo: String,
    etiquetaCosto: String,
    pendiente: String?,
    errorCantidad: String?,
    errorCosto: String?,
    alCambiarCantidad: (String) -> Unit,
    alCambiarCosto: (String) -> Unit,
    alQuitar: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = Dimensiones.espacioCompacto), verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(producto.nombre + (pendiente?.let { " · pendiente $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = alQuitar) { Text("Quitar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            CampoCantidad(cantidad, alCambiarCantidad, "Cantidad", Modifier.weight(1f), error = errorCantidad)
            CampoCantidad(costo, alCambiarCosto, etiquetaCosto, Modifier.weight(1f), error = errorCosto)
        }
        HorizontalDivider()
    }
}
