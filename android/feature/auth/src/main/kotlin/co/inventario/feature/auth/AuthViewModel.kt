package co.inventario.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.RepositorioSesion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Un solo `StateFlow` con datos, `cargando` y `error` (plan.md §8.3). */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val nombre: String = "",
    val negocio: String = "",
    val moneda: String = "COP",
    val cargando: Boolean = false,
    val error: String? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
)

/** Sucesos de una sola vez: no van en el estado para no repetirse al rotar. */
sealed interface AuthSuceso {
    data object SesionIniciada : AuthSuceso
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repositorio: RepositorioSesion,
) : ViewModel() {

    private val _estado = MutableStateFlow(AuthUiState())
    val estado: StateFlow<AuthUiState> = _estado.asStateFlow()

    private val _sucesos = Channel<AuthSuceso>(Channel.BUFFERED)
    val sucesos = _sucesos.receiveAsFlow()

    fun cambiarEmail(v: String) = _estado.update { it.copy(email = v, erroresCampo = it.erroresCampo - "email", error = null) }
    fun cambiarPassword(v: String) = _estado.update { it.copy(password = v, erroresCampo = it.erroresCampo - "password", error = null) }
    fun cambiarNombre(v: String) = _estado.update { it.copy(nombre = v, erroresCampo = it.erroresCampo - "nombre") }
    fun cambiarNegocio(v: String) = _estado.update { it.copy(negocio = v, erroresCampo = it.erroresCampo - "negocio") }
    fun cambiarMoneda(v: String) = _estado.update { it.copy(moneda = v.uppercase().take(3), erroresCampo = it.erroresCampo - "moneda") }

    fun iniciarSesion() {
        val s = _estado.value
        val errores = validarCredenciales(s.email, s.password)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresCampo = errores) }
            return
        }
        ejecutar { repositorio.iniciarSesion(s.email.trim(), s.password) }
    }

    fun registrar() {
        val s = _estado.value
        val errores = validarCredenciales(s.email, s.password).toMutableMap()
        if (s.nombre.isBlank()) errores["nombre"] = "Escribe tu nombre."
        if (s.negocio.isBlank()) errores["negocio"] = "Escribe el nombre del negocio."
        if (!Regex("^[A-Z]{3}$").matches(s.moneda)) errores["moneda"] = "La moneda son tres letras, como COP."
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresCampo = errores) }
            return
        }
        ejecutar {
            repositorio.registrar(
                email = s.email.trim(), password = s.password, nombre = s.nombre.trim(),
                negocio = s.negocio.trim(), moneda = s.moneda, zonaHoraria = java.util.TimeZone.getDefault().id,
            )
        }
    }

    private fun ejecutar(operacion: suspend () -> Resultado<*>) {
        if (_estado.value.cargando) return
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val resultado = operacion()) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(cargando = false) }
                    _sucesos.send(AuthSuceso.SesionIniciada)
                }
                is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = resultado.error.mensaje) }
            }
        }
    }

    private fun validarCredenciales(email: String, password: String): Map<String, String> {
        val errores = mutableMapOf<String, String>()
        if (!email.contains("@") || email.length < 5) errores["email"] = "Escribe un correo válido."
        if (password.length < 8) errores["password"] = "La contraseña tiene al menos 8 caracteres."
        return errores
    }
}
