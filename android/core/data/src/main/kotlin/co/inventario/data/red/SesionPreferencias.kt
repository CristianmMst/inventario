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

    override fun borrar() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val CLAVE_ACCESO = "token_acceso"
        const val CLAVE_RENOVACION = "token_renovacion"
    }
}
