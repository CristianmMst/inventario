package co.inventario.designsystem.tema

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.inventario.designsystem.R
import kotlin.math.pow

/**
 * Tokens del sistema de diseño (RNF-08, RNF-09). Fijados aquí una sola vez para que ninguna
 * pantalla pueda incumplirlos por descuido: texto de contenido ≥ 16 sp, área táctil ≥ 48 dp,
 * contraste AA. Utilizable de pie, con el brillo al mínimo y con una mano.
 *
 * Los mínimos son el suelo, no el techo: la jerarquía se construye por encima de ellos con
 * peso, color y tamaño, nunca bajando de 16 sp.
 */
object Dimensiones {
    /**
     * RNF-08: ningún objetivo táctil por debajo de 48 × 48 dp.
     *
     * Todo token cuyo nombre empiece por `altura` o `areaTactil` mide un control que el dedo
     * toca y `TemaTest` lo verifica contra este mínimo. Lo que no se toca (grosores, iconos,
     * espacios) lleva otro prefijo a propósito.
     */
    val areaTactilMinima: Dp = 48.dp
    /** Los botones de acción principal son más altos: se pulsan con el pulgar sin mirar. */
    val alturaBotonPrincipal: Dp = 56.dp
    val alturaBotonSecundario: Dp = 48.dp
    val alturaCampo: Dp = 56.dp
    val alturaFilaLista: Dp = 72.dp
    /** Filas que además llevan una acción propia, como las de reposición. */
    val alturaFilaDestacada: Dp = 88.dp
    val alturaBarraNavegacion: Dp = 80.dp
    val alturaBarraSuperior: Dp = 64.dp
    /** El paso de −/+ del selector de cantidad; cómodo para el pulgar sin mirar. */
    val alturaPaso: Dp = 64.dp

    val espacioMinimo: Dp = 4.dp
    val espacioCompacto: Dp = 8.dp
    val espacioMedio: Dp = 12.dp
    val espacio: Dp = 16.dp
    val espacioAmplio: Dp = 24.dp
    val espacioSeccion: Dp = 32.dp

    val radioPequeno: Dp = 8.dp
    val radio: Dp = 12.dp
    val radioGrande: Dp = 16.dp
    val radioDestacado: Dp = 20.dp
    /** Píldoras y chips: un radio mayor que cualquier alto real. */
    val radioPildora: Dp = 999.dp

    val elevacionNivel0: Dp = 0.dp
    val elevacionNivel1: Dp = 1.dp
    val elevacionNivel2: Dp = 3.dp
    val elevacionNivel3: Dp = 6.dp

    /** Glifos, no objetivos táctiles: el objetivo es el `IconButton` que los envuelve. */
    val iconoPequeno: Dp = 20.dp
    val icono: Dp = 24.dp
    val iconoGrande: Dp = 32.dp
    val iconoIlustracion: Dp = 64.dp
    val miniatura: Dp = 48.dp
    val miniaturaGrande: Dp = 88.dp

    val grosorBorde: Dp = 1.dp
    val grosorBordeMarcado: Dp = 2.dp
    val grosorBarraProgreso: Dp = 8.dp
}

/**
 * Paleta Material 3 completa. Antes solo se cableaban ocho roles y el resto caía al morado
 * *baseline* de M3: cada `FilterChip` de la app se pintaba lavanda contra el verde de marca.
 * Aquí está el esquema entero, derivado del mismo `primario` de siempre.
 */
object Colores {
    val primario = Color(0xFF0B5D4B)
    val sobrePrimario = Color(0xFFFFFFFF)
    val primarioContenedor = Color(0xFF9FF2D8)
    val sobrePrimarioContenedor = Color(0xFF002019)
    val primarioInverso = Color(0xFF83D6BE)

    val secundario = Color(0xFF4B635A)
    val sobreSecundario = Color(0xFFFFFFFF)
    val secundarioContenedor = Color(0xFFCDE9DD)
    val sobreSecundarioContenedor = Color(0xFF072019)

    /** Lo informativo y las salidas de inventario; un azul apagado que no compite con el verde. */
    val terciario = Color(0xFF3F6375)
    val sobreTerciario = Color(0xFFFFFFFF)
    val terciarioContenedor = Color(0xFFC2E8FF)
    val sobreTerciarioContenedor = Color(0xFF001E2C)

    val fondo = Color(0xFFFBFDFA)
    val sobreFondo = Color(0xFF191C1B)
    val superficie = Color(0xFFFBFDFA)
    val sobreSuperficie = Color(0xFF191C1B)
    val superficieVariante = Color(0xFFDBE5DF)
    val sobreSuperficieVariante = Color(0xFF3F4945)

    val superficieContenedorMinima = Color(0xFFFFFFFF)
    val superficieContenedorBaja = Color(0xFFF5F8F4)
    val superficieContenedor = Color(0xFFEFF2EE)
    val superficieContenedorAlta = Color(0xFFE9ECE9)
    val superficieContenedorMaxima = Color(0xFFE3E7E3)

    val contorno = Color(0xFF6F7975)
    val contornoVariante = Color(0xFFBFC9C4)

    val superficieInversa = Color(0xFF2E312F)
    val sobreSuperficieInversa = Color(0xFFEFF1EE)

    val error = Color(0xFFB3261E)
    val sobreError = Color(0xFFFFFFFF)
    val errorContenedor = Color(0xFFFFDAD5)
    val sobreErrorContenedor = Color(0xFF410002)
}

/**
 * El color que responde la única pregunta que Marta le hace a la app: «¿me puedo fiar de este
 * número?». No es decoración; es la diferencia entre un stock sano, uno que hay que reponer y
 * uno que ya se acabó. `exito` y `advertencia` existían sin usarse: aquí tienen trabajo.
 */
object Estado {
    val enRango = Color(0xFF1B6E3A)
    val enRangoContenedor = Color(0xFFB7F2C6)
    val sobreEnRangoContenedor = Color(0xFF00210C)

    val bajoMinimo = Color(0xFF8A5A00)
    val bajoMinimoContenedor = Color(0xFFFFDEA6)
    val sobreBajoMinimoContenedor = Color(0xFF2B1700)

    val agotado = Color(0xFFB3261E)
    val agotadoContenedor = Color(0xFFFFDAD5)
    val sobreAgotadoContenedor = Color(0xFF410002)

    /** Archivado, anulado: presente pero fuera de juego. */
    val neutro = Color(0xFF3F4945)
    val neutroContenedor = Color(0xFFE3E7E3)
}

/**
 * IBM Plex Sans (SIL OFL). Se eligió por las cifras: el 4 abierto y el 1 con base distinguen
 * un stock de otro a un metro y con el brillo bajo, y trae versalitas numéricas tabulares para
 * que la columna de cantidades no baile al cambiar de dígito.
 */
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

/** RNF-09: el texto de contenido no baja de 16 sp; el stock y las cantidades, destacados. */
object Tipografia {
    val tamanoMinimoContenido = 16.sp

    /** Cifras de ancho fijo: obligatorio en todo número que viva en una columna. */
    private const val TABULARES = "tnum"

    val material: Typography = Typography(
        displayLarge = TextStyle(fontFamily = PlexSans, fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold),
        headlineLarge = TextStyle(fontFamily = PlexSans, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontFamily = PlexSans, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontFamily = PlexSans, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontFamily = PlexSans, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontFamily = PlexSans, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontFamily = PlexSans, fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = TextStyle(fontFamily = PlexSans, fontSize = 16.sp, lineHeight = 24.sp),
        bodySmall = TextStyle(fontFamily = PlexSans, fontSize = 16.sp, lineHeight = 22.sp),
        labelLarge = TextStyle(fontFamily = PlexSans, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontFamily = PlexSans, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = PlexSans, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    )

    /** El stock actual se lee de pie, a un metro: grande, en negrita y tabular. */
    val stockDestacado = TextStyle(
        fontFamily = PlexSans,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULARES,
    )

    /** El mismo papel que `stockDestacado` pero dentro de una tarjeta, no como héroe. */
    val stockSecundario = TextStyle(
        fontFamily = PlexSans,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULARES,
    )

    /** La cifra que cierra cada fila de un listado. */
    val numeroFila = TextStyle(
        fontFamily = PlexSans,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TABULARES,
    )

    /** Importes, fechas y códigos dentro de texto corrido. */
    val numeroCuerpo = TextStyle(
        fontFamily = PlexSans,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TABULARES,
    )

    /** Todos los estilos de contenido que expone el tema, para verificarlos de una vez. */
    val estilos: List<TextStyle>
        get() = listOf(
            material.displayLarge, material.headlineLarge, material.headlineMedium,
            material.titleLarge, material.titleMedium, material.titleSmall,
            material.bodyLarge, material.bodyMedium, material.bodySmall,
            material.labelLarge, material.labelMedium, material.labelSmall,
            stockDestacado, stockSecundario, numeroFila, numeroCuerpo,
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

/**
 * Pares texto/fondo que usa el tema; todos deben pasar AA. Añadir un color al sistema sin
 * añadirlo aquí es lo que `TemaTest` impide: la lista es la red de seguridad, no un trámite.
 */
val paresDeContraste: List<Pair<Color, Color>> = listOf(
    Colores.sobrePrimario to Colores.primario,
    Colores.sobrePrimarioContenedor to Colores.primarioContenedor,
    Colores.sobreSecundario to Colores.secundario,
    Colores.sobreSecundarioContenedor to Colores.secundarioContenedor,
    Colores.sobreTerciario to Colores.terciario,
    Colores.sobreTerciarioContenedor to Colores.terciarioContenedor,

    Colores.sobreFondo to Colores.fondo,
    Colores.sobreSuperficie to Colores.superficie,
    Colores.sobreSuperficieVariante to Colores.superficieVariante,
    Colores.sobreSuperficie to Colores.superficieContenedorMinima,
    Colores.sobreSuperficie to Colores.superficieContenedorBaja,
    Colores.sobreSuperficie to Colores.superficieContenedor,
    Colores.sobreSuperficie to Colores.superficieContenedorAlta,
    Colores.sobreSuperficie to Colores.superficieContenedorMaxima,
    Colores.sobreSuperficieVariante to Colores.superficieContenedorAlta,
    Colores.sobreSuperficieInversa to Colores.superficieInversa,
    Colores.primarioInverso to Colores.superficieInversa,

    Colores.sobreError to Colores.error,
    Colores.sobreErrorContenedor to Colores.errorContenedor,

    Estado.enRango to Colores.fondo,
    Estado.sobreEnRangoContenedor to Estado.enRangoContenedor,
    Estado.bajoMinimo to Colores.fondo,
    Estado.sobreBajoMinimoContenedor to Estado.bajoMinimoContenedor,
    Estado.agotado to Colores.fondo,
    Estado.sobreAgotadoContenedor to Estado.agotadoContenedor,
    Estado.neutro to Estado.neutroContenedor,

    Colores.primario to Colores.fondo,
    Colores.primario to Colores.superficieContenedorBaja,
)
