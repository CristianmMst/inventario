package co.inventario.feature.reportes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.ChipFiltro
import co.inventario.designsystem.componentes.EsqueletoLista
import co.inventario.designsystem.componentes.EstadoError
import co.inventario.designsystem.componentes.EstadoVacio
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Estado
import co.inventario.designsystem.tema.Iconos
import co.inventario.designsystem.tema.Tipografia

/**
 * Lo que hay que reponer, ordenado por lo que más falta. Cada fila lleva su propia entrada
 * directa: desde ver el hueco en el estante hasta registrar la llegada hay un toque.
 */
@Composable
fun PantallaReponer(
    alAbrirProducto: (String) -> Unit,
    alRegistrarEntrada: (String) -> Unit,
    barraInferior: @Composable () -> Unit = {},
    vm: ReponerViewModel = hiltViewModel(),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()

    PantallaInventario(titulo = "Reponer", barraInferior = barraInferior) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            Text(
                "Ordenado por lo que más falta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimensiones.espacio),
            )
            Row(
                Modifier.padding(Dimensiones.espacio),
                horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
            ) {
                // Sin número al lado: el reporte viene paginado, así que `size` es el tamaño de
                // la página, no cuántos hay. Un «(100)» que en realidad significa «al menos 100»
                // es peor que no decir nada.
                ChipFiltro(
                    texto = "Bajo mínimo",
                    activo = estado.lista == ListaReposicion.BAJO_MINIMO,
                    alPulsar = { vm.cambiarLista(ListaReposicion.BAJO_MINIMO) },
                )
                ChipFiltro(
                    texto = "Agotados",
                    activo = estado.lista == ListaReposicion.AGOTADOS,
                    alPulsar = { vm.cambiarLista(ListaReposicion.AGOTADOS) },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val error = estado.error
            when {
                estado.cargando -> EsqueletoLista(Modifier.weight(1f))
                error != null -> EstadoError(error, vm::recargar, Modifier.weight(1f))
                estado.filas.isEmpty() -> EstadoVacio(
                    titulo = if (estado.lista == ListaReposicion.BAJO_MINIMO) "Nada bajo mínimo" else "Nada agotado",
                    explicacion = "Todo el catálogo está por encima de su mínimo. Nada que pedir hoy.",
                    modifier = Modifier.weight(1f),
                )
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(estado.filas, key = { it.productoId }) { fila ->
                        FilaReponer(
                            fila = fila,
                            alPulsar = { alAbrirProducto(fila.productoId) },
                            alRegistrarEntrada = { alRegistrarEntrada(fila.productoId) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaReponer(
    fila: FilaReposicion,
    alPulsar: () -> Unit,
    alRegistrarEntrada: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimensiones.alturaFilaDestacada)
            .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = alPulsar),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
        ) {
            Text(fila.nombre, style = MaterialTheme.typography.titleMedium)
            Text(
                fila.faltan,
                style = MaterialTheme.typography.labelMedium,
                color = if (fila.agotado) Estado.agotado else Estado.bajoMinimo,
            )
            Text(
                fila.detalle,
                style = Tipografia.numeroCuerpo,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledIconButton(
            onClick = alRegistrarEntrada,
            shape = MaterialTheme.shapes.large,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.size(Dimensiones.alturaBotonPrincipal),
        ) {
            Icon(
                Iconos.entrada,
                contentDescription = "Registrar entrada de ${fila.nombre}",
                modifier = Modifier.size(Dimensiones.icono),
            )
        }
    }
}
