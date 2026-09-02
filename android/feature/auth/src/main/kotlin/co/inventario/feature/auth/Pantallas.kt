package co.inventario.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones

/** Pantallas con estado (Hilt) que delegan en Composables sin estado, previsualizables. */

@Composable
fun PantallaLogin(alIniciarSesion: () -> Unit, irARegistro: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.sucesos.collect { if (it is AuthSuceso.SesionIniciada) alIniciarSesion() } }
    ContenidoLogin(
        estado = estado,
        alCambiarEmail = vm::cambiarEmail,
        alCambiarPassword = vm::cambiarPassword,
        alEntrar = vm::iniciarSesion,
        irARegistro = irARegistro,
    )
}

@Composable
fun ContenidoLogin(
    estado: AuthUiState,
    alCambiarEmail: (String) -> Unit,
    alCambiarPassword: (String) -> Unit,
    alEntrar: () -> Unit,
    irARegistro: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Spacer(Modifier.height(Dimensiones.espacio))
        Text("Inventario", style = MaterialTheme.typography.headlineMedium)
        Text("Inicia sesión para seguir con tu negocio.", style = MaterialTheme.typography.bodyLarge)
        CampoTexto(estado.email, alCambiarEmail, "Correo", error = estado.erroresCampo["email"], tipoTeclado = KeyboardType.Email, habilitado = !estado.cargando)
        CampoTexto(estado.password, alCambiarPassword, "Contraseña", error = estado.erroresCampo["password"], esContrasena = true, habilitado = !estado.cargando)
        MensajeError(estado.error)
        BotonPrincipal(if (estado.cargando) "Entrando…" else "Entrar", alEntrar, habilitado = !estado.cargando)
        BotonSecundario("Crear mi cuenta", irARegistro, habilitado = !estado.cargando)
    }
}

@Composable
fun PantallaRegistro(alRegistrarse: () -> Unit, irALogin: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.sucesos.collect { if (it is AuthSuceso.SesionIniciada) alRegistrarse() } }
    ContenidoRegistro(
        estado = estado,
        alCambiarEmail = vm::cambiarEmail,
        alCambiarPassword = vm::cambiarPassword,
        alCambiarNombre = vm::cambiarNombre,
        alCambiarNegocio = vm::cambiarNegocio,
        alCambiarMoneda = vm::cambiarMoneda,
        alRegistrar = vm::registrar,
        irALogin = irALogin,
    )
}

@Composable
fun ContenidoRegistro(
    estado: AuthUiState,
    alCambiarEmail: (String) -> Unit,
    alCambiarPassword: (String) -> Unit,
    alCambiarNombre: (String) -> Unit,
    alCambiarNegocio: (String) -> Unit,
    alCambiarMoneda: (String) -> Unit,
    alRegistrar: () -> Unit,
    irALogin: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimensiones.espacio),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
    ) {
        Spacer(Modifier.height(Dimensiones.espacio))
        Text("Abrir mi cuenta", style = MaterialTheme.typography.headlineMedium)
        Text("Tu usuario y tu negocio se crean juntos, en un paso.", style = MaterialTheme.typography.bodyLarge)
        CampoTexto(estado.nombre, alCambiarNombre, "Tu nombre", error = estado.erroresCampo["nombre"], habilitado = !estado.cargando)
        CampoTexto(estado.email, alCambiarEmail, "Correo", error = estado.erroresCampo["email"], tipoTeclado = KeyboardType.Email, habilitado = !estado.cargando)
        CampoTexto(estado.password, alCambiarPassword, "Contraseña (mínimo 8)", error = estado.erroresCampo["password"], esContrasena = true, habilitado = !estado.cargando)
        CampoTexto(estado.negocio, alCambiarNegocio, "Nombre del negocio", error = estado.erroresCampo["negocio"], habilitado = !estado.cargando)
        CampoTexto(estado.moneda, alCambiarMoneda, "Moneda (ISO 4217)", error = estado.erroresCampo["moneda"], habilitado = !estado.cargando)
        MensajeError(estado.error)
        BotonPrincipal(if (estado.cargando) "Creando…" else "Crear cuenta", alRegistrar, habilitado = !estado.cargando)
        BotonSecundario("Ya tengo cuenta", irALogin, habilitado = !estado.cargando)
    }
}
