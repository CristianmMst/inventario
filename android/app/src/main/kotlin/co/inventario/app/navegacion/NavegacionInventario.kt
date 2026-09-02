package co.inventario.app.navegacion

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import co.inventario.domain.modelo.TipoMovimiento
import co.inventario.feature.ajustes.PantallaAjustes
import co.inventario.feature.auth.PantallaLogin
import co.inventario.feature.auth.PantallaRegistro
import co.inventario.feature.catalogo.AccionesFicha
import co.inventario.feature.catalogo.PantallaAltaProducto
import co.inventario.feature.catalogo.PantallaBusqueda
import co.inventario.feature.catalogo.PantallaFicha
import co.inventario.feature.catalogo.PantallaResolverCodigo
import co.inventario.feature.compras.PantallaNuevaOrden
import co.inventario.feature.compras.PantallaOrdenDetalle
import co.inventario.feature.compras.PantallaOrdenes
import co.inventario.feature.compras.PantallaProveedores
import co.inventario.feature.compras.PantallaRecepcion
import co.inventario.feature.escaneo.PantallaEscaneo
import co.inventario.feature.facturas.PantallaFacturas
import co.inventario.feature.facturas.PantallaNuevaFactura
import co.inventario.feature.movimientos.PantallaConteo
import co.inventario.feature.movimientos.PantallaHistorial
import co.inventario.feature.movimientos.PantallaMovimiento
import co.inventario.feature.reportes.PantallaReportes
import kotlinx.coroutines.flow.Flow

/**
 * NavHost de la app (T-074). El inicio es el escaneo (HU-03): la cámara es la puerta de todo.
 * `sesionCerrada` viene de la capa de red: al cerrarse la sesión se vuelve al inicio de sesión
 * desde cualquier pantalla (RF-AUT-003). `monedaBase` y `nombreNegocio` son los del negocio.
 */
@Composable
fun NavegacionInventario(
    haySesion: Boolean,
    monedaBase: () -> String,
    nombreNegocio: () -> String,
    sesionCerrada: Flow<Unit>,
) {
    val nav = rememberNavController()

    LaunchedEffect(Unit) {
        sesionCerrada.collect { nav.aLogin() }
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
                irAMenu = { nav.navigate(Ruta.Menu) },
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
            PantallaMovimiento(
                productoId = ruta.productoId,
                tipo = TipoMovimiento.desde(ruta.tipo),
                // Tras registrar se vuelve a la ficha, que recarga el stock desde el servidor.
                alRegistrar = { nav.popBackStack() },
                alCancelar = { nav.popBackStack() },
            )
        }
        composable<Ruta.Conteo> { entrada ->
            val ruta = entrada.toRoute<Ruta.Conteo>()
            PantallaConteo(productoId = ruta.productoId, alRegistrar = { nav.popBackStack() }, alCancelar = { nav.popBackStack() })
        }
        composable<Ruta.Historial> { entrada ->
            val ruta = entrada.toRoute<Ruta.Historial>()
            PantallaHistorial(productoId = ruta.productoId, alVolver = { nav.popBackStack() })
        }

        // --- H9 -------------------------------------------------------------------------------
        composable<Ruta.Menu> {
            PantallaMenu(nombreNegocio = nombreNegocio(), irA = { nav.navigate(it) }, alVolver = nav::volverAlInicio)
        }
        composable<Ruta.Proveedores> { PantallaProveedores(alVolver = { nav.popBackStack() }) }
        composable<Ruta.Ordenes> {
            PantallaOrdenes(alAbrir = { id -> nav.navigate(Ruta.Orden(id)) }, alNueva = { nav.navigate(Ruta.NuevaOrden) }, alVolver = { nav.popBackStack() })
        }
        composable<Ruta.Orden> { entrada ->
            val ruta = entrada.toRoute<Ruta.Orden>()
            PantallaOrdenDetalle(ordenId = ruta.ordenId, alRecibir = { id -> nav.navigate(Ruta.Recepcion(id)) }, alVolver = { nav.popBackStack() })
        }
        composable<Ruta.NuevaOrden> {
            PantallaNuevaOrden(
                monedaBase = monedaBase(),
                alCrear = { id -> nav.navigate(Ruta.Orden(id)) { popUpTo<Ruta.Ordenes>() } },
                alCancelar = { nav.popBackStack() },
            )
        }
        composable<Ruta.Recepcion> { entrada ->
            val ruta = entrada.toRoute<Ruta.Recepcion>()
            PantallaRecepcion(
                ordenId = ruta.ordenId,
                monedaBase = monedaBase(),
                // Confirmada: se vuelve a donde se venía (menú u orden), que recarga.
                alConfirmar = { nav.popBackStack() },
                alCancelar = { nav.popBackStack() },
            )
        }
        composable<Ruta.Facturas> {
            PantallaFacturas(alNueva = { nav.navigate(Ruta.NuevaFactura) }, alVolver = { nav.popBackStack() })
        }
        composable<Ruta.NuevaFactura> {
            PantallaNuevaFactura(monedaBase = monedaBase(), alRegistrar = { nav.popBackStack() }, alCancelar = { nav.popBackStack() })
        }
        composable<Ruta.Reportes> {
            PantallaReportes(alAbrirProducto = { id -> nav.navigate(Ruta.Producto(id)) }, alVolver = { nav.popBackStack() })
        }
        composable<Ruta.Ajustes> {
            val sesion: SesionViewModel = hiltViewModel()
            PantallaAjustes(alCerrarSesion = { sesion.cerrarSesion { nav.aLogin() } }, alVolver = { nav.popBackStack() })
        }
    }
}

private fun NavHostController.volverAlInicio() {
    popBackStack(Ruta.Inicio, inclusive = false)
}

private fun NavHostController.aLogin() {
    navigate(Ruta.Login) { popUpTo(0) { inclusive = true } }
}
