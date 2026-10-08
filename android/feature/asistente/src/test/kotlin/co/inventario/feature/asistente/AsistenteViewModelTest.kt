package co.inventario.feature.asistente

import co.inventario.common.Resultado
import co.inventario.common.error.MapeadorErrores
import co.inventario.data.repositorio.RepositorioAsistente
import co.inventario.domain.modelo.AutorMensaje
import co.inventario.domain.modelo.MensajeChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** RF-AST-001/002: conversar con el asistente desde la app. */
class AsistenteViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class RepoFalso : RepositorioAsistente {
        var chatsCreados = 0
        val enviados = mutableListOf<Pair<String, String>>()
        val fallos = ArrayDeque<String>()

        override suspend fun crearChat(): Resultado<String> {
            chatsCreados++
            return Resultado.Exito("chat_$chatsCreados")
        }

        override suspend fun enviar(chatId: String, texto: String): Resultado<List<String>> {
            enviados += chatId to texto
            fallos.removeFirstOrNull()?.let { return Resultado.Fallo(MapeadorErrores.error(it)) }
            return Resultado.Exito(listOf("Respuesta a: $texto"))
        }

        override suspend fun terminar(chatId: String) = Resultado.Exito(Unit)
    }

    private val repo = RepoFalso()

    @BeforeTest fun antes() = Dispatchers.setMain(dispatcher)

    @AfterTest fun despues() = Dispatchers.resetMain()

    @Test
    fun `el chat se abre con el primer mensaje y se reutiliza`() = runTest(dispatcher) {
        val vm = AsistenteViewModel(repo)
        assertEquals(0, repo.chatsCreados)

        vm.cambiarBorrador("  ¿Qué está por agotarse?  ")
        vm.enviar()
        advanceUntilIdle()
        vm.enviar("¿Y agotados?")
        advanceUntilIdle()

        assertEquals(1, repo.chatsCreados)
        assertEquals(listOf("chat_1" to "¿Qué está por agotarse?", "chat_1" to "¿Y agotados?"), repo.enviados)
        assertEquals(
            listOf(
                MensajeChat(AutorMensaje.USUARIO, "¿Qué está por agotarse?"),
                MensajeChat(AutorMensaje.ASISTENTE, "Respuesta a: ¿Qué está por agotarse?"),
                MensajeChat(AutorMensaje.USUARIO, "¿Y agotados?"),
                MensajeChat(AutorMensaje.ASISTENTE, "Respuesta a: ¿Y agotados?"),
            ),
            vm.estado.value.mensajes,
        )
        assertEquals("", vm.estado.value.borrador)
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun `un mensaje vacio no se envia`() = runTest(dispatcher) {
        val vm = AsistenteViewModel(repo)
        vm.enviar("   ")
        advanceUntilIdle()
        assertEquals(0, repo.chatsCreados)
        assertEquals(emptyList(), vm.estado.value.mensajes)
    }

    @Test
    fun `un fallo muestra el error y reintentar no duplica el mensaje`() = runTest(dispatcher) {
        repo.fallos += "ASISTENTE_NO_DISPONIBLE"
        val vm = AsistenteViewModel(repo)

        vm.enviar("hola")
        advanceUntilIdle()
        assertEquals(MapeadorErrores.mensajePara("ASISTENTE_NO_DISPONIBLE"), vm.estado.value.error)
        assertEquals("hola", vm.estado.value.pendiente)

        vm.reintentar()
        advanceUntilIdle()

        assertNull(vm.estado.value.error)
        assertEquals(
            listOf(MensajeChat(AutorMensaje.USUARIO, "hola"), MensajeChat(AutorMensaje.ASISTENTE, "Respuesta a: hola")),
            vm.estado.value.mensajes,
        )
    }

    @Test
    fun `un chat terminado en el servidor se reemplaza por uno nuevo`() = runTest(dispatcher) {
        val vm = AsistenteViewModel(repo)
        vm.enviar("uno")
        advanceUntilIdle()
        repo.fallos += "CHAT_TERMINADO"

        vm.enviar("dos")
        advanceUntilIdle()
        vm.reintentar()
        advanceUntilIdle()

        assertEquals(2, repo.chatsCreados)
        assertEquals("chat_2" to "dos", repo.enviados.last())
    }

    @Test
    fun `las negritas y vinetas del asistente se respetan`() {
        val texto = conNegritas("Resumen\n- **Arroz** (A-1): 2 / 10")
        assertEquals("Resumen\n• Arroz (A-1): 2 / 10", texto.text)
        assertEquals(1, texto.spanStyles.size)
    }
}
