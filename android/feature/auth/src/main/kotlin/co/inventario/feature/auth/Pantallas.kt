package co.inventario.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.inventario.designsystem.componentes.BotonPrincipal
import co.inventario.designsystem.componentes.BotonSecundario
import co.inventario.designsystem.componentes.CampoTexto
import co.inventario.designsystem.componentes.MensajeError
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Iconos

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
    FormularioAuth {
        Marca()
        Titulo("Inventario", "Inicia sesión para seguir con tu negocio.")
        CampoTexto(
            estado.email,
            alCambiarEmail,
            "Correo",
            error = estado.erroresCampo["email"],
            tipoTeclado = KeyboardType.Email,
            habilitado = !estado.cargando,
        )
        CampoTexto(
            estado.password,
            alCambiarPassword,
            "Contraseña",
            error = estado.erroresCampo["password"],
            esContrasena = true,
            habilitado = !estado.cargando,
        )
        MensajeError(estado.error)
        Spacer(Modifier.height(Dimensiones.espacioCompacto))
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
    FormularioAuth {
        Marca()
        Titulo("Abrir mi cuenta", "Tu usuario y tu negocio se crean juntos, en un paso.")
        CampoTexto(
            estado.nombre,
            alCambiarNombre,
            "Tu nombre",
            error = estado.erroresCampo["nombre"],
            habilitado = !estado.cargando,
        )
        CampoTexto(
            estado.email,
            alCambiarEmail,
            "Correo",
            error = estado.erroresCampo["email"],
            tipoTeclado = KeyboardType.Email,
            habilitado = !estado.cargando,
        )
        CampoTexto(
            estado.password,
            alCambiarPassword,
            "Contraseña",
            error = estado.erroresCampo["password"],
            esContrasena = true,
            habilitado = !estado.cargando,
            apoyo = "Mínimo 8 caracteres.",
        )
        CampoTexto(
            estado.negocio,
            alCambiarNegocio,
            "Nombre del negocio",
            error = estado.erroresCampo["negocio"],
            habilitado = !estado.cargando,
        )
        CampoTexto(
            estado.moneda,
            alCambiarMoneda,
            "Moneda",
            error = estado.erroresCampo["moneda"],
            habilitado = !estado.cargando,
            apoyo = "Código ISO 4217, como COP o USD. No se puede cambiar después.",
        )
        MensajeError(estado.error)
        Spacer(Modifier.height(Dimensiones.espacioCompacto))
        BotonPrincipal(if (estado.cargando) "Creando…" else "Crear cuenta", alRegistrar, habilitado = !estado.cargando)
        BotonSecundario("Ya tengo cuenta", irALogin, habilitado = !estado.cargando)
    }
}

/** Formulario centrado y con ancho máximo: en una pantalla ancha no se estira hasta el ridículo. */
@Composable
private fun FormularioAuth(contenido: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimensiones.espacioAmplio),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = ANCHO_MAXIMO),
            verticalArrangement = Arrangement.spacedBy(Dimensiones.espacio),
        ) {
            contenido()
        }
    }
}

private val ANCHO_MAXIMO = 420.dp

/** La marca, que sencillamente no existía: el login empezaba con un campo de texto. */
@Composable
private fun Marca() {
    Spacer(Modifier.height(Dimensiones.espacioSeccion))
    Box(
        Modifier
            .size(Dimensiones.alturaBotonPrincipal)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimensiones.radioGrande)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Iconos.reponer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(Dimensiones.iconoGrande),
        )
    }
}

@Composable
private fun Titulo(titulo: String, explicacion: String) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensiones.espacioCompacto),
    ) {
        Text(
            titulo,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            explicacion,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
