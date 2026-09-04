package co.inventario.designsystem.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.BorderStroke
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos
import co.inventario.designsystem.tema.Tipografia
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_VISIBLE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Una fecha se elige, no se teclea. Antes había que escribir `AAAA-MM-DD` a mano en reportes y
 * facturas, con un `try/catch` detrás por si la persona se equivocaba de formato: eso es pedirle
 * al usuario que haga de validador.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(
    valor: LocalDate?,
    alCambiar: (LocalDate) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    habilitado: Boolean = true,
) {
    var abierto by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo)) {
        OutlinedCard(
            onClick = { if (habilitado) abierto = true },
            enabled = habilitado,
            shape = MaterialTheme.shapes.small,
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                Dimensiones.grosorBorde,
                SolidColor(
                    if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                ),
            ),
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimensiones.alturaCampo),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensiones.espacio, vertical = Dimensiones.espacioMedio),
                horizontalArrangement = Arrangement.spacedBy(Dimensiones.espacioMedio),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Iconos.fecha,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensiones.icono),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioMinimo)) {
                    Text(
                        etiqueta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        valor?.format(FORMATO_VISIBLE) ?: "Elegir una fecha",
                        style = Tipografia.numeroCuerpo,
                        color = if (valor != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
            }
        }
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = Dimensiones.espacio),
            )
        }
    }

    if (abierto) {
        val inicial = (valor ?: LocalDate.now())
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        val estadoCalendario = rememberDatePickerState(initialSelectedDateMillis = inicial)
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        estadoCalendario.selectedDateMillis?.let { millis ->
                            alCambiar(
                                Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate(),
                            )
                        }
                        abierto = false
                    },
                    modifier = Modifier.defaultMinSize(minHeight = Dimensiones.areaTactilMinima),
                ) {
                    Text("Elegir", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { abierto = false },
                    modifier = Modifier.defaultMinSize(minHeight = Dimensiones.areaTactilMinima),
                ) {
                    Text("Cancelar", style = MaterialTheme.typography.labelLarge)
                }
            },
        ) {
            DatePicker(state = estadoCalendario)
        }
    }
}
