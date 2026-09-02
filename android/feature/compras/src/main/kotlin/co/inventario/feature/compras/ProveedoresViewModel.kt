package co.inventario.feature.compras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.inventario.common.Resultado
import co.inventario.data.repositorio.ProveedorDatos
import co.inventario.data.repositorio.RepositorioCompras
import co.inventario.domain.modelo.Proveedor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Formulario de alta/edición; `id` null es alta. Los campos son texto tal como se teclea. */
data class FormularioProveedor(
    val id: String? = null,
    val campos: Map<String, String> = emptyMap(),
)

data class ProveedoresUiState(
    val proveedores: List<Proveedor> = emptyList(),
    val incluirArchivados: Boolean = false,
    val formulario: FormularioProveedor? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val erroresCampo: Map<String, String> = emptyMap(),
    /** RN-17: id del proveedor que no se pudo eliminar porque tiene documentos. */
    val sugerirArchivar: String? = null,
)

/** RF-COM-001 / RN-17: proveedores. Con documentos no se borra: se ofrece archivar. */
@HiltViewModel
class ProveedoresViewModel @Inject constructor(private val compras: RepositorioCompras) : ViewModel() {

    private val _estado = MutableStateFlow(ProveedoresUiState())
    val estado: StateFlow<ProveedoresUiState> = _estado.asStateFlow()

    init { cargar() }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            when (val r = compras.proveedores(_estado.value.incluirArchivados)) {
                is Resultado.Exito -> _estado.update { it.copy(cargando = false, proveedores = r.valor) }
                is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = r.error.mensaje) }
            }
        }
    }

    fun alternarArchivados() {
        _estado.update { it.copy(incluirArchivados = !it.incluirArchivados) }
        cargar()
    }

    fun nuevo() = _estado.update { it.copy(formulario = FormularioProveedor(), erroresCampo = emptyMap(), error = null) }

    fun editar(id: String) = _estado.update { s ->
        val p = s.proveedores.firstOrNull { it.id == id } ?: return@update s
        s.copy(
            formulario = FormularioProveedor(
                id = id,
                campos = mapOf(
                    "nombre" to p.nombre, "identificacionFiscal" to p.identificacionFiscal.orEmpty(), "contacto" to p.contacto.orEmpty(),
                    "telefono" to p.telefono.orEmpty(), "email" to p.email.orEmpty(), "direccion" to p.direccion.orEmpty(), "notas" to p.notas.orEmpty(),
                ),
            ),
            erroresCampo = emptyMap(),
        )
    }

    fun cerrarFormulario() = _estado.update { it.copy(formulario = null, erroresCampo = emptyMap()) }

    fun cambiarCampo(nombre: String, valor: String) = _estado.update { s ->
        val f = s.formulario ?: return@update s
        s.copy(formulario = f.copy(campos = f.campos + (nombre to valor)), erroresCampo = s.erroresCampo - nombre)
    }

    fun guardar() {
        val f = _estado.value.formulario ?: return
        val nombre = f.campos["nombre"].orEmpty().trim()
        if (nombre.isBlank()) {
            _estado.update { it.copy(erroresCampo = mapOf("nombre" to "El nombre es obligatorio.")) }
            return
        }
        val datos = ProveedorDatos(
            nombre = nombre, identificacionFiscal = f.campos["identificacionFiscal"], contacto = f.campos["contacto"], telefono = f.campos["telefono"],
            email = f.campos["email"], direccion = f.campos["direccion"], notas = f.campos["notas"],
        )
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val r = if (f.id == null) compras.crearProveedor(datos) else compras.editarProveedor(f.id, datos)
            when (r) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(formulario = null) }
                    cargar()
                }
                is Resultado.Fallo -> _estado.update { it.copy(cargando = false, error = r.error.mensaje) }
            }
        }
    }

    fun eliminar(id: String) {
        viewModelScope.launch {
            when (val r = compras.eliminarProveedor(id)) {
                is Resultado.Exito -> cargar()
                is Resultado.Fallo -> _estado.update {
                    it.copy(error = r.error.mensaje, sugerirArchivar = if (r.error.codigo == "PROVEEDOR_CON_DOCUMENTOS") id else null)
                }
            }
        }
    }

    fun archivar(id: String, archivar: Boolean = true) {
        viewModelScope.launch {
            when (val r = compras.archivarProveedor(id, archivar)) {
                is Resultado.Exito -> {
                    _estado.update { it.copy(sugerirArchivar = null, error = null) }
                    cargar()
                }
                is Resultado.Fallo -> _estado.update { it.copy(error = r.error.mensaje) }
            }
        }
    }
}
