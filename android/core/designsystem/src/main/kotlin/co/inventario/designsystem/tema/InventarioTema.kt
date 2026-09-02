package co.inventario.designsystem.tema

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Tema mínimo para que la app compile; T-073 fija tipografía ≥ 16 sp y contraste AA. */
@Composable
fun InventarioTema(contenido: @Composable () -> Unit) {
    MaterialTheme(content = contenido)
}
