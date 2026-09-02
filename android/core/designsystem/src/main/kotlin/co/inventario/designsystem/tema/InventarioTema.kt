package co.inventario.designsystem.tema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val esquemaClaro = lightColorScheme(
    primary = Colores.primario,
    onPrimary = Colores.sobrePrimario,
    background = Colores.fondo,
    onBackground = Colores.sobreFondo,
    surface = Colores.superficie,
    onSurface = Colores.sobreSuperficie,
    error = Colores.error,
    onError = Colores.sobreError,
)

/**
 * Tema único de la app (RNF-08, RNF-09). Solo claro: la papelería tiene luz y la bodega no,
 * y en ambas el contraste AA con fondo claro se lee con el brillo al mínimo.
 */
@Composable
fun InventarioTema(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = esquemaClaro,
        typography = Tipografia.material,
        shapes = Shapes(
            small = RoundedCornerShape(Dimensiones.radio),
            medium = RoundedCornerShape(Dimensiones.radio),
            large = RoundedCornerShape(Dimensiones.radio),
        ),
        content = contenido,
    )
}
