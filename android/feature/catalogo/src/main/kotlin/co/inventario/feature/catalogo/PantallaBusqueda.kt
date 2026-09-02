package co.inventario.feature.catalogo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.data.repositorio.FiltrosProductos
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.domain.modelo.Producto

/** T-083: búsqueda por texto (RF-CAT-007) y listado con filtros (RF-CAT-014). */
@Composable
fun PantallaBusqueda(
    alAbrirFicha: (String) -> Unit,
    alCrearProducto: () -> Unit,
    alVolver: () -> Unit,
    vm: BusquedaViewModel = hiltViewModel(),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Dimensiones.espacio), verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
        CampoTexto(estado.texto, vm::cambiarTexto, "Buscar por nombre, SKU o categoría")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
            ChipFiltro("Bajo mínimo", estado.filtros.condicionStock == "bajo_minimo") { vm.cambiarFiltros(estado.filtros.copy(condicionStock = it("bajo_minimo"))) }
            ChipFiltro("Agotados", estado.filtros.condicionStock == "agotado") { vm.cambiarFiltros(estado.filtros.copy(condicionStock = it("agotado"))) }
            ChipFiltro("Archivados", estado.filtros.estado == "archivado") { vm.cambiarFiltros(estado.filtros.copy(estado = it("archivado"))) }
            estado.categorias.forEach { c ->
                ChipFiltro(c.nombre, estado.filtros.categoriaId == c.id) { vm.cambiarFiltros(estado.filtros.copy(categoriaId = it(c.id))) }
            }
        }
        MensajeError(estado.error)
        if (estado.error != null) BotonSecundario("Reintentar", vm::reintentar)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(estado.resultados, key = { _, p -> p.id }) { indice, producto ->
                if (indice >= estado.resultados.size - 5) LaunchedEffect(indice) { vm.cargarMas() }
                FilaProducto(producto) { alAbrirFicha(producto.id) }
                HorizontalDivider()
            }
            if (!estado.cargando && estado.resultados.isEmpty()) {
                item { Text("No hay productos con esa búsqueda.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Dimensiones.espacio)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto), modifier = Modifier.padding(bottom = Dimensiones.espacio)) {
            BotonSecundario("Nuevo producto", alCrearProducto, Modifier.weight(1f))
            BotonSecundario("Escanear", alVolver, Modifier.weight(1f))
        }
    }
}

/** El lambda recibe una función que devuelve el valor si se activa y null si se desactiva. */
@Composable
private fun ChipFiltro(texto: String, activo: Boolean, alCambiar: ((String) -> String?) -> Unit) {
    FilterChip(selected = activo, onClick = { alCambiar { valor -> if (activo) null else valor } }, label = { Text(texto) })
}

@Composable
private fun FilaProducto(producto: Producto, alPulsar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimensiones.areaTactilMinima).clickable(onClick = alPulsar).padding(vertical = Dimensiones.espacioCompacto),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(producto.nombre, style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(producto.sku, producto.categoria?.nombre).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            producto.stockActual.aApi().trimEnd('0').trimEnd('.'),
            style = MaterialTheme.typography.titleMedium,
            color = if (producto.bajoMinimo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}
