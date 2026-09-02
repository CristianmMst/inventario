# Backlog de implementación — App de Inventario v1

Fase 4 · Ordenado por dependencias. Rige `constitution.md`; implementa `spec.md` según
`plan.md`.

**Formato.** Cada tarea es de **medio día como máximo**. `Cubre` referencia los requisitos de
`spec.md`. `Dep.` son las tareas que deben estar hechas antes. La columna **∥** marca las que
pueden hacerse en paralelo con sus hermanas del mismo hito.

**Criterio de "hecho"** (`constitution.md` §6): el criterio de verificación se cumple, los
tests pasan, el `RF-` aparece en el mensaje del commit, la API afectada figura en OpenAPI, no
quedan `TODO` sin issue y ninguna otra prueba se rompió.

**Nomenclatura de los tests.** Todo test nombra el requisito que verifica:
`test_rf_inv_005_salida_mayor_que_stock_se_rechaza`. Es lo que hace comprobable, con un
`grep`, que ningún `RF-` quedó sin prueba.

**Orden general.** El backend de cada área va antes que su pantalla en Android, porque la app
no puede probarse contra nada. Una vez cerrado el contrato de un área (su hito de backend
terminado), el trabajo de Android de esa área puede avanzar en paralelo con el backend del
área siguiente. Los hitos H7 a H9 solo dependen de que exista el endpoint, no de que esté
pulido.

---

## H0 · Cimientos

Sin esto no se puede escribir nada más. Ninguna tarea de este hito es paralelizable con las
otras: cada una apoya la siguiente.

| ID | Tarea | Archivos | Cubre | Verificación | Dep. | ∥ |
|---|---|---|---|---|---|---|
| `T-001` | Esqueleto del repositorio y `docker-compose.yml` con PostgreSQL 16 y backend con recarga; volumen para `IMAGENES_DIR` | `docker-compose.yml`, `backend/Dockerfile`, `backend/pyproject.toml`, `.env.example` | `plan.md` §10 | `docker compose up` levanta los dos servicios y `GET /api/v1/salud` responde 200 | — | |
| `T-002` | App FastAPI, configuración por entorno y logging estructurado con `X-Request-Id` | `app/main.py`, `app/config.py`, `app/infra/logging.py` | `RNF-07`, `RNF-12` | Un error provocado devuelve `X-Request-Id` y el log correlaciona; el cuerpo no contiene traza | `T-001` | |
| `T-003` | Formato único de error y manejadores de excepción; jerarquía de errores de dominio sin HTTP | `app/api/errores.py`, `app/dominio/errores.py` | `constitution.md` §3, `RNF-07`, `RNF-12` | Tests de 400, 401, 403, 404, 409 y 422 que verifican el esquema `{error:{code,message,details}}` | `T-002` | |
| `T-004` | Alembic configurado, migración inicial vacía y comprobación de `downgrade` | `alembic/`, `alembic.ini` | `constitution.md` §4 | `alembic upgrade head` y `downgrade base` sin error sobre base limpia, en CI | `T-001` | |
| `T-005` | Paginación por cursor genérica y dependencia de FastAPI | `app/infra/paginacion.py`, `app/api/deps.py` | `constitution.md` §2, `RF-CAT-014` | Test: insertar filas entre dos páginas no duplica ni salta registros | `T-003` | |
| `T-006` | Base de pruebas: `conftest` con testcontainers PostgreSQL, cliente `httpx` y fábricas de datos | `tests/conftest.py`, `tests/fabricas.py` | `constitution.md` §5 | `pytest` verde contra PostgreSQL real; ningún test usa SQLite | `T-004` | |
| `T-007` | Tipos de dominio `Dinero`, `Cantidad`, `Moneda`, `UnidadMedida` | `app/dominio/tipos.py` | `RN-07`, `plan.md` §0 | Tests: `Dinero` no acepta `float`, serializa como cadena decimal; `Cantidad` rechaza más de 3 decimales | `T-006` | |

---

## H1 · Identidad y acceso

| ID | Tarea | Archivos | Cubre | Verificación | Dep. | ∥ |
|---|---|---|---|---|---|---|
| `T-008` | Migración `usuarios`, `negocios`, `membresias` | `alembic/versions/`, `app/modelos/identidad.py` | `RF-AUT-001`, `RF-AUT-004`, `RN-19` | `upgrade`/`downgrade` limpios; `membresias` existe aunque v1 cree una sola fila | `T-007` | |
| `T-009` | Registro self-service: crea usuario, negocio y membresía en una operación | `app/api/v1/auth.py`, `app/servicios/auth.py` | `RF-AUT-001`, `RF-AUT-004` | Test: registro devuelve tokens; correo repetido responde 409 sin revelar más | `T-008` | |
| `T-010` | Login y emisión de JWT de acceso (15 min, Argon2id para contraseñas) | `app/servicios/auth.py`, `app/infra/seguridad.py` | `RF-AUT-002`, `RNF-11` | Test: credencial correcta devuelve token válido; incorrecta responde 401 `CREDENCIAL_INVALIDA` | `T-009` | |
| `T-011` | Migración `refresh_tokens` y renovación con rotación y detección de reúso | `alembic/versions/`, `app/servicios/auth.py` | `RF-AUT-003`, `RNF-11` | Test: reusar un token revocado revoca **toda la familia** | `T-010` | |
| `T-012` | Cambio de contraseña con revocación de refresh tokens | `app/api/v1/auth.py` | `RF-AUT-006` | Test: tras cambiar la contraseña, el refresh anterior deja de funcionar | `T-011` | |
| `T-013` | Migración `api_keys`, emisión, listado y revocación; secreto mostrado una sola vez | `alembic/versions/`, `app/api/v1/api_keys.py` | `RF-AUT-005` | Test: el secreto solo aparece en la respuesta de creación; en la base hay hash Argon2id | `T-010` | ∥ |
| `T-014` | Dependencia `contexto_actual()`: resuelve JWT y `X-API-Key`, devuelve `ContextoNegocio`; aislamiento con 404 | `app/api/deps.py` | `RF-AUT-007`, `RNF-12` | Test: un recurso de otro negocio responde **404, nunca 403**, con ambas credenciales | `T-013` | |
| `T-015` | Migración `operaciones_idempotentes` y dependencia `Idempotency-Key` | `alembic/versions/`, `app/infra/idempotencia.py` | `RN-20`, `RNF-06` | Tests: misma clave y mismo cuerpo devuelve la respuesta guardada; cuerpo distinto responde 409 | `T-014` | |

---

## H2 · Catálogo

Desde aquí, `T-016` a `T-018` son secuenciales; `T-019` a `T-024` pueden repartirse.

| ID | Tarea | Archivos | Cubre | Verificación | Dep. | ∥ |
|---|---|---|---|---|---|---|
| `T-016` | Migración `unidades_medida` y semilla en migración de datos | `alembic/versions/`, `app/modelos/catalogo.py` | `RF-CAT-004`, `RF-INV-009`, `RN-06` | Base recién creada trae las unidades; `GET /unidades-medida` las devuelve | `T-015` | |
| `T-017` | Migración `categorias`, alta, listado y edición | `app/api/v1/categorias.py` | `RF-CAT-005` | Test: nombre repetido en el mismo negocio responde 409; el mismo nombre en otro negocio, no | `T-016` | |
| `T-018` | Migración `productos` con columna generada `tsvector` e índice GIN | `alembic/versions/`, `app/modelos/catalogo.py` | `RF-CAT-001`, `RF-CAT-002`, `RF-CAT-012`, `RF-CAT-013`, `RNF-02` | `EXPLAIN` de la búsqueda usa el índice GIN, no un escaneo secuencial | `T-017` | |
| `T-019` | Alta y edición de producto | `app/api/v1/productos.py`, `app/servicios/productos.py` | `RF-CAT-001`, `RF-CAT-002`, `RF-CAT-010` | Tests: falta nombre o unidad → 422 señalando el campo; SKU repetido → 409; SKU ausente se genera | `T-018` | |
| `T-020` | Migración `codigos_barras`, alta y baja de códigos | `app/api/v1/codigos_barras.py` | `RF-CAT-003`, `RN-05` | Test: asignar un código ya usado responde 409 **indicando a qué producto pertenece** | `T-018` | ∥ |
| `T-021` | Búsqueda por código exacto; 404 que incluye el código consultado en el cuerpo | `app/api/v1/productos.py` | `RF-CAT-008`, `RF-CAT-009`, `RN-14`, `RNF-01` | Test: el 404 trae el código, para que la app precargue el alta sin una segunda llamada. Nada se crea solo | `T-020` | |
| `T-022` | Búsqueda por texto sobre nombre, SKU y categoría, insensible a mayúsculas y tildes | `app/api/v1/productos.py` | `RF-CAT-007`, `RNF-02` | Test: "cuad" encuentra "Cuaderno"; "PAPEL" encuentra "papel"; resultados paginados | `T-018` | ∥ |
| `T-023` | Listado con filtros por categoría, estado y condición de stock | `app/api/v1/productos.py` | `RF-CAT-014` | Test: los tres filtros combinados devuelven el conjunto esperado, paginado por cursor | `T-022` | |
| `T-024` | Archivar y desarchivar producto; borrado prohibido con historial | `app/api/v1/productos.py` | `RF-CAT-011`, `RN-17` | Test: archivado no aparece en búsqueda de operación ni admite movimientos, pero conserva historial | `T-019` | ∥ |

---

## H3 · Movimientos de inventario

El hito con más reglas por línea de código. `T-025` a `T-029` son estrictamente secuenciales.

| ID | Tarea | Archivos | Cubre | Verificación | Dep. | ∥ |
|---|---|---|---|---|---|---|
| `T-025` | Dominio de movimientos: tipos, signo por tipo, validación de cantidad por unidad | `app/dominio/movimientos.py` | `RF-INV-001`, `RF-INV-009`, `RN-07`, `RN-16` | Tests unitarios **sin base de datos**: 2,5 en unidad discreta se rechaza; cantidad ≤ 0 se rechaza; merma es tipo propio | `T-016` | |
| `T-026` | Migración `movimientos` y trigger que rechaza `UPDATE` y `DELETE` salvo `anulado_en` | `alembic/versions/` | `RF-INV-002`, `RF-INV-007`, `RN-02`, `RNF-13` | Test: un `UPDATE` directo por SQL falla; el rastro de autor y momento no se puede borrar | `T-025` | |
| `T-027` | Migración `motivos_movimiento` y semilla, con `otro` y nota obligatoria por tipo | `alembic/versions/` | `RF-INV-010` | Test: motivo fuera de la lista del tipo → 422; `otro` sin nota → 422 | `T-026` | |
| `T-028` | Migración `stock_productos` y función de reconciliación contra la suma de movimientos | `alembic/versions/`, `app/repositorios/stock.py` | `RF-INV-003`, `RF-INV-004`, `RN-01` | Test de reconciliación tras una batería aleatoria de operaciones: instantánea == suma. No existe ningún endpoint ni consulta que fije el stock directamente | `T-026` | |
| `T-029` | Registrar movimiento: transacción con `SELECT … FOR UPDATE`, validación y bloqueo de negativo | `app/servicios/movimientos.py`, `app/api/v1/movimientos.py` | `RF-INV-005`, `RN-03`, `RNF-03` | Test: salida de 5 sobre stock 2 → 409 `STOCK_INSUFICIENTE` con `disponible` y `puede_forzar`, y el stock **no cambia** | `T-028` | |
| `T-030` | Override `forzar: true` con motivo obligatorio y marca de forzado | `app/servicios/movimientos.py` | `RF-INV-006`, `RN-04` | Test: con `forzar` el movimiento se registra, queda marcado y el stock puede ser negativo | `T-029` | |
| `T-031` | Idempotencia aplicada a `POST /movimientos` | `app/api/v1/movimientos.py` | `RF-INV-011`, `RNF-06`, `RN-20` | Test: **5 envíos con la misma clave producen un solo movimiento** | `T-030` | |
| `T-032` | Anulación con contramovimiento que referencia al original | `app/servicios/movimientos.py` | `RF-INV-008`, `RN-02` | Tests: anular crea el inverso y devuelve el stock; anular dos veces → 409; anular un contramovimiento → 409 | `T-031` | |
| `T-033` | Ajuste por conteo físico: el servidor calcula el delta | `app/api/v1/conteos.py` | `RF-INV-013`, `RN-15` | Tests: contar 8 sobre 10 crea ajuste de −2; contar 10 sobre 10 **no crea movimiento** | `T-031` | ∥ |
| `T-034` | Historial paginado con stock resultante y marca de anulado; enlace a la recepción de origen | `app/api/v1/movimientos.py` | `RF-INV-012`, `RF-INV-014`, `RF-REP-004` | Test: orden cronológico inverso, cursor estable, stock resultante correcto en cada fila | `T-032` | |
| `T-035` | Pruebas de concurrencia sobre el mismo producto | `tests/integracion/test_concurrencia.py` | `RF-INV-004`, `RN-03` | 20 salidas simultáneas sobre stock 3: exactamente 3 tienen éxito y el stock nunca queda negativo sin forzar | `T-034` | |

---

## H4 · Compras y proveedores

| ID | Tarea | Archivos | Cubre | Verificación | Dep. | ∥ |
|---|---|---|---|---|---|---|
| `T-036` | Migración `proveedores`, CRUD y archivado | `app/api/v1/proveedores.py` | `RF-COM-001`, `RN-17` | Test: eliminar un proveedor con documentos → 409 ofreciendo archivar; archivado no es seleccionable pero sigue nombrado en su historial | `T-035` | |
| `T-037` | Migración `ordenes_compra` y `ordenes_compra_lineas`; CRUD en borrador | `app/api/v1/ordenes_compra.py` | `RF-COM-002` | Test: líneas editables solo en borrador; cantidad pendiente se **calcula**, no se guarda | `T-036` | |
| `T-038` | Transiciones de estado: emitir, cancelar | `app/servicios/compras.py` | `RF-COM-003`, `RF-COM-010` | Tests: emitida no admite edición de líneas; cancelar con recepciones → 409 | `T-037` | |
| `T-039` | Migración `recepciones` y `recepciones_lineas`; alta en borrador con o sin `orden_id` | `app/api/v1/recepciones.py` | `RF-COM-004`, `RF-COM-005`, `RN-11` | Test: recepción **sin** orden se crea igual de bien que con orden; `orden_id` nulo no es un caso especial | `T-038` | |
| `T-040` | Confirmar recepción: atómica, genera las entradas, congela costo, moneda, tasa y equivalente base, actualiza el costo actual | `app/servicios/recepciones.py` | `RF-COM-006`, `RF-COM-007`, `RF-COM-011`, `RF-INV-014`, `RN-08`, `RN-10`, `RN-13` | Tests: fallo en una línea no deja ninguna entrada; cambiar el costo del producto después no altera la línea congelada; `PATCH /negocio` que cambie `moneda_base` responde 409 en cuanto existe un documento valorizado | `T-039` | |
| `T-041` | Recepción parcial y exceso con confirmación explícita | `app/servicios/recepciones.py` | `RF-COM-008`, `RF-COM-009`, `RN-12` | Tests: recibir 60 de 100 deja *parcialmente recibida* con 40 pendientes; recibir 120 sin confirmar → 409 | `T-040` | |
| `T-042` | Cerrar orden con faltante indicando motivo | `app/servicios/compras.py` | `RF-COM-008`, `RF-COM-003` | Test: cerrada con faltante no admite más recepciones | `T-041` | |
| `T-043` | Inmutabilidad de la recepción confirmada | `app/servicios/recepciones.py` | `RF-COM-012` | Test: `PATCH` sobre confirmada → 409 `RECEPCION_INMUTABLE`; anular sus movimientos la marca corregida sin borrarla | `T-040` | ∥ |
| `T-044` | Listados de órdenes y recepciones con filtros | `app/api/v1/ordenes_compra.py`, `recepciones.py` | `RF-COM-013` | Test: filtros por proveedor, estado y rango de fechas, paginados | `T-042` | ∥ |

---

## H5 · Imágenes y facturas

El almacén tiene **un solo adaptador** (filesystem). El `Protocol` se conserva, y `T-046` es
lo que impide que se vuelva decoración: los tests se escriben contra la interfaz y se
comprueba que ningún servicio importe la implementación concreta. Esa disciplina es lo que
hará barato añadir S3 el día que el backend salga de esta máquina.

| ID | Tarea | Archivos | Cubre | Verificación | Dep. | ∥ |
|---|---|---|---|---|---|---|
| `T-045` | `Protocol AlmacenImagenes` y adaptador de sistema de archivos bajo `IMAGENES_DIR` | `app/almacenamiento/base.py`, `filesystem.py` | `RNF-11` | Guardar, leer y borrar funcionan con claves opacas y aleatorias | `T-035` | ∥ |
| `T-046` | Tests de contrato escritos contra el `Protocol` y comprobación de que ningún servicio importa el adaptador concreto | `tests/integracion/test_almacen.py` | `RNF-11` | Los tests no mencionan `AlmacenFilesystem`; un `import` de la implementación desde `servicios/` hace fallar la comprobación | `T-045` | |
| `T-047` | Migración `imagenes`, subida multipart y validación de límites en el servidor | `app/api/v1/imagenes.py` | `RNF-05`, `RF-CAT-006` | Tests: producto > 300 KB o > 1280 px → 422; factura > 1,5 MB o > 2048 px → 422 | `T-046` | |
| `T-048` | URL de lectura firmada con HMAC y caducidad de 15 minutos, servida por FastAPI | `app/api/v1/imagenes.py` | `RNF-11`, `RNF-16` | Tests: la clave no es adivinable; la URL caduca; nada se depura automáticamente | `T-047` | |
| `T-049` | Migración `facturas` con `ck_facturas_cuadre`; alta | `alembic/versions/`, `app/api/v1/facturas.py` | `RF-FAC-001`, `RF-FAC-003`, `RN-18` | Test: base + impuesto ≠ total → 422 `FACTURA_NO_CUADRA` **mostrando la diferencia**; la base lo rechaza aunque Pydantic no | `T-048` | |
| `T-050` | Unicidad de número por proveedor | `alembic/versions/` | `RF-FAC-002` | Test: número repetido del mismo proveedor → 409 indicando la factura existente; el mismo número de otro proveedor se acepta | `T-049` | |
| `T-051` | Estado de pago y marcar como pagada con fecha | `app/api/v1/facturas.py` | `RF-FAC-004` | Tests: pagar sin fecha → 422; pagada desaparece del filtro de pendientes | `T-049` | ∥ |
| `T-052` | Adjuntar y quitar imágenes de factura | `app/api/v1/facturas.py` | `RF-FAC-005` | Test: varias imágenes por factura, ordenadas | `T-049` | ∥ |
| `T-053` | Vinculación factura ↔ recepciones del mismo proveedor | `app/servicios/facturas.py` | `RF-FAC-006` | Tests: una recepción no puede estar en dos facturas; se admite factura sin recepción | `T-050` | |
| `T-054` | Listado de facturas con filtros y total acumulado del filtro | `app/api/v1/facturas.py` | `RF-FAC-008` | Test: el total corresponde al filtro aplicado, no a la página | `T-051` | ∥ |
| `T-055` | Exportación ZIP con CSV e imágenes nombradas `AAAA-MM-DD_proveedor_numero.jpg` | `app/servicios/exportacion.py` | `RF-FAC-007` | Test: el ZIP abre fuera de la app, el CSV cuadra con las imágenes y no falta ninguna del período | `T-054` | |

---

## H6 · Eventos y reportes

| ID | Tarea | Archivos | Cubre | Verificación | Dep. | ∥ |
|---|---|---|---|---|---|---|
| `T-056` | Dominio de eventos: sobre común y constructores por tipo | `app/dominio/eventos.py` | `RF-INT-002`, `RF-INT-003` | Tests unitarios del sobre y del payload de los 16 tipos de `spec.md` §6 | `T-035` | ∥ |
| `T-057` | Migración `eventos` con `secuencia` BIGSERIAL y escritura en la misma transacción del hecho | `alembic/versions/`, `app/servicios/` | `RF-INT-001`, `RN-21` | Test: si la transacción del movimiento falla, **no queda evento huérfano**, y viceversa | `T-056` | |
| `T-058` | Eventos de stock con antirrebote de bajo mínimo | `app/dominio/stock.py` | `RN-22`, `RF-CAT-012` | Test: bajar de 10 a 8 emite el evento; bajar a 7 y a 6 **no lo repite**; recuperarse y volver a caer sí | `T-057` | |
| `T-059` | Eventos de compras, facturas y discrepancias | `app/servicios/` | `RF-INT-001` | Test: confirmar recepción, registrar factura y forzar un movimiento emiten su evento con el payload de la spec | `T-057` | ∥ |
| `T-060` | `GET /eventos` paginado por `secuencia`, filtrable por tipo y fecha | `app/api/v1/eventos.py` | `RF-INT-004` | Test: un consumidor se pone al día desde `desde_secuencia` sin saltos ni repeticiones | `T-058` | |
| `T-061` | Migración `suscripciones_webhook`, alta, baja y listado. **Sin entrega** | `app/api/v1/webhooks.py` | `RF-INT-005`, `RF-INT-006`, `RF-INT-007` | Test: la suscripción se persiste con el secreto en hash; el contrato figura en OpenAPI y no se dispara ninguna petición saliente | `T-060` | ∥ |
| `T-062` | Reportes de bajo mínimo y agotados, con índice parcial | `app/api/v1/reportes.py` | `RF-REP-001`, `RF-REP-007` | Tests: ordenado por déficit relativo; producto sin mínimo se excluye de bajo mínimo pero aparece en agotados | `T-058` | |
| `T-063` | Reporte de productos sin movimiento, umbral por parámetro | `app/api/v1/reportes.py` | `RF-REP-002` | Test: producto creado hace 10 días **no** aparece con umbral de 90 | `T-062` | ∥ |
| `T-064` | Valorización a costo con desglose por categoría y lista de no valorizables | `app/api/v1/reportes.py` | `RF-REP-003`, `RN-09` | Test: producto con stock y sin costo aparece aparte y **no cuenta como cero**; la valorización usa el costo actual y no admite parámetro de fecha pasada | `T-062` | ∥ |
| `T-065` | Resumen de compras por período, en moneda base con las tasas congeladas | `app/api/v1/reportes.py` | `RF-REP-005` | Test: compras en otra moneda se expresan con la tasa de su recepción, no con la de hoy | `T-064` | |
| `T-066` | Reportes de mermas y de discrepancias (movimientos forzados) | `app/api/v1/reportes.py` | `RF-REP-006`, `RN-04`, `RN-16` | Test: las mermas aparecen separadas de las salidas y valorizadas a costo | `T-065` | ∥ |
| `T-067` | Paginación y acceso con `X-API-Key` en los siete reportes | `app/api/v1/reportes.py` | `RF-REP-008`, `RF-INT-008` | Test parametrizado: los siete reportes responden igual con JWT y con API key | `T-066` | |

---

## H7 · Android — base

Puede arrancar en cuanto H1 esté cerrado; no necesita esperar a H6.

| ID | Tarea | Archivos | Cubre | Verificación | Dep. | ∥ |
|---|---|---|---|---|---|---|
| `T-068` | Proyecto Gradle multimódulo, catálogo de versiones y convenciones de build; `minSdk 24` con desugaring | `settings.gradle.kts`, `build-logic/`, `gradle/libs.versions.toml` | `RNF-14` | `./gradlew assembleDebug` compila; `java.time` funciona en un emulador API 24 | `T-014` | |
| `T-069` | `core:domain` — modelos y reglas en Kotlin puro | `core/domain/**` | `RN-07`, `RN-18`, `RF-INV-009` | El módulo **no declara ninguna dependencia de Android**, verificado en el build; tests JVM pasan | `T-068` | |
| `T-070` | `core:data` — Retrofit, kotlinx.serialization, DTOs y mapeadores a dominio | `core/data/**` | — | Tests de mapeo ida y vuelta; el dinero se deserializa desde cadena, nunca a `Double` | `T-069` | |
| `T-071` | Hilt, interceptor de autenticación y `Authenticator` de renovación de JWT | `core/data/red/**`, `app/di/**` | `RF-AUT-003` | Test: ante un 401 se renueva y se reintenta una sola vez; dos 401 seguidos cierran sesión | `T-070` | |
| `T-072` | Mapeo centralizado de errores de la API a texto en español | `core/common/error/**` | `RNF-07`, `RNF-12` | Test: cada `code` de la API tiene su texto; ningún `message` crudo del servidor llega a la UI | `T-071` | |
| `T-073` | `core:designsystem` con tema, tipografía ≥ 16 sp, área táctil 48 dp y contraste WCAG AA | `core/designsystem/**` | `RNF-08`, `RNF-09` | Test de los componentes compartidos: ninguno permite texto < 16 sp ni objetivo < 48 dp | `T-068` | ∥ |
| `T-074` | Navegación tipada y esqueleto del `NavHost` | `app/navegacion/**` | — | Las rutas son objetos serializables; los argumentos se verifican en compilación | `T-073` | |
| `T-075` | Pantallas de registro e inicio de sesión | `feature/auth/**` | `RF-AUT-001`, `RF-AUT-002` | Prueba manual contra el backend local: registro → sesión iniciada sin volver a autenticar | `T-074` | |

---

## H8 · Android — operación diaria

Es el hito que decide si Marta usa la app o vuelve al cuaderno.

| ID | Tarea | Archivos | Cubre | Verificación | Dep. | ∥ |
|---|---|---|---|---|---|---|
| `T-076` | Room: tabla de bandeja de salida de escrituras pendientes | `core/data/local/**` | `RNF-06` | Test: la clave de idempotencia se genera **al confirmar**, no al enviar, y se persiste con la operación | `T-075` | |
| `T-077` | Reintento de lo pendiente al arrancar, con la misma clave | `core/data/outbox/**` | `RNF-06`, `RNF-07` | Test: matar la app a mitad del envío y reabrir deja **un solo** movimiento en el servidor | `T-076` | |
| `T-078` | Pantalla de escaneo: CameraX + ML Kit empaquetado, antirrebote de 1,5 s, linterna | `feature/escaneo/**` | `RF-CAT-008`, `RNF-10` | Manual: apuntar 5 s a una etiqueta produce **una** lectura; la cámara se libera al salir; linterna funciona | `T-075` | ∥ |
| `T-079` | Permiso de cámara con explicación previa y camino alternativo tecleado | `feature/escaneo/**` | `RNF-15` | Manual: denegar el permiso deja **todos** los flujos completables tecleando el código | `T-078` | |
| `T-080` | Código desconocido → alta precargada con ese código | `feature/catalogo/**` | `RF-CAT-009`, `RN-14` | Manual: escanear un código nuevo abre el alta con el campo relleno; nada se crea solo | `T-079` | |
| `T-081` | Ficha de producto con stock actual destacado | `feature/catalogo/**` | `RF-CAT-008`, `RF-INV-003` | Manual: del escaneo a la ficha en menos de 500 ms con red estable (`RNF-01`) | `T-080` | |
| `T-082` | Alta y edición de producto con captura de foto y compresión en el celular | `feature/catalogo/**` | `RF-CAT-001`, `RF-CAT-006`, `RNF-05` | Test: la imagen sale ≤ 300 KB y ≤ 1280 px antes de subir; el producto se crea aunque la foto siga subiendo | `T-081` | |
| `T-083` | Búsqueda por texto y listado con filtros | `feature/catalogo/**` | `RF-CAT-007`, `RF-CAT-014` | Manual: "cuad" devuelve resultados en menos de 500 ms sobre el catálogo de prueba | `T-081` | ∥ |
| `T-084` | Registrar salida en 3 toques, con diálogo de override ante stock insuficiente | `feature/movimientos/**` | `RF-INV-005`, `RF-INV-006`, `RNF-08` | Manual: **3 toques desde el escaneo**; el 409 se convierte en un diálogo que dice cuánto hay y ofrece forzar | `T-082` | |
| `T-085` | Registrar merma y entrada manual, con la lista cerrada de motivos | `feature/movimientos/**` | `RF-INV-001`, `RF-INV-010` | Manual: "otro" exige nota; los motivos son los de la semilla, sin campo libre de motivo | `T-084` | ∥ |
| `T-086` | Conteo físico: se declara la cantidad contada y se muestra la diferencia antes de confirmar | `feature/movimientos/**` | `RF-INV-013` | Manual: contar 8 sobre 10 muestra −2 antes de confirmar; contar 10 avisa que coincidía | `T-084` | ∥ |
| `T-087` | Historial del producto y anulación con motivo | `feature/movimientos/**` | `RF-INV-012`, `RF-INV-008` | Manual: no existe ninguna forma de editar un movimiento; anulado y contramovimiento se ven juntos | `T-086` | |

---

## H9 · Android — compras, facturas y reportes

| ID | Tarea | Archivos | Cubre | Verificación | Dep. | ∥ |
|---|---|---|---|---|---|---|
| `T-088` | Proveedores: alta, listado, edición y archivado | `feature/compras/**` | `RF-COM-001` | Manual: proveedor con documentos no se puede eliminar, solo archivar | `T-087` | ∥ |
| `T-089` | Órdenes de compra: borrador, emitir, cancelar | `feature/compras/**` | `RF-COM-002`, `RF-COM-003`, `RF-COM-010` | Manual: emitida bloquea la edición de líneas | `T-088` | |
| `T-090` | Recepción directa y contra orden, con avisos de parcial y de exceso | `feature/compras/**` | `RF-COM-004`, `RF-COM-005`, `RF-COM-008`, `RF-COM-009` | Manual: recibir sin orden funciona en el flujo principal; recibir de más pide confirmación explícita | `T-089` | |
| `T-091` | Facturas: registro con base, IVA y total, y captura de la foto del documento | `feature/facturas/**` | `RF-FAC-001`, `RF-FAC-003`, `RF-FAC-005`, `RNF-05` | Manual: el cuadre se valida antes de enviar; la foto sale ≤ 1,5 MB y el número se lee al 100% de zoom | `T-090` | |
| `T-092` | Vinculación a recepciones y marcado de pago | `feature/facturas/**` | `RF-FAC-004`, `RF-FAC-006` | Manual: pagar exige fecha; la factura sale del filtro de pendientes | `T-091` | |
| `T-093` | Listado de facturas y exportación con el menú de compartir de Android | `feature/facturas/**` | `RF-FAC-007`, `RF-FAC-008` | Manual: el ZIP se descarga y se envía por WhatsApp; se abre en un computador sin la app | `T-092` | |
| `T-094` | Pantallas de los siete reportes | `feature/reportes/**` | `RF-REP-001` a `RF-REP-007` | Manual: bajo mínimo ordenado por urgencia; los no valorizables se ven aparte | `T-093` | ∥ |
| `T-095` | Gestión de credenciales de servicio y suscripciones de webhook desde la app | `feature/ajustes/**` | `RF-AUT-005`, `RF-INT-005` | Manual: el secreto se muestra una sola vez y se puede copiar | `T-094` | ∥ |

---

## H10 · Cierre y verificación

Nada de esto es opcional: es donde los requisitos no funcionales dejan de ser una intención y
pasan a tener número.

| ID | Tarea | Archivos | Cubre | Verificación | Dep. | ∥ |
|---|---|---|---|---|---|---|
| `T-096` | Script de semilla: 20.000 productos, 180.000 movimientos, 12 meses de historia | `backend/scripts/semilla.py` | — | El script corre en menos de 5 minutos y deja la base en el volumen de referencia | `T-067` | |
| `T-097` | Medición de p95 de `RNF-01` a `RNF-04`, con umbral que rompe el build | `tests/rendimiento/**` | `RNF-01`, `RNF-02`, `RNF-03`, `RNF-04` | Escaneo < 300 ms, texto < 500 ms, movimiento < 400 ms, reportes < 2 s, sobre la base sembrada | `T-096` | |
| `T-098` | Revisión de paridad API/app: toda capacidad de la app tiene endpoint con `X-API-Key` | `docs/paridad.md` | `RF-INT-008`, `RNF-17` | Tabla firmada capacidad ↔ endpoint, sin huecos, y comprobación de que el **100% de las rutas registradas figura en el documento OpenAPI** con ejemplo de petición y de error | `T-095` | ∥ |
| `T-099` | Verificación de auditoría y retención | `tests/integracion/test_auditoria.py` | `RNF-13`, `RNF-16` | Test: ningún proceso borra registros de auditoría; nada se depura automáticamente | `T-097` | ∥ |
| `T-100` | Revisión de operación con una mano, legibilidad y arranque | `docs/revision_ux.md` | `RNF-08`, `RNF-09`, `RNF-10` | En pantalla de 6": controles al alcance del pulgar, salida en 3 toques, arranque < 3 s y cámara lista < 1,5 s en gama media | `T-094` | ∥ |

---

## Notas de ejecución

**Cadencia.** La implementación corre **de corrido, sin pedir aprobación tarea por tarea**:
son 100 tareas y el trabajo ya está especificado hasta el criterio de verificación. Se reporta
al cerrar cada hito, sin bloquear. Solo se para ante algo que contradiga `spec.md` o
`constitution.md`, una decisión de negocio no cubierta, o un fallo irresoluble dentro de lo
especificado.

**Dónde puede romperse el orden.** H7 (Android base) solo depende de `T-014`, así que puede
arrancar en cuanto exista la autenticación, en paralelo con H2 a H6. Es el corte natural si
hay dos personas: una en el backend por hitos, otra en Android detrás del contrato ya cerrado.

**Las tres tareas que más conviene no apresurar.** `T-029` (bloqueo por fila y stock
negativo), `T-031` (idempotencia) y `T-040` (confirmación atómica de la recepción) son donde
se decide si los números de Marta cuadran. Un fallo ahí no se ve en una demo y se ve en
producción el tercer mes.

**Lo que este backlog no incluye,** por estar fuera del alcance de v1: ventas y POS,
facturación electrónica, múltiples bodegas, empleados y roles, pagos en línea, OCR de
facturas, modo sin conexión completo, valorización a fecha pasada y el **adaptador S3 de
imágenes** — este último es una tarea aislada de media jornada el día que el backend salga de
la máquina local, gracias a que `T-045` y `T-046` mantienen el `Protocol` honesto.
