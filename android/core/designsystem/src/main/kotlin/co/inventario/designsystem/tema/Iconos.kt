package co.inventario.designsystem.tema

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiOff

/**
 * El vocabulario de iconos de la app, en un solo sitio. Antes no se dibujaba ninguno pese a
 * tener la librería declarada; centralizarlos evita que dos pantallas elijan dibujos distintos
 * para la misma idea, que es como se pierde el significado.
 *
 * Regla: `Filled` solo para el destino activo de la barra inferior; el resto, `Outlined`.
 */
object Iconos {
    // Navegación
    val atras = Icons.AutoMirrored.Outlined.ArrowBack
    val avanzar = Icons.AutoMirrored.Outlined.KeyboardArrowRight
    val masOpciones = Icons.Outlined.MoreVert
    val cerrar = Icons.Outlined.Close

    // Destinos
    val escanear = Icons.Outlined.QrCodeScanner
    val escanearActivo = Icons.Filled.QrCodeScanner
    val catalogo = Icons.Outlined.Search
    val catalogoActivo = Icons.Filled.Search
    val reponer = Icons.Outlined.Inventory2
    val reponerActivo = Icons.Filled.Inventory2
    val mas = Icons.Outlined.Menu
    val masActivo = Icons.Filled.Menu

    // Movimientos
    val entrada = Icons.Outlined.ArrowDownward
    val salida = Icons.Outlined.ArrowUpward
    val merma = Icons.Outlined.WarningAmber
    val conteo = Icons.Outlined.FactCheck
    val historial = Icons.Outlined.History
    val anular = Icons.Outlined.Undo

    // Catálogo
    val editar = Icons.Outlined.Edit
    val archivar = Icons.Outlined.Archive
    val desarchivar = Icons.Outlined.Unarchive
    val anadir = Icons.Outlined.Add
    val quitar = Icons.Outlined.Remove
    val imagen = Icons.Outlined.Image
    val camara = Icons.Outlined.PhotoCamera

    // Escaneo
    val linterna = Icons.Outlined.FlashlightOn
    val linternaApagada = Icons.Outlined.FlashlightOff
    val teclado = Icons.Outlined.Keyboard

    // Compras y documentos
    val proveedor = Icons.Outlined.LocalShipping
    val orden = Icons.Outlined.Description
    val factura = Icons.Outlined.ReceiptLong
    val reporte = Icons.Outlined.BarChart
    val ajustes = Icons.Outlined.Settings
    val cerrarSesion = Icons.AutoMirrored.Outlined.Logout
    val fecha = Icons.Outlined.CalendarToday

    // Estados
    val confirmar = Icons.Outlined.Check
    val error = Icons.Outlined.ErrorOutline
    val reintentar = Icons.Outlined.Refresh
    val sinConexion = Icons.Outlined.WifiOff
    val vacio = Icons.Outlined.Inventory2
}
