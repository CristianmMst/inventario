package co.inventario.designsystem.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Estado
import co.inventario.designsystem.tema.Iconos

/**
 * Cuántas escrituras esperan en la bandeja de salida. Lo publica el armazón de navegación una
 * sola vez y lo lee [PantallaInventario] en todas partes: así el aviso sale en cualquier
 * pantalla sin enhebrar un parámetro por dieciocho firmas.
 */
val LocalPendientesDeEnvio = compositionLocalOf { 0 }

/**
 * El armazón de toda pantalla de la app. Antes cada una era un `Column` pelado y «volver» era
 * un botón perdido al pie; aquí el título, la vuelta atrás y los avisos tienen un sitio fijo.
 *
 * `acciones` es la clave de RNF-08: lo que se pone ahí queda **anclado abajo**, al alcance del
 * pulgar, aunque el contenido crezca y haya que desplazarlo. Antes los botones quedaban en el
 * tercio inferior solo mientras el contenido no desbordase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInventario(
    titulo: String,
    modifier: Modifier = Modifier,
    alVolver: (() -> Unit)? = null,
    accionesBarra: @Composable RowScope.() -> Unit = {},
    barraInferior: @Composable () -> Unit = {},
    acciones: (@Composable ColumnScope.() -> Unit)? = null,
    aviso: @Composable () -> Unit = { BannerConexion(LocalPendientesDeEnvio.current) },
    contenido: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            titulo,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    navigationIcon = {
                        if (alVolver != null) {
                            IconButton(onClick = alVolver) {
                                Icon(
                                    Iconos.atras,
                                    contentDescription = "Volver",
                                    modifier = Modifier.size(Dimensiones.icono),
                                )
                            }
                        }
                    },
                    actions = { accionesBarra() },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
                aviso()
            }
        },
        bottomBar = {
            Column {
                if (acciones != null) BarraAccionInferior(acciones)
                barraInferior()
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = contenido,
    )
}

/**
 * Las acciones de la pantalla, ancladas al pie sobre una superficie elevada. Los `Row` de dos
 * botones parten el ancho en mitades para que el pulgar no tenga que cruzar la pantalla.
 */
@Composable
fun BarraAccionInferior(contenido: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = Dimensiones.elevacionNivel2,
        shadowElevation = Dimensiones.elevacionNivel2,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = Dimensiones.espacio,
                        end = Dimensiones.espacio,
                        top = Dimensiones.espacioMedio,
                        bottom = Dimensiones.espacio,
                    ),
                verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                content = contenido,
            )
        }
    }
}

/** Un destino de la barra inferior. El icono relleno marca el activo. */
data class Destino(
    val etiqueta: String,
    val icono: ImageVector,
    val iconoActivo: ImageVector,
    val insignia: Int? = null,
)

/**
 * Los cuatro destinos de primer nivel. El escaneo sigue siendo el de arranque (HU-03, «la
 * cámara es la puerta de todo»), pero deja de ser el único camino: antes todo colgaba de él y
 * de un menú con siete botones idénticos.
 */
@Composable
fun BarraNavegacion(
    destinos: List<Destino>,
    indiceActivo: Int,
    alElegir: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Dimensiones.elevacionNivel0,
    ) {
        destinos.forEachIndexed { indice, destino ->
            val activo = indice == indiceActivo
            NavigationBarItem(
                selected = activo,
                onClick = { alElegir(indice) },
                icon = {
                    if (destino.insignia != null && destino.insignia > 0) {
                        InsigniaSobre(destino.insignia) {
                            Icon(
                                if (activo) destino.iconoActivo else destino.icono,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensiones.icono),
                            )
                        }
                    } else {
                        Icon(
                            if (activo) destino.iconoActivo else destino.icono,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensiones.icono),
                        )
                    }
                },
                label = { Text(destino.etiqueta, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun InsigniaSobre(cuenta: Int, contenido: @Composable () -> Unit) {
    androidx.compose.material3.BadgedBox(
        badge = {
            androidx.compose.material3.Badge(
                containerColor = Estado.bajoMinimo,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text("$cuenta", style = MaterialTheme.typography.labelMedium)
            }
        },
        content = { contenido() },
    )
}

/**
 * La bandeja de salida reintenta en silencio; sin esto, la única señal de que algo no ha salido
 * era un botón dentro de la pantalla de movimiento. Marta necesita verlo siempre: su miedo es
 * justamente que el número no cuadre con el estante.
 */
@Composable
fun BannerConexion(pendientes: Int, modifier: Modifier = Modifier) {
    if (pendientes <= 0) return
    val texto = if (pendientes == 1) {
        "Sin conexión · 1 movimiento por enviar"
    } else {
        "Sin conexión · $pendientes movimientos por enviar"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Estado.bajoMinimoContenedor)
            .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Iconos.sinConexion,
            contentDescription = null,
            tint = Estado.sobreBajoMinimoContenedor,
            modifier = Modifier.size(Dimensiones.icono),
        )
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = Estado.sobreBajoMinimoContenedor,
        )
    }
}
