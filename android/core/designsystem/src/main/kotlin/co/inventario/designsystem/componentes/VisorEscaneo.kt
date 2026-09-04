package co.inventario.designsystem.componentes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.inventario.designsystem.tema.Colores
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos

private val ScrimEscaneo = Color(0x9E090E0C)
private const val ANCHO_VENTANA = 0.78f
private const val PROPORCION_VENTANA = 0.56f
private const val ALTURA_VENTANA = 0.30f

/**
 * El visor del escáner. La vista de cámara era un `PreviewView` desnudo: sin retícula, sin
 * penumbra alrededor y sin ninguna señal de que la app estuviera mirando. Aquí la ventana de
 * lectura se ve, el resto se atenúa y la linterna deja de ser uno de cuatro botones iguales al
 * pie para pasar a estar sobre la imagen, que es donde hace falta en la bodega.
 */
@Composable
fun VisorEscaneo(
    linternaEncendida: Boolean,
    alAlternarLinterna: (() -> Unit)?,
    modifier: Modifier = Modifier,
    ayuda: String = "Apunta al código de barras",
    detalle: String = "Se lee solo. No hace falta tocar nada.",
    aviso: @Composable (BoxScope.() -> Unit)? = null,
    vistaPrevia: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // El texto de ayuda se coloca a partir de las mismas constantes que dibujan la retícula;
        // si se calcula «a ojo» acaba cayendo dentro de la ventana de lectura y tapando el código.
        val altoTotal = maxHeight
        val anchoVentana = maxWidth * ANCHO_VENTANA
        val altoVentana = anchoVentana * PROPORCION_VENTANA
        val bajoLaVentana = altoTotal * ALTURA_VENTANA + altoVentana + Dimensiones.espacioAmplio

        vistaPrevia()

        Canvas(Modifier.fillMaxSize()) {
            val ancho = size.width * ANCHO_VENTANA
            val alto = ancho * PROPORCION_VENTANA
            val izquierda = (size.width - ancho) / 2f
            val arriba = size.height * ALTURA_VENTANA
            val radio = CornerRadius(12.dp.toPx(), 12.dp.toPx())

            // Penumbra alrededor de la ventana, en cuatro piezas.
            drawRect(ScrimEscaneo, Offset.Zero, Size(size.width, arriba))
            drawRect(ScrimEscaneo, Offset(0f, arriba + alto), Size(size.width, size.height - arriba - alto))
            drawRect(ScrimEscaneo, Offset(0f, arriba), Size(izquierda, alto))
            drawRect(
                ScrimEscaneo,
                Offset(izquierda + ancho, arriba),
                Size(size.width - izquierda - ancho, alto),
            )

            // Esquinas de la retícula: marcan dónde mirar sin tapar el código.
            val brazo = alto * 0.28f
            val grosor = 4.dp.toPx()
            val esquinas = listOf(
                Offset(izquierda, arriba) to listOf(Offset(brazo, 0f), Offset(0f, brazo)),
                Offset(izquierda + ancho, arriba) to listOf(Offset(-brazo, 0f), Offset(0f, brazo)),
                Offset(izquierda, arriba + alto) to listOf(Offset(brazo, 0f), Offset(0f, -brazo)),
                Offset(izquierda + ancho, arriba + alto) to listOf(Offset(-brazo, 0f), Offset(0f, -brazo)),
            )
            for ((vertice, brazos) in esquinas) {
                for (delta in brazos) {
                    drawLine(
                        color = Colores.primarioInverso,
                        start = vertice,
                        end = Offset(vertice.x + delta.x, vertice.y + delta.y),
                        strokeWidth = grosor,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
            drawRoundRect(
                color = Colores.primarioInverso.copy(alpha = 0.28f),
                topLeft = Offset(izquierda, arriba),
                size = Size(ancho, alto),
                cornerRadius = radio,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = Dimensiones.espacioSeccion, start = Dimensiones.espacioAmplio, end = Dimensiones.espacioAmplio),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
        ) {
            aviso?.let { Box(Modifier.fillMaxWidth(), content = it) }
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = bajoLaVentana, start = Dimensiones.espacioAmplio, end = Dimensiones.espacioAmplio),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
        ) {
            Text(
                ayuda,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                detalle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
            )
        }

        if (alAlternarLinterna != null) {
            FilledIconButton(
                onClick = alAlternarLinterna,
                shape = RoundedCornerShape(Dimensiones.radioPildora),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (linternaEncendida) Colores.primarioInverso else Color.White.copy(alpha = 0.18f),
                    contentColor = if (linternaEncendida) Colores.sobrePrimarioContenedor else Color.White,
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(Dimensiones.espacio)
                    .size(Dimensiones.alturaBotonPrincipal),
            ) {
                Icon(
                    if (linternaEncendida) Iconos.linterna else Iconos.linternaApagada,
                    contentDescription = if (linternaEncendida) "Apagar la linterna" else "Encender la linterna",
                    modifier = Modifier.size(Dimensiones.icono),
                )
            }
        }
    }
}

/** Aviso flotante sobre la cámara: sin conexión, permiso, lo que haga falta decir sin tapar. */
@Composable
fun AvisoSobreCamara(texto: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(ScrimEscaneo, RoundedCornerShape(Dimensiones.radioPildora))
            .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioCompacto),
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Iconos.sinConexion,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(Dimensiones.iconoPequeno),
        )
        Text(texto, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}
