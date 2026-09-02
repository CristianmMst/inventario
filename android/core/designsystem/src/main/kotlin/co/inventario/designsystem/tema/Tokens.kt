package co.inventario.designsystem.tema

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow

/**
 * Tokens del sistema de diseño (RNF-08, RNF-09). Fijados aquí una sola vez para que ninguna
 * pantalla pueda incumplirlos por descuido: texto de contenido ≥ 16 sp, área táctil ≥ 48 dp,
 * contraste AA. Utilizable de pie, con el brillo al mínimo y con una mano.
 */
object Dimensiones {
    /** RNF-08: ningún objetivo táctil por debajo de 48 × 48 dp. */
    val areaTactilMinima: Dp = 48.dp
    /** Los botones de acción principal son más altos: se pulsan con el pulgar sin mirar. */
    val alturaBotonPrincipal: Dp = 56.dp
    val espacio: Dp = 16.dp
    val espacioCompacto: Dp = 8.dp
    val radio: Dp = 12.dp
}

object Colores {
    val primario = Color(0xFF0B5D4B)
    val sobrePrimario = Color(0xFFFFFFFF)
    val fondo = Color(0xFFFFFFFF)
    val sobreFondo = Color(0xFF1A1C1A)
    val superficie = Color(0xFFF4F7F5)
    val sobreSuperficie = Color(0xFF1A1C1A)
    val error = Color(0xFFB3261E)
    val sobreError = Color(0xFFFFFFFF)
    val exito = Color(0xFF1B6E3A)
    val advertencia = Color(0xFF8A5A00)
    val stockDestacado = Color(0xFF0B5D4B)
}

/** RNF-09: el texto de contenido no baja de 16 sp; el stock y las cantidades, destacados. */
object Tipografia {
    val tamanoMinimoContenido = 16.sp

    val material: Typography = Typography(
        displayLarge = TextStyle(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodySmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
        labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    )

    /** El stock actual se lee de pie, a un metro: grande y en negrita. */
    val stockDestacado = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)

    /** Todos los estilos de contenido que expone el tema, para verificarlos de una vez. */
    val estilos: List<TextStyle>
        get() = listOf(
            material.displayLarge, material.headlineMedium, material.titleLarge, material.titleMedium,
            material.bodyLarge, material.bodyMedium, material.bodySmall,
            material.labelLarge, material.labelMedium, material.labelSmall, stockDestacado,
        )
}

/** Relación de contraste WCAG 2.x entre dos colores opacos. AA exige 4.5:1 para texto normal. */
fun contraste(a: Color, b: Color): Double {
    fun luminancia(c: Color): Double {
        fun canal(v: Float): Double {
            val x = v.toDouble()
            return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * canal(c.red) + 0.7152 * canal(c.green) + 0.0722 * canal(c.blue)
    }
    val l1 = luminancia(a)
    val l2 = luminancia(b)
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
}

/** Pares texto/fondo que usa el tema; todos deben pasar AA. */
val paresDeContraste: List<Pair<Color, Color>> = listOf(
    Colores.sobrePrimario to Colores.primario,
    Colores.sobreFondo to Colores.fondo,
    Colores.sobreSuperficie to Colores.superficie,
    Colores.sobreError to Colores.error,
    Colores.exito to Colores.fondo,
    Colores.advertencia to Colores.fondo,
    Colores.stockDestacado to Colores.fondo,
)
