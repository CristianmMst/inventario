package co.inventario.feature.asistente

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonTexto
import co.inventario.designsystem.componentes.ChipFiltro
import co.inventario.designsystem.componentes.EstadoVacio
import co.inventario.designsystem.componentes.PantallaInventario
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos
import co.inventario.domain.modelo.AutorMensaje
import co.inventario.domain.modelo.MensajeChat

/**
 * Chat con el asistente de inventario (HU-21). Responde con el stock real del negocio: lo que
 * está bajo el mínimo, lo agotado y cualquier producto por nombre o SKU.
 */
@Composable
fun PantallaAsistente(
    alVolver: () -> Unit,
    vm: AsistenteViewModel = hiltViewModel(),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val lista = rememberLazyListState()
    val filas = estado.mensajes.size + (if (estado.enviando) 1 else 0) + (if (estado.error != null) 1 else 0)

    LaunchedEffect(filas) {
        if (filas > 0) lista.animateScrollToItem(filas - 1)
    }

    PantallaInventario(
        titulo = "Asistente",
        alVolver = alVolver,
        modifier = Modifier.imePadding(),
        acciones = {
            Compositor(
                borrador = estado.borrador,
                puedeEnviar = estado.puedeEnviar,
                alCambiar = vm::cambiarBorrador,
                alEnviar = { vm.enviar() },
            )
        },
    ) { relleno ->
        if (estado.mensajes.isEmpty()) {
            Bienvenida(alElegir = vm::enviar, modifier = Modifier.fillMaxSize().padding(relleno))
            return@PantallaInventario
        }
        LazyColumn(
            state = lista,
            modifier = Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(Dimensiones.espacio),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
        ) {
            itemsIndexed(estado.mensajes) { _, mensaje -> Burbuja(mensaje) }
            if (estado.enviando) {
                item { Escribiendo() }
            }
            estado.error?.let { error ->
                item { ErrorEnvio(error, alReintentar = vm::reintentar) }
            }
        }
    }
}

@Composable
private fun Bienvenida(alElegir: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        EstadoVacio(
            titulo = "Pregúntale a tu inventario",
            explicacion = "El asistente consulta el stock real de tu negocio: qué falta, qué se acabó y cuánto queda de cada producto.",
            icono = Iconos.asistente,
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Dimensiones.espacioAmplio),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsistenteViewModel.SUGERENCIAS.forEach { sugerencia ->
                ChipFiltro(texto = sugerencia, activo = false, alPulsar = { alElegir(sugerencia) })
            }
        }
    }
}

@Composable
private fun Burbuja(mensaje: MensajeChat) {
    val delUsuario = mensaje.autor == AutorMensaje.USUARIO
    Box(Modifier.fillMaxWidth(), contentAlignment = if (delUsuario) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (delUsuario) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (delUsuario) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(
                topStart = Dimensiones.radioGrande,
                topEnd = Dimensiones.radioGrande,
                bottomStart = if (delUsuario) Dimensiones.radioGrande else Dimensiones.radioPequeno,
                bottomEnd = if (delUsuario) Dimensiones.radioPequeno else Dimensiones.radioGrande,
            ),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                if (delUsuario) AnnotatedString(mensaje.texto) else conNegritas(mensaje.texto),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
            )
        }
    }
}

@Composable
private fun Escribiendo() {
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(Dimensiones.iconoPequeno), strokeWidth = Dimensiones.grosorBordeMarcado)
        Text(
            "Consultando el inventario…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorEnvio(texto: String, alReintentar: () -> Unit) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        BotonTexto("Reintentar", alReintentar)
    }
}

@Composable
private fun Compositor(
    borrador: String,
    puedeEnviar: Boolean,
    alCambiar: (String) -> Unit,
    alEnviar: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = borrador,
            onValueChange = alCambiar,
            placeholder = { Text("Escribe tu pregunta") },
            maxLines = 4,
            shape = MaterialTheme.shapes.large,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { if (puedeEnviar) alEnviar() }),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = alEnviar,
            enabled = puedeEnviar,
            shape = MaterialTheme.shapes.large,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(Dimensiones.alturaBotonPrincipal),
        ) {
            Icon(Iconos.enviar, contentDescription = "Enviar", modifier = Modifier.size(Dimensiones.icono))
        }
    }
}

/**
 * El asistente escribe en Markdown ligero. Se respetan las negritas (`**texto**`) y las viñetas
 * (`- `); lo demás se muestra tal cual. Una tabla Markdown se lee peor en un teléfono que una
 * lista, por eso el prompt pide listas.
 */
internal fun conNegritas(texto: String): AnnotatedString = buildAnnotatedString {
    texto.lines().forEachIndexed { i, linea ->
        if (i > 0) append('\n')
        val contenido = if (linea.trimStart().startsWith("- ")) "• " + linea.trimStart().removePrefix("- ") else linea
        val partes = contenido.split("**")
        partes.forEachIndexed { j, parte ->
            if (j % 2 == 1 && j < partes.lastIndex) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(parte) }
            } else {
                if (j % 2 == 1) append("**")
                append(parte)
            }
        }
    }
}
