# Constitución del proyecto — App de Inventario

Principios no negociables. Si `spec.md`, `plan.md` o el código contradicen este documento,
gana este documento. Cambiarlo exige una decisión explícita, registrada aquí y fechada.

## 1. Nombres

- **SQL**: tablas en plural snake_case; columnas snake_case; PK siempre `id` (UUID); FK
  `<entidad>_id`; marcas de tiempo `created_at` / `updated_at`, `timestamptz` en UTC.
  Índices `ix_<tabla>_<columnas>`; restricciones `uq_`, `ck_`, `fk_`.
- **Python**: PEP 8. Anotaciones de tipo obligatorias en toda función pública.
- **Kotlin**: camelCase para funciones y propiedades, PascalCase para tipos y Composables.
- **JSON de la API**: snake_case, idéntico al nombre de la columna cuando existe. No se
  traducen nombres entre capas; un renombrado es un cambio de contrato.
- Los conceptos de **dominio se nombran en español** (`producto`, `movimiento`, `proveedor`,
  `recepcion`, `factura`) para que la spec, la base de datos y el código digan lo mismo. En
  identificadores no se usan tildes ni `ñ`.

## 2. Estilo de la API

- REST sobre HTTP, recursos en plural, versión en la ruta: `/api/v1/productos`. Dentro de
  `v1` no se rompe la compatibilidad; un cambio incompatible es `v2`.
- GET consulta, POST crea, PATCH modifica parcialmente, DELETE solo donde borrar es
  legítimo. Sin verbos en la URL salvo acciones de dominio irreducibles:
  `POST /recepciones/{id}/confirmar`.
- **Toda colección se pagina**, sin excepción, por cursor (`?cursor=&limit=`, `limit` máximo
  100). No existe un endpoint que devuelva "todo".
- **Toda escritura que crea un hecho de negocio acepta `Idempotency-Key`.** Reintentar con
  la misma clave devuelve el mismo resultado, nunca un duplicado.
- Fechas en ISO 8601 UTC. **Dinero**: `NUMERIC(18,4)` en almacenamiento; en transporte, cadena
  decimal más su código ISO 4217 (`{"monto": "12.5000", "moneda": "COP"}`). **Nunca coma
  flotante** para dinero ni para cantidades, y nunca número JSON para un monto: un cliente
  descuidado lo deserializaría a `double`. Ver enmienda **E-01**.
- El esquema OpenAPI se genera del código. No se escribe a mano ni se corrige después.

## 3. Errores

- Formato único: `{"error": {"code": "...", "message": "...", "details": {...}}}`.
  `code` es estable y en MAYUSCULA_SNAKE para que el cliente decida qué hacer; `message` es
  para el dueño del negocio, en español y sin jerga.
- 400 payload malformado · 401 sin credencial · 403 con credencial y sin permiso · 404 no
  existe · **409 conflicto de regla de negocio** (stock insuficiente, factura duplicada) ·
  422 validación semántica · 429 límite de tasa · 5xx culpa nuestra.
- Todo 5xx lleva `X-Request-Id` correlacionado con los logs. Ningún error expone trazas,
  SQL ni nombres internos.
- **Un movimiento no se pierde por un timeout.** Escrituras idempotentes en el servidor y
  reintento en el cliente. Ante un fallo de red la app dice "no se guardó, reintentando";
  nunca muestra un éxito falso ni una pantalla en blanco.

## 4. Datos y migraciones

- PostgreSQL. Todo cambio de esquema es una migración Alembic; `create_all` solo en tests.
- **Una migración aplicada nunca se edita**: se corrige con otra nueva.
- Toda migración tiene `downgrade` funcional y se prueba contra una base con datos, no
  vacía. Un cambio destructivo se despliega en dos pasos: expandir, migrar datos, contraer.
- Toda entidad de negocio lleva `business_id` desde el primer día, indexado, y **toda
  consulta lo filtra**. v1 es mono-negocio; el código no da eso por supuesto.
- Los hechos de negocio son inmutables. Movimientos y eventos no se editan ni se borran:
  se compensan con otro hecho que referencia al original.

## 5. Pruebas

- Backend con pytest. **Cada regla de negocio de `spec.md` tiene al menos un test que la
  nombra por su `RF-`.** Los tests de integración corren contra PostgreSQL real en
  contenedor, nunca contra SQLite.
- Un bug se reproduce primero con un test que falla, y solo después se arregla.
- Android: tests JVM sobre dominio y ViewModels. Instrumentados solo para escaneo y cámara.
- No se persigue un porcentaje de cobertura; se persigue que ningún `RF-` quede sin test.
- La suite completa corre en menos de 5 minutos. Si no, se arregla la suite.

## 6. Definición de "hecho"

Una tarea está hecha cuando cumple su criterio de verificación escrito, sus tests pasan, el
`RF-` que cubre aparece en el mensaje del commit, la API afectada figura correctamente en
OpenAPI, no quedan `TODO` sin issue asociado y ninguna otra prueba se rompió. "Funciona en
mi máquina" no cuenta.

## 7. Trazabilidad

Historia de usuario → `RF-###` → tarea → commit. Ningún requisito sin historia, ninguna
tarea sin requisito, ningún commit de implementación sin requisito. Un hueco de información
se escribe `[PENDIENTE DE DECISIÓN: ...]` y se pregunta. **No se rellena por intuición.**

## 8. Diseño para lo que viene

- **API-first**: todo lo que hace la app móvil se puede hacer por HTTP con una credencial de
  servicio, no solo con sesión de usuario. Si un flujo solo existe dentro de la app, está mal.
- Todo hecho de negocio relevante emite un **evento de dominio persistido**. En v1 solo se
  escribe en base de datos; añadir el transporte no cambiará al productor.
- La lógica de negocio de Android vive en módulos Kotlin sin dependencias de Android.

## 9. Idioma

Documentación, mensajes al usuario, nombres de dominio y commits en español. Palabras clave
del lenguaje, librerías y términos técnicos consolidados, en inglés.

---

## Enmiendas

| ID | Fecha | Cambio | Motivo |
|---|---|---|---|
| **E-01** | 2026-09-01 | §2, representación del dinero: de "entero en la unidad mínima de la moneda" a `NUMERIC(18,4)` en base de datos y cadena decimal más ISO 4217 en la API. | Un costo unitario puede caer **por debajo de la unidad mínima** de la moneda: un tornillo a 12,50 COP. Redondearlo propaga error a la valorización de `RF-REP-003` al multiplicarlo por el stock — 4.000 unidades a 13 en lugar de 12,50 dan 52.000 frente a 50.000 reales, un 4% de desviación. `NUMERIC` es decimal exacto, así que lo que la regla protegía de verdad, **nunca coma flotante**, queda intacto; lo que cambia es la representación. |
