package co.inventario.designsystem.componentes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Tipografia

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
) {
    Button(
        onClick = alPulsar,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().height(Dimensiones.alturaBotonPrincipal),
    ) {
        Text(texto, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun BotonSecundario(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    OutlinedButton(
        onClick = alPulsar,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = Dimensiones.areaTactilMinima),
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge)
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
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = valor,
            onValueChange = alCambiar,
            label = { Text(etiqueta) },
            isError = error != null,
            enabled = habilitado,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = tipoTeclado),
            visualTransformation = if (esContrasena) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimensiones.areaTactilMinima),
        )
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
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

/** El stock actual, destacado para leerse de pie (RNF-09). */
@Composable
fun StockDestacado(cantidad: String, unidad: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(cantidad, style = Tipografia.stockDestacado, color = MaterialTheme.colorScheme.primary)
        Text(unidad, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun MensajeError(texto: String?, modifier: Modifier = Modifier) {
    if (texto != null) {
        Text(texto, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
    }
}
