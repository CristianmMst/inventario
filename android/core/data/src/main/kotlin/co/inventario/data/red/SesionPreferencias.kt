package co.inventario.data.red

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Tokens en SharedPreferences privadas de la app (modo privado, sin respaldo: allowBackup es
 * false). El de acceso caduca a los 15 minutos; el de renovación rota en cada uso (RNF-11).
 */
class SesionPreferencias(contexto: Context) : AlmacenSesion {
    private val prefs: SharedPreferences =
        contexto.getSharedPreferences("sesion_inventario", Context.MODE_PRIVATE)

    override fun tokens(): Tokens? {
        val acceso = prefs.getString(CLAVE_ACCESO, null) ?: return null
        val renovacion = prefs.getString(CLAVE_RENOVACION, null) ?: return null
        return Tokens(acceso, renovacion)
    }

    override fun guardar(tokens: Tokens) {
        prefs.edit(commit = true) {
            putString(CLAVE_ACCESO, tokens.acceso)
            putString(CLAVE_RENOVACION, tokens.renovacion)
        }
    }

    override fun negocio(): DatosNegocio? {
        val nombre = prefs.getString(CLAVE_NEGOCIO, null) ?: return null
        val moneda = prefs.getString(CLAVE_MONEDA, null) ?: return null
        return DatosNegocio(nombre, moneda)
    }

    override fun guardarNegocio(negocio: DatosNegocio) {
        prefs.edit(commit = true) {
            putString(CLAVE_NEGOCIO, negocio.nombre)
            putString(CLAVE_MONEDA, negocio.monedaBase)
        }
    }

    override fun borrar() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val CLAVE_ACCESO = "token_acceso"
        const val CLAVE_RENOVACION = "token_renovacion"
        const val CLAVE_NEGOCIO = "negocio_nombre"
        const val CLAVE_MONEDA = "negocio_moneda"
    }
}
