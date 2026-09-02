package co.inventario.app.navegacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import co.inventario.feature.auth.PantallaLogin
import co.inventario.feature.auth.PantallaRegistro
import co.inventario.feature.escaneo.PantallaEscaneo
import kotlinx.coroutines.flow.Flow

/**
 * NavHost de la app (T-074). El inicio es el escaneo (HU-03): la cámara es la puerta de todo.
 * `sesionCerrada` viene de la capa de red: al cerrarse la sesión se vuelve al inicio de sesión
 * desde cualquier pantalla (RF-AUT-003).
 */
@Composable
fun NavegacionInventario(haySesion: Boolean, sesionCerrada: Flow<Unit>) {
    val nav = rememberNavController()

    LaunchedEffect(Unit) {
        sesionCerrada.collect {
            nav.navigate(Ruta.Login) { popUpTo(0) { inclusive = true } }
        }
    }

    // Sin tamaño explícito el NavHost mide 0×0 dentro del ComposeView (wrap_content) y no se ve nada.
    NavHost(
        navController = nav,
        startDestination = if (haySesion) Ruta.Inicio else Ruta.Login,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable<Ruta.Login> {
            PantallaLogin(
                alIniciarSesion = { nav.navigate(Ruta.Inicio) { popUpTo(0) { inclusive = true } } },
                irARegistro = { nav.navigate(Ruta.Registro) },
            )
        }
        composable<Ruta.Registro> {
            PantallaRegistro(
                alRegistrarse = { nav.navigate(Ruta.Inicio) { popUpTo(0) { inclusive = true } } },
                irALogin = { nav.popBackStack() },
            )
        }
        composable<Ruta.Inicio> {
            PantallaEscaneo(
                // T-080/T-081 resuelven el código contra el catálogo; hasta entonces se muestra.
                alLeerCodigo = { codigo -> nav.navigate(Ruta.AltaProducto(codigo)) },
                irABuscar = { nav.navigate(Ruta.Busqueda) },
            )
        }
        composable<Ruta.AltaProducto> { entrada ->
            val ruta = entrada.toRoute<Ruta.AltaProducto>()
            Marcador("Código leído: ${ruta.codigoBarras ?: "ninguno"}")
        }
        composable<Ruta.Busqueda> { Marcador("Búsqueda (T-083)") }
    }
}

@Composable
private fun Marcador(texto: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(texto) }
}
