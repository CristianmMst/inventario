package co.inventario.designsystem.componentes

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Estado
import co.inventario.designsystem.tema.Iconos
import co.inventario.designsystem.tema.Tipografia

/**
 * Cómo está un producto respecto de su mínimo. Es la única pregunta que Marta le hace a la app
 * («¿me puedo fiar de este número?»), así que tiene color propio y un solo sitio donde se
 * decide cómo se ve.
 */
enum class EstadoStock(val etiqueta: String) {
    EN_RANGO("En rango"),
    BAJO_MINIMO("Bajo mínimo"),
    AGOTADO("Agotado"),
    ARCHIVADO("Archivado"),
    ;

    val color: Color
        get() = when (this) {
            EN_RANGO -> Estado.enRango
            BAJO_MINIMO -> Estado.bajoMinimo
            AGOTADO -> Estado.agotado
            ARCHIVADO -> Estado.neutro
        }

    val contenedor: Color
        get() = when (this) {
            EN_RANGO -> Estado.enRangoContenedor
            BAJO_MINIMO -> Estado.bajoMinimoContenedor
            AGOTADO -> Estado.agotadoContenedor
            ARCHIVADO -> Estado.neutroContenedor
        }

    val sobreContenedor: Color
        get() = when (this) {
            EN_RANGO -> Estado.sobreEnRangoContenedor
            BAJO_MINIMO -> Estado.sobreBajoMinimoContenedor
            AGOTADO -> Estado.sobreAgotadoContenedor
            ARCHIVADO -> Estado.neutro
        }

    companion object {
        /** La regla que ya vive en el dominio, traducida a color una sola vez. */
        fun de(agotado: Boolean, bajoMinimo: Boolean, archivado: Boolean = false): EstadoStock = when {
            archivado -> ARCHIVADO
            agotado -> AGOTADO
            bajoMinimo -> BAJO_MINIMO
            else -> EN_RANGO
        }
    }
}

/** El estado, dicho en una palabra y con su color. */
@Composable
fun PildoraEstado(
    estado: EstadoStock,
    modifier: Modifier = Modifier,
    texto: String = estado.etiqueta,
) {
    Row(
        modifier = modifier
            .background(estado.contenedor, RoundedCornerShape(Dimensiones.radioPildora))
            .padding(horizontal = Dimensiones.espacioMedio, vertical = Dimensiones.espacioCompacto),
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Dimensiones.espacioMedio)
                .background(estado.color, RoundedCornerShape(Dimensiones.radioPildora)),
        )
        Text(texto, style = MaterialTheme.typography.labelMedium, color = estado.sobreContenedor)
    }
}

/**
 * Píldora para estados que no son de stock (una orden emitida, una factura pagada): el mismo
 * lenguaje visual, sin forzarlos dentro de [EstadoStock], que significa otra cosa.
 */
@Composable
fun PildoraEstadoOrden(
    texto: String,
    modifier: Modifier = Modifier,
    contenedor: Color = Estado.neutroContenedor,
    sobreContenedor: Color = Estado.neutro,
) {
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium,
        color = sobreContenedor,
        modifier = modifier
            .background(contenedor, RoundedCornerShape(Dimensiones.radioPildora))
            .padding(horizontal = Dimensiones.espacioMedio, vertical = Dimensiones.espacioCompacto),
    )
}

/**
 * El héroe de la ficha y del movimiento: la cifra que se lee de pie, con su estado y su
 * distancia al mínimo. Responde de un vistazo si hay que reponer.
 */
@Composable
fun TarjetaStock(
    cantidad: String,
    unidad: String,
    estado: EstadoStock,
    modifier: Modifier = Modifier,
    minimo: String? = null,
    proporcionDelMinimo: Float? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = androidx.compose.foundation.BorderStroke(
            Dimensiones.grosorBorde,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            Modifier.padding(Dimensiones.espacioAmplio),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                StockDestacado(cantidad, unidad, color = estado.color)
                PildoraEstado(estado)
            }
            if (proporcionDelMinimo != null && minimo != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto)) {
                    LinearProgressIndicator(
                        progress = { proporcionDelMinimo.coerceIn(0f, 1f) },
                        color = estado.color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimensiones.grosorBarraProgreso)
                            .clearAndSetSemantics { },
                    )
                    Text(
                        "Mínimo $minimo $unidad",
                        style = Tipografia.numeroCuerpo,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * La fila de producto de toda la app. Antes cada pantalla tenía su copia privada: cinco
 * `Fila*` distintas que se fueron separando entre sí.
 */
@Composable
fun FilaProducto(
    nombre: String,
    detalle: String,
    cantidad: String,
    unidad: String,
    estado: EstadoStock,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    imagenUrl: String? = null,
    contenidoMiniatura: @Composable (String?) -> Unit = { Miniatura(it) },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimensiones.alturaFilaLista)
            .clickable(onClick = alPulsar)
            .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        contenidoMiniatura(imagenUrl)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
        ) {
            Text(
                nombre,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detalle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
        ) {
            Text(cantidad, style = Tipografia.numeroFila, color = estado.color)
            Text(
                if (estado == EstadoStock.EN_RANGO) unidad else estado.etiqueta.lowercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (estado == EstadoStock.EN_RANGO) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    estado.color
                },
            )
        }
    }
}

/** Marcador de imagen: hueco con icono mientras no hay foto, y sitio para la real. */
@Composable
fun Miniatura(imagenUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Dimensiones.miniatura)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(Dimensiones.radioPequeno),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (imagenUrl == null) {
            Icon(
                Iconos.imagen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(Dimensiones.iconoPequeno),
            )
        }
    }
}

/** Dirección de un movimiento, con el color e icono que le tocan. */
enum class SentidoMovimiento(val icono: ImageVector, val color: Color) {
    ENTRADA(Iconos.entrada, Estado.enRango),
    SALIDA(Iconos.salida, Color(0xFF3F6375)),
    MERMA(Iconos.merma, Estado.bajoMinimo),
    AJUSTE(Iconos.conteo, Color(0xFF4B635A)),
    ANULACION(Iconos.anular, Estado.neutro),
}

/** Una línea del historial: qué pasó, cuánto y cómo quedó el stock. */
@Composable
fun FilaMovimiento(
    sentido: SentidoMovimiento,
    titulo: String,
    detalle: String,
    cantidad: String,
    stockResultante: String?,
    modifier: Modifier = Modifier,
    anulado: Boolean = false,
    alPulsar: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimensiones.alturaFilaLista)
            .then(if (alPulsar != null) Modifier.clickable(onClick = alPulsar) else Modifier)
            .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Dimensiones.miniatura)
                .background(
                    if (anulado) Estado.neutroContenedor else sentido.color.copy(alpha = 0.14f),
                    RoundedCornerShape(Dimensiones.radioPequeno),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                sentido.icono,
                contentDescription = null,
                tint = if (anulado) Estado.neutro else sentido.color,
                modifier = Modifier.size(Dimensiones.icono),
            )
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
        ) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(
                detalle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo),
        ) {
            Text(
                cantidad,
                style = Tipografia.numeroFila,
                color = if (anulado) Estado.neutro else sentido.color,
            )
            if (anulado) {
                PildoraEstado(EstadoStock.ARCHIVADO, texto = "Anulado")
            } else if (stockResultante != null) {
                Text(
                    "quedó en $stockResultante",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Tarjeta de datos en filas etiqueta/valor, para las fichas y los detalles. */
@Composable
fun TarjetaDatos(
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScopeDatos.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(
            Dimensiones.grosorBorde,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(horizontal = Dimensiones.espacio)) {
            ColumnScopeDatos.contenido()
        }
    }
}

object ColumnScopeDatos

@Composable
fun ColumnScopeDatos.FilaDato(etiqueta: String, valor: String, ultima: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimensiones.areaTactilMinima)
            .padding(vertical = Dimensiones.espacioMedio),
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(valor, style = Tipografia.numeroCuerpo, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    if (!ultima) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Chip de filtro con los colores del tema; sin esto heredan el morado *baseline* de M3. */
@Composable
fun ChipFiltro(
    texto: String,
    activo: Boolean,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    FilterChip(
        selected = activo,
        onClick = alPulsar,
        enabled = habilitado,
        label = { Text(texto, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = if (activo) {
            {
                Icon(
                    Iconos.confirmar,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensiones.iconoPequeno),
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(Dimensiones.radioPildora),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = habilitado,
            selected = activo,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = modifier.defaultMinSize(minHeight = Dimensiones.areaTactilMinima),
    )
}

/**
 * Vacío con nombre y salida. Antes era una frase suelta en medio de la lista, que no distingue
 * «no hay nada» de «no ha cargado».
 */
@Composable
fun EstadoVacio(
    titulo: String,
    explicacion: String,
    modifier: Modifier = Modifier,
    icono: ImageVector = Iconos.vacio,
    textoAccion: String? = null,
    alAccionar: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensiones.espacioAmplio, vertical = Dimensiones.espacioSeccion),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(Dimensiones.iconoIlustracion),
        )
        Text(titulo, style = MaterialTheme.typography.titleLarge)
        Text(
            explicacion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (textoAccion != null && alAccionar != null) {
            Spacer(Modifier.height(Dimensiones.espacioCompacto))
            BotonSecundario(textoAccion, alAccionar, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Error con salida. Cuatro pantallas mostraban el texto del error sin ofrecer reintentar;
 * este componente no permite esa combinación.
 */
@Composable
fun EstadoError(
    texto: String,
    alReintentar: () -> Unit,
    modifier: Modifier = Modifier,
    textoAccion: String = "Reintentar",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensiones.espacioAmplio, vertical = Dimensiones.espacioSeccion),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
    ) {
        Icon(
            Iconos.error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(Dimensiones.iconoIlustracion),
        )
        Text(
            texto,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(Dimensiones.espacioCompacto))
        BotonSecundario(textoAccion, alReintentar, icono = Iconos.reintentar, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Esqueleto de carga. Sustituye a los tres `CircularProgressIndicator` sueltos: dice cuánto
 * viene y de qué forma, en vez de dejar la pantalla en blanco.
 */
@Composable
fun EsqueletoLista(modifier: Modifier = Modifier, filas: Int = 6) {
    val transicion = rememberInfiniteTransition(label = "esqueleto")
    val brillo by transicion.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "brillo",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Cargando" },
    ) {
        repeat(filas) { indice ->
            val color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                alpha = brillo * (1f - indice * 0.08f).coerceAtLeast(0.4f),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Dimensiones.alturaFilaLista)
                    .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
                horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(Dimensiones.miniatura)
                        .background(color, RoundedCornerShape(Dimensiones.radioPequeno)),
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.66f)
                            .height(Dimensiones.espacio)
                            .background(color, RoundedCornerShape(Dimensiones.radioPildora)),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(Dimensiones.espacioMedio)
                            .background(color, RoundedCornerShape(Dimensiones.radioPildora)),
                    )
                }
                Box(
                    Modifier
                        .width(Dimensiones.espacioSeccion)
                        .height(Dimensiones.espacioAmplio)
                        .background(color, RoundedCornerShape(Dimensiones.radioPildora)),
                )
            }
        }
    }
}

/**
 * Confirmación de lo irreversible. Existe porque borrar un proveedor no la tenía: se borraba
 * al primer toque, sin diálogo ni deshacer.
 */
@Composable
fun DialogoConfirmacion(
    titulo: String,
    texto: String,
    textoConfirmar: String,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
    modifier: Modifier = Modifier,
    destructivo: Boolean = false,
    textoCancelar: String = "Cancelar",
    contenidoExtra: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = alCancelar,
        icon = if (destructivo) {
            {
                Icon(
                    Iconos.error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(Dimensiones.iconoGrande),
                )
            }
        } else {
            null
        },
        title = { Text(titulo, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio)) {
                Text(texto, style = MaterialTheme.typography.bodyLarge)
                contenidoExtra?.invoke()
            }
        },
        confirmButton = {
            TextButton(
                onClick = alConfirmar,
                modifier = Modifier.defaultMinSize(minHeight = Dimensiones.areaTactilMinima),
            ) {
                Text(
                    textoConfirmar,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructivo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = alCancelar,
                modifier = Modifier.defaultMinSize(minHeight = Dimensiones.areaTactilMinima),
            ) {
                Text(textoCancelar, style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
