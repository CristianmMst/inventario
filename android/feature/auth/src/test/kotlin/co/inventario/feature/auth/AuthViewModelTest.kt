package co.inventario.feature.auth

import app.cash.turbine.test
import co.inventario.common.ErrorApp
import co.inventario.common.Resultado
import co.inventario.data.repositorio.RepositorioSesion
import co.inventario.data.repositorio.SesionIniciada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RF-AUT-001 / RF-AUT-002: estado único, sucesos de una sola vez, errores en español. */
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class RepoFalso(var respuesta: Resultado<SesionIniciada>) : RepositorioSesion {
        var llamadas = 0
        override fun haySesion() = false
        override suspend fun registrar(email: String, password: String, nombre: String, negocio: String, moneda: String, zonaHoraria: String) = also { llamadas++ }.respuesta
        override suspend fun iniciarSesion(email: String, password: String) = also { llamadas++ }.respuesta
        override suspend fun cerrarSesion() = Unit
    }

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)
    @AfterTest fun despues() = Dispatchers.resetMain()

    @Test
    fun `iniciar sesion con exito emite el suceso una sola vez y deja de cargar`() = runTest(dispatcher) {
        val repo = RepoFalso(Resultado.Exito(SesionIniciada("Marta", "Papelería", "COP")))
        val vm = AuthViewModel(repo)
        vm.cambiarEmail("marta@papeleria.co")
        vm.cambiarPassword("Contrasena-segura-123")
        vm.sucesos.test {
            vm.iniciarSesion()
            assertEquals(AuthSuceso.SesionIniciada, awaitItem())
            expectNoEvents()
        }
        assertTrue(!vm.estado.value.cargando)
        assertNull(vm.estado.value.error)
        assertEquals(1, repo.llamadas)
    }

    @Test
    fun `una credencial invalida muestra el texto en espanol y no navega`() = runTest(dispatcher) {
        val repo = RepoFalso(Resultado.Fallo(ErrorApp("CREDENCIAL_INVALIDA", "Correo o contraseña incorrectos.")))
        val vm = AuthViewModel(repo)
        vm.cambiarEmail("marta@papeleria.co")
        vm.cambiarPassword("mala-contrasena")
        vm.sucesos.test {
            vm.iniciarSesion()
            dispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals("Correo o contraseña incorrectos.", vm.estado.value.error)
        assertTrue(!vm.estado.value.cargando)
    }

    @Test
    fun `no se llama a la api con campos invalidos y cada campo senala su error`() = runTest(dispatcher) {
        val repo = RepoFalso(Resultado.Exito(SesionIniciada("M", "P", "COP")))
        val vm = AuthViewModel(repo)
        vm.cambiarEmail("no-es-correo")
        vm.cambiarPassword("corta")
        vm.iniciarSesion()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("email", "password"), vm.estado.value.erroresCampo.keys)
        assertEquals(0, repo.llamadas)
    }

    @Test
    fun `el registro exige nombre negocio y moneda iso`() = runTest(dispatcher) {
        val repo = RepoFalso(Resultado.Exito(SesionIniciada("M", "P", "COP")))
        val vm = AuthViewModel(repo)
        vm.cambiarEmail("marta@papeleria.co")
        vm.cambiarPassword("Contrasena-segura-123")
        vm.cambiarMoneda("12")
        vm.registrar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("nombre", "negocio", "moneda"), vm.estado.value.erroresCampo.keys)
        assertEquals(0, repo.llamadas)
        vm.cambiarNombre("Marta"); vm.cambiarNegocio("Papelería"); vm.cambiarMoneda("cop")
        assertEquals("COP", vm.estado.value.moneda)
        vm.sucesos.test {
            vm.registrar()
            assertEquals(AuthSuceso.SesionIniciada, awaitItem())
        }
        assertEquals(1, repo.llamadas)
    }

    @Test
    fun `un fallo de red dice que no se pudo y permite reintentar`() = runTest(dispatcher) {
        val repo = RepoFalso(Resultado.Fallo(ErrorApp("SIN_RED", "No se guardó. Sin conexión: reintentando…")))
        val vm = AuthViewModel(repo)
        vm.cambiarEmail("marta@papeleria.co"); vm.cambiarPassword("Contrasena-segura-123")
        vm.iniciarSesion(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.estado.value.error!!.startsWith("No se guardó"))
        repo.respuesta = Resultado.Exito(SesionIniciada("Marta", "P", "COP"))
        vm.sucesos.test {
            vm.iniciarSesion()
            assertEquals(AuthSuceso.SesionIniciada, awaitItem())
        }
        assertEquals(2, repo.llamadas)
    }
}
