package co.inventario.feature.ajustes

import co.inventario.common.Resultado
import co.inventario.data.repositorio.RepositorioAjustes
import co.inventario.domain.modelo.ApiKey
import co.inventario.domain.modelo.ApiKeyCreada
import co.inventario.domain.modelo.Suscripcion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RF-AUT-005 / RF-INT-005: el secreto se muestra una sola vez; webhooks con contrato, sin entrega. */
class AjustesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val repo = object : RepositorioAjustes {
        val claves = mutableListOf(ApiKey("k1", "Caja", "inv_abc12345", Instant.parse("2026-09-01T10:00:00Z"), null, null))
        val revocadas = mutableListOf<String>()
        val suscripciones = mutableListOf<Suscripcion>()
        var creadas = 0
        override suspend fun apiKeys() = Resultado.Exito(claves.toList())
        override suspend fun crearApiKey(nombre: String): Resultado<ApiKeyCreada> {
            creadas++
            val clave = ApiKey("k$creadas-nueva", nombre, "inv_zzz99999", Instant.parse("2026-09-02T10:00:00Z"), null, null)
            claves += clave
            return Resultado.Exito(ApiKeyCreada(clave, "inv_zzz99999_SECRETO-QUE-SOLO-SE-VE-UNA-VEZ"))
        }
        override suspend fun revocarApiKey(id: String): Resultado<Unit> { revocadas += id; claves.removeAll { it.id == id }; return Resultado.Exito(Unit) }
        override suspend fun suscripciones() = Resultado.Exito(suscripciones.toList())
        override suspend fun crearSuscripcion(url: String, tipos: List<String>, secreto: String, descripcion: String?): Resultado<Suscripcion> {
            val s = Suscripcion("s${suscripciones.size + 1}", url, tipos, true, descripcion, Instant.parse("2026-09-02T10:00:00Z"))
            suscripciones += s
            return Resultado.Exito(s)
        }
        override suspend fun eliminarSuscripcion(id: String): Resultado<Unit> { suscripciones.removeAll { it.id == id }; return Resultado.Exito(Unit) }
    }

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    private fun vm() = AjustesViewModel(repo).also { dispatcher.scheduler.advanceUntilIdle() }

    @Test
    fun `rf aut 005 el secreto se muestra una sola vez y luego solo queda el prefijo`() = runTest(dispatcher) {
        val vm = vm()
        vm.cambiarNombreClave("Integración contable")
        vm.crearClave(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("inv_zzz99999_SECRETO-QUE-SOLO-SE-VE-UNA-VEZ", vm.estado.value.secretoNuevo)
        vm.secretoGuardado()
        assertNull(vm.estado.value.secretoNuevo)
        assertEquals(listOf("inv_abc12345", "inv_zzz99999"), vm.estado.value.claves.map { it.prefijo })
        assertTrue(vm.estado.value.claves.none { "SECRETO" in it.toString() })
    }

    @Test
    fun `rf aut 005 revocar quita la clave de la lista`() = runTest(dispatcher) {
        val vm = vm()
        vm.revocarClave("k1"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("k1"), repo.revocadas)
        assertTrue(vm.estado.value.claves.isEmpty())
    }

    @Test
    fun `rf int 005 una suscripcion exige url https, al menos un tipo y secreto largo`() = runTest(dispatcher) {
        val vm = vm()
        vm.cambiarUrlWebhook("http://inseguro.example.com/hook")
        vm.cambiarSecretoWebhook("corto")
        vm.crearSuscripcion(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("url", "tipos", "secreto"), vm.estado.value.erroresCampo.keys)
        vm.cambiarUrlWebhook("https://contable.example.com/hook")
        vm.alternarTipo("movimiento.registrado")
        vm.cambiarSecretoWebhook("un-secreto-de-al-menos-treinta-y-dos-caracteres")
        vm.crearSuscripcion(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("movimiento.registrado"), vm.estado.value.suscripciones.single().tipos)
        assertTrue(vm.estado.value.tiposDisponibles.contains("stock.bajo_minimo"))
    }
}
