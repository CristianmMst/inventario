package co.inventario.designsystem.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos
import co.inventario.designsystem.tema.Tipografia
import java.math.BigDecimal

/**
 * Componentes compartidos. Los mínimos de RNF-08 y RNF-09 viven aquí: quien use estos
 * componentes no puede bajar de 48 dp de área táctil ni de 16 sp de texto.
 */

@Composable
fun BotonPrincipal(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    icono: ImageVector? = null,
) {
    Button(
        onClick = alPulsar,
        enabled = habilitado,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().height(Dimensiones.alturaBotonPrincipal),
    ) {
        if (icono != null) {
            // El icono repite lo que dice la etiqueta; para el lector de pantalla sobra.
            Icon(icono, contentDescription = null, modifier = Modifier.size(Dimensiones.icono))
            Spacer(Modifier.width(Dimensiones.espacioCompacto))
        }
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BotonSecundario(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    icono: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    OutlinedButton(
        onClick = alPulsar,
        enabled = habilitado,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = Dimensiones.areaTactilMinima),
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(Dimensiones.iconoPequeno))
            Spacer(Modifier.width(Dimensiones.espacioCompacto))
        }
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

/** Acción terciaria: la que no merece un borde, pero sí los 48 dp. */
@Composable
fun BotonTexto(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(
        onClick = alPulsar,
        enabled = habilitado,
        modifier = modifier.defaultMinSize(minHeight = Dimensiones.areaTactilMinima),
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    tipoTeclado: KeyboardType = KeyboardType.Text,
    esContrasena: Boolean = false,
    habilitado: Boolean = true,
    apoyo: String? = null,
    lineas: Int = 1,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo)) {
        OutlinedTextField(
            value = valor,
            onValueChange = alCambiar,
            label = { Text(etiqueta) },
            isError = error != null,
            enabled = habilitado,
            singleLine = lineas == 1,
            maxLines = lineas,
            shape = MaterialTheme.shapes.small,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado),
            visualTransformation = if (esContrasena) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimensiones.alturaCampo),
        )
        val apoyoVisible = error ?: apoyo
        if (apoyoVisible != null) {
            Text(
                apoyoVisible,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Dimensiones.espacio),
            )
        }
    }
}

/** Campo numérico para cantidades: teclado decimal y el valor siempre como texto (RN-07). */
@Composable
fun CampoCantidad(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    admiteDecimales: Boolean = true,
) {
    CampoTexto(
        valor = valor,
        alCambiar = { nuevo -> if (nuevo.all { it.isDigit() || (admiteDecimales && it == '.') }) alCambiar(nuevo) },
        etiqueta = etiqueta,
        modifier = modifier,
        error = error,
        tipoTeclado = if (admiteDecimales) KeyboardType.Decimal else KeyboardType.Number,
    )
}

/**
 * Cantidad con pasos de −1 y +1. Una salida de mostrador es casi siempre de una a tres
 * unidades: con esto no hace falta abrir el teclado ni mirar la pantalla. El valor sigue
 * viviendo como texto (RN-07); los pasos solo lo reescriben.
 */
@Composable
fun SelectorCantidad(
    valor: String,
    alCambiar: (String) -> Unit,
    unidad: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    admiteDecimales: Boolean = true,
    habilitado: Boolean = true,
) {
    fun desplazar(pasos: Int) {
        val actual = valor.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val nuevo = actual.add(BigDecimal(pasos)).max(BigDecimal.ZERO)
        alCambiar(nuevo.stripTrailingZeros().toPlainString())
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotonPaso(
                icono = Iconos.quitar,
                descripcion = "Quitar uno",
                alPulsar = { desplazar(-1) },
                habilitado = habilitado && (valor.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO,
            )
            CampoCantidad(
                valor = valor,
                alCambiar = alCambiar,
                etiqueta = unidad,
                admiteDecimales = admiteDecimales,
                modifier = Modifier.weight(1f),
            )
            BotonPaso(
                icono = Iconos.anadir,
                descripcion = "Añadir uno",
                alPulsar = { desplazar(1) },
                habilitado = habilitado,
            )
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BotonPaso(
    icono: ImageVector,
    descripcion: String,
    alPulsar: () -> Unit,
    habilitado: Boolean,
) {
    OutlinedButton(
        onClick = alPulsar,
        enabled = habilitado,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(Dimensiones.espacioMinimo),
        modifier = Modifier.size(Dimensiones.alturaPaso),
    ) {
        Icon(icono, contentDescription = descripcion, modifier = Modifier.size(Dimensiones.iconoGrande))
    }
}

/** El stock actual, destacado para leerse de pie (RNF-09). */
@Composable
fun StockDestacado(
    cantidad: String,
    unidad: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(cantidad, style = Tipografia.stockDestacado, color = color)
        Text(
            unidad,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimensiones.espacioCompacto),
        )
    }
}

@Composable
fun MensajeError(texto: String?, modifier: Modifier = Modifier) {
    if (texto != null) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Iconos.error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(Dimensiones.icono).clearAndSetSemantics { },
            )
            Text(
                texto,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
