package co.inventario.app.navegacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import co.inventario.feature.auth.PantallaLogin
import co.inventario.feature.auth.PantallaRegistro
import co.inventario.feature.catalogo.AccionesFicha
import co.inventario.feature.catalogo.PantallaAltaProducto
import co.inventario.feature.catalogo.PantallaBusqueda
import co.inventario.feature.catalogo.PantallaFicha
import co.inventario.feature.catalogo.PantallaResolverCodigo
import co.inventario.feature.escaneo.PantallaEscaneo
import kotlinx.coroutines.flow.Flow

/**
 * NavHost de la app (T-074). El inicio es el escaneo (HU-03): la cámara es la puerta de todo.
 * `sesionCerrada` viene de la capa de red: al cerrarse la sesión se vuelve al inicio de sesión
 * desde cualquier pantalla (RF-AUT-003). `monedaBase` es la del negocio (E-01).
 */
@Composable
fun NavegacionInventario(haySesion: Boolean, monedaBase: () -> String, sesionCerrada: Flow<Unit>) {
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
                alLeerCodigo = { codigo -> nav.navigate(Ruta.ResolverCodigo(codigo)) },
                irABuscar = { nav.navigate(Ruta.Busqueda) },
            )
        }
        composable<Ruta.ResolverCodigo> { entrada ->
            val ruta = entrada.toRoute<Ruta.ResolverCodigo>()
            PantallaResolverCodigo(
                codigo = ruta.codigo,
                // La resolución sale de la pila: "atrás" desde la ficha vuelve al escaneo.
                alAbrirFicha = { id -> nav.navigate(Ruta.Producto(id)) { popUpTo<Ruta.Inicio>() } },
                alCrearConCodigo = { codigo -> nav.navigate(Ruta.AltaProducto(codigo)) { popUpTo<Ruta.Inicio>() } },
                alVolver = nav::volverAlInicio,
            )
        }
        composable<Ruta.Producto> { entrada ->
            val ruta = entrada.toRoute<Ruta.Producto>()
            PantallaFicha(
                productoId = ruta.productoId,
                acciones = AccionesFicha(
                    registrarSalida = { id -> nav.navigate(Ruta.Movimiento(id, "salida")) },
                    registrarEntrada = { id -> nav.navigate(Ruta.Movimiento(id, "entrada")) },
                    registrarMerma = { id -> nav.navigate(Ruta.Movimiento(id, "merma")) },
                    contar = { id -> nav.navigate(Ruta.Conteo(id)) },
                    verHistorial = { id -> nav.navigate(Ruta.Historial(id)) },
                    editar = { id -> nav.navigate(Ruta.EditarProducto(id)) },
                    volverAEscanear = nav::volverAlInicio,
                ),
            )
        }
        composable<Ruta.AltaProducto> { entrada ->
            val ruta = entrada.toRoute<Ruta.AltaProducto>()
            PantallaAltaProducto(
                codigoBarras = ruta.codigoBarras,
                productoId = null,
                monedaBase = monedaBase(),
                alGuardar = { id -> nav.navigate(Ruta.Producto(id)) { popUpTo<Ruta.Inicio>() } },
                alCancelar = { nav.popBackStack() },
            )
        }
        composable<Ruta.EditarProducto> { entrada ->
            val ruta = entrada.toRoute<Ruta.EditarProducto>()
            PantallaAltaProducto(
                codigoBarras = null,
                productoId = ruta.productoId,
                monedaBase = monedaBase(),
                alGuardar = { nav.popBackStack() },
                alCancelar = { nav.popBackStack() },
            )
        }
        composable<Ruta.Busqueda> {
            PantallaBusqueda(
                alAbrirFicha = { id -> nav.navigate(Ruta.Producto(id)) },
                alCrearProducto = { nav.navigate(Ruta.AltaProducto()) },
                alVolver = nav::volverAlInicio,
            )
        }
        composable<Ruta.Movimiento> { entrada ->
            val ruta = entrada.toRoute<Ruta.Movimiento>()
            Marcador("Movimiento ${ruta.tipo} (T-084/T-085)")
        }
        composable<Ruta.Conteo> { Marcador("Conteo (T-086)") }
        composable<Ruta.Historial> { Marcador("Historial (T-087)") }
    }
}

private fun NavHostController.volverAlInicio() {
    popBackStack(Ruta.Inicio, inclusive = false)
}

@Composable
private fun Marcador(texto: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(texto) }
}
