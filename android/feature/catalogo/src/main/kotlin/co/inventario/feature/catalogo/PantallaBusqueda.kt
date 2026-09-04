package co.inventario.feature.catalogo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.data.repositorio.FiltrosProductos
import co.inventario.designsystem.componentes.ChipFiltro
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.EsqueletoLista
import co.inventario.designsystem.componentes.EstadoError
import co.inventario.designsystem.componentes.EstadoStock
import co.inventario.designsystem.componentes.EstadoVacio
import co.inventario.designsystem.componentes.FilaProducto
import co.inventario.designsystem.componentes.Formato
import co.inventario.designsystem.componentes.Miniatura
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos
import co.inventario.domain.modelo.Producto
import coil3.compose.AsyncImage

/** T-083: búsqueda por texto (RF-CAT-007) y listado con filtros (RF-CAT-014). */
@Composable
fun PantallaBusqueda(
    alAbrirFicha: (String) -> Unit,
    alCrearProducto: () -> Unit,
    barraInferior: @Composable () -> Unit = {},
    vm: BusquedaViewModel = hiltViewModel(),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()

    PantallaInventario(titulo = "Catálogo", barraInferior = barraInferior) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            Column(Modifier.fillMaxSize()) {
                CampoTexto(
                    valor = estado.texto,
                    alCambiar = vm::cambiarTexto,
                    etiqueta = "Buscar por nombre, SKU o categoría",
                    modifier = Modifier.padding(
                        start = Dimensiones.espacio,
                        end = Dimensiones.espacio,
                        bottom = Dimensiones.espacioMedio,
                    ),
                )

                // Los filtros se agrupan por significado: primero la condición del stock, que es
                // lo que se mira a diario, y después las categorías.
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Dimensiones.espacio),
                    horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                ) {
                    ChipFiltro(
                        texto = "Bajo mínimo",
                        activo = estado.filtros.condicionStock == "bajo_minimo",
                        alPulsar = { vm.cambiarFiltros(estado.filtros.alternarStock("bajo_minimo")) },
                    )
                    ChipFiltro(
                        texto = "Agotados",
                        activo = estado.filtros.condicionStock == "agotado",
                        alPulsar = { vm.cambiarFiltros(estado.filtros.alternarStock("agotado")) },
                    )
                    ChipFiltro(
                        texto = "Archivados",
                        activo = estado.filtros.estado == "archivado",
                        alPulsar = {
                            vm.cambiarFiltros(
                                estado.filtros.copy(
                                    estado = if (estado.filtros.estado == "archivado") null else "archivado",
                                ),
                            )
                        },
                    )
                    estado.categorias.forEach { categoria ->
                        ChipFiltro(
                            texto = categoria.nombre,
                            activo = estado.filtros.categoriaId == categoria.id,
                            alPulsar = {
                                vm.cambiarFiltros(
                                    estado.filtros.copy(
                                        categoriaId = if (estado.filtros.categoriaId == categoria.id) {
                                            null
                                        } else {
                                            categoria.id
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = Dimensiones.espacioMedio),
                )

                val error = estado.error
                when {
                    error != null && estado.resultados.isEmpty() ->
                        EstadoError(error, vm::reintentar, Modifier.weight(1f))

                    estado.cargando && estado.resultados.isEmpty() ->
                        EsqueletoLista(Modifier.weight(1f))

                    estado.resultados.isEmpty() -> EstadoVacio(
                        titulo = if (estado.texto.isBlank()) "El catálogo está vacío" else "Sin resultados",
                        explicacion = if (estado.texto.isBlank()) {
                            "Todavía no hay productos. Crea el primero o escanea un código para darlo de alta."
                        } else {
                            "No hay productos que coincidan con «${estado.texto}». Prueba con menos palabras."
                        },
                        icono = Iconos.catalogo,
                        textoAccion = "Crear un producto",
                        alAccionar = alCrearProducto,
                        modifier = Modifier.weight(1f),
                    )

                    // El hueco de abajo evita que el botón flotante tape la última fila.
                    else -> LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = Dimensiones.alturaBarraNavegacion),
                    ) {
                        itemsIndexed(estado.resultados, key = { _, p -> p.id }) { indice, producto ->
                            if (indice >= estado.resultados.size - 5) {
                                LaunchedEffect(indice) { vm.cargarMas() }
                            }
                            FilaProducto(
                                nombre = producto.nombre,
                                detalle = listOfNotNull(producto.sku, producto.categoria?.nombre).joinToString(" · "),
                                cantidad = producto.stockActual.aTexto(),
                                unidad = producto.unidad.nombre,
                                estado = producto.aEstadoStock(),
                                alPulsar = { alAbrirFicha(producto.id) },
                                imagenUrl = producto.imagenUrl,
                                contenidoMiniatura = { url -> MiniaturaProducto(url, producto.nombre) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (estado.cargando) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(Dimensiones.espacio), Alignment.Center) {
                                    Text(
                                        "Cargando más…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = alCrearProducto,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Dimensiones.espacio),
                icon = { Icon(Iconos.anadir, contentDescription = null, modifier = Modifier.size(Dimensiones.icono)) },
                text = { Text("Nuevo", style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

/** La foto del producto, con hueco mientras carga y si falla; antes no tenía ninguno. */
@Composable
internal fun MiniaturaProducto(url: String?, nombre: String) {
    if (url == null) {
        Miniatura(null)
    } else {
        AsyncImage(
            model = url,
            contentDescription = "Foto de $nombre",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(Dimensiones.miniatura)
                .clip(RoundedCornerShape(Dimensiones.radioPequeno)),
        )
    }
}

/** Las tres condiciones de stock son excluyentes entre sí: elegir una apaga la otra. */
private fun FiltrosProductos.alternarStock(valor: String) =
    copy(condicionStock = if (condicionStock == valor) null else valor)

internal fun Producto.aEstadoStock(): EstadoStock = EstadoStock.de(
    agotado = stockActual.valor.signum() <= 0,
    bajoMinimo = bajoMinimo,
    archivado = estaArchivado,
)

internal fun co.inventario.domain.modelo.Cantidad.aTexto(): String = Formato.cantidad(valor)
