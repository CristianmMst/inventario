package co.inventario.designsystem.tema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * El esquema entero, no solo los roles obvios. Dejar `secondaryContainer`, `surfaceVariant` u
 * `outline` sin cablear no los deja «neutros»: los deja en el morado *baseline* de Material 3,
 * que es de donde salían los chips lavanda sobre el verde de la marca.
 */
private val esquemaClaro = lightColorScheme(
    primary = Colores.primario,
    onPrimary = Colores.sobrePrimario,
    primaryContainer = Colores.primarioContenedor,
    onPrimaryContainer = Colores.sobrePrimarioContenedor,
    inversePrimary = Colores.primarioInverso,

    secondary = Colores.secundario,
    onSecondary = Colores.sobreSecundario,
    secondaryContainer = Colores.secundarioContenedor,
    onSecondaryContainer = Colores.sobreSecundarioContenedor,

    tertiary = Colores.terciario,
    onTertiary = Colores.sobreTerciario,
    tertiaryContainer = Colores.terciarioContenedor,
    onTertiaryContainer = Colores.sobreTerciarioContenedor,

    background = Colores.fondo,
    onBackground = Colores.sobreFondo,
    surface = Colores.superficie,
    onSurface = Colores.sobreSuperficie,
    surfaceVariant = Colores.superficieVariante,
    onSurfaceVariant = Colores.sobreSuperficieVariante,

    surfaceContainerLowest = Colores.superficieContenedorMinima,
    surfaceContainerLow = Colores.superficieContenedorBaja,
    surfaceContainer = Colores.superficieContenedor,
    surfaceContainerHigh = Colores.superficieContenedorAlta,
    surfaceContainerHighest = Colores.superficieContenedorMaxima,

    outline = Colores.contorno,
    outlineVariant = Colores.contornoVariante,

    inverseSurface = Colores.superficieInversa,
    inverseOnSurface = Colores.sobreSuperficieInversa,

    error = Colores.error,
    onError = Colores.sobreError,
    errorContainer = Colores.errorContenedor,
    onErrorContainer = Colores.sobreErrorContenedor,
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
            // `extraSmall` gobierna los campos de texto; sin cablearlo se quedaban en 4 dp
            // mientras el resto de la app iba a 12.
            extraSmall = RoundedCornerShape(Dimensiones.radioPequeno),
            small = RoundedCornerShape(Dimensiones.radio),
            medium = RoundedCornerShape(Dimensiones.radio),
            large = RoundedCornerShape(Dimensiones.radioGrande),
            extraLarge = RoundedCornerShape(Dimensiones.radioDestacado),
        ),
        content = contenido,
    )
}
