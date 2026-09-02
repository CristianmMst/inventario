# Especificación funcional — App de Inventario v1

Fase 2 · El **qué**, sin tecnología. Rige `constitution.md`.

**Identificadores.** `HU-##` historia de usuario · `RF-AREA-###` requisito funcional ·
`RNF-##` requisito no funcional · `RN-##` regla de negocio · `EV-*` evento de dominio.
Todo `RF-` referencia la historia que lo justifica. Áreas: `AUT` autenticación,
`CAT` catálogo, `INV` movimientos, `COM` compras y proveedores, `FAC` facturas,
`REP` reportes y alertas, `INT` integración.

---

## 1. Personas y contexto de uso

### 1.1 Marta — dueña de la papelería (usuaria única de la v1)

48 años, atiende sola su papelería de barrio. Celular Android de gama media, pantalla de 6",
tres años de uso. Está **de pie detrás del mostrador** y casi siempre tiene la otra mano
ocupada: sostiene la mercancía, cobra, o atiende a alguien. La bodega está al fondo, con
poca luz. El wifi llega mal allí y los datos móviles van y vienen.

No distingue "guardar" de "sincronizar", y no debería tener que hacerlo. Su miedo real no es
perder la app: es que el sistema le diga un número que no coincide con lo que hay en el
estante. El día que eso pase dos veces, deja de usarla y vuelve al cuaderno.

**Lo que impone al diseño.** El escaneo es la forma primaria de identificar un producto y el
texto es el respaldo, nunca al revés. Las acciones frecuentes se alcanzan con el pulgar de
una mano. Registrar una salida no pasa de tres toques después del escaneo. Los errores se
explican en su idioma y siempre dicen qué hacer a continuación. Y ningún movimiento que ella
haya confirmado puede desaparecer porque se cayó la señal.

### 1.2 Don Julio — el contador (usuario indirecto, no abre la app)

Recibe cada mes las facturas de compra del negocio. No entra al sistema: Marta le manda las
imágenes y un listado. Necesita que **la foto sea legible** (número, NIT, fecha y monto se
leen sin ampliar hasta la deformación) y que el listado cuadre con las imágenes. Es la razón
por la que las fotos de factura se comprimen menos que las de producto y por la que existe
una exportación por período.

### 1.3 El agente de automatización (consumidor de la API, aún sin definir)

Un proceso no humano que se autenticará con credencial de servicio y hará por HTTP lo mismo
que hace la app. Todavía no se sabe qué automatizará. Su única exigencia sobre la v1 es que
**no exista ninguna capacidad que solo viva dentro de la app** y que los hechos de negocio
queden registrados como eventos consultables.

### 1.4 Momentos de uso

| Momento | Situación real | Lo que exige del sistema |
|---|---|---|
| Llega el proveedor | De pie, cajas abiertas, el repartidor esperando | Recepción rápida sin orden previa; escanear y teclear cantidad |
| Sale mercancía | Cliente delante, una mano libre | Escanear, cantidad, confirmar. Tres toques |
| Conteo físico | En la bodega, sin señal, celular en una mano | Declarar la cantidad contada, no calcular la diferencia |
| Fin de mes | Sentada, con calma | Reportes, exportar facturas para el contador |
| Reposición | Mirando estantes vacíos | Lista de lo que está bajo mínimo, ordenada por urgencia |

---

## 2. Historias de usuario

Criterios de aceptación en Given / When / Then. Cada historia enumera los `RF-` que la
realizan.

### HU-01 — Abrir mi cuenta y configurar el negocio

*Como dueña quiero crear mi cuenta y registrar mi negocio para empezar a usar la app sin que
nadie tenga que darme de alta.*
Realiza: `RF-AUT-001`, `RF-AUT-002`, `RF-AUT-004`, `RF-AUT-006`

- **Dado** que no tengo cuenta, **cuando** me registro con correo, contraseña y nombre del
  negocio, **entonces** se crean mi usuario y mi negocio, y quedo autenticada sin volver a
  iniciar sesión.
- **Dado** que ya existe una cuenta con mi correo, **cuando** intento registrarme,
  **entonces** el sistema me dice que ese correo ya está registrado y me ofrece iniciar
  sesión, sin revelar nada más.
- **Dado** que creo el negocio, **cuando** elijo su moneda base, **entonces** todos los
  reportes de valorización se expresan en esa moneda y la elección queda fijada.

### HU-02 — Dar de alta un producto escaneando su código

*Como dueña quiero crear un producto escaneando su código de barras para no teclear catorce
dígitos de pie detrás del mostrador.*
Realiza: `RF-CAT-001`, `RF-CAT-002`, `RF-CAT-003`, `RF-CAT-004`, `RF-CAT-006`, `RF-CAT-009`

- **Dado** que escaneo un código que no existe en mi catálogo, **cuando** el sistema no lo
  encuentra, **entonces** me ofrece crear un producto nuevo con ese código ya rellenado.
- **Dado** que estoy creando un producto, **cuando** guardo sin nombre o sin unidad de
  medida, **entonces** el sistema me lo impide y señala el campo que falta.
- **Dado** que escaneo un código ya asignado a otro producto de mi catálogo, **cuando**
  intento asignarlo, **entonces** el sistema lo rechaza y me muestra a qué producto
  pertenece.
- **Dado** que tomo la foto del producto, **cuando** la confirmo, **entonces** se comprime en
  el celular antes de subirse y el producto queda creado aunque la foto aún esté subiendo.

### HU-03 — Encontrar un producto en segundos

*Como dueña quiero encontrar un producto escaneando o escribiendo parte de su nombre para no
perder tiempo con el cliente delante.*
Realiza: `RF-CAT-007`, `RF-CAT-008`, `RF-CAT-009`, `RF-CAT-014`

- **Dado** un catálogo de 20.000 productos, **cuando** escaneo un código existente,
  **entonces** veo la ficha con su stock actual dentro del objetivo de `RNF-01`.
- **Dado** que escribo "cuad" en el buscador, **cuando** hay coincidencias por nombre, SKU o
  categoría, **entonces** veo resultados paginados ordenados por relevancia.
- **Dado** un producto con varios códigos de barras, **cuando** escaneo cualquiera de ellos,
  **entonces** llego al mismo producto.

### HU-04 — Registrar una salida de mercancía

*Como dueña quiero descontar del stock lo que sale para que el número del sistema coincida
con el estante.*
Realiza: `RF-INV-001`, `RF-INV-002`, `RF-INV-005`, `RF-INV-006`, `RF-INV-009`, `RF-INV-011`

- **Dado** un producto con 10 unidades, **cuando** registro una salida de 3, **entonces** el
  stock queda en 7 y el movimiento guarda cantidad, motivo, fecha y quién lo hizo.
- **Dado** un producto con 2 unidades, **cuando** intento una salida de 5, **entonces** el
  sistema la rechaza, me dice que solo hay 2 y no altera el stock.
- **Dado** que el sistema rechazó la salida por stock insuficiente, **cuando** confirmo
  explícitamente que el conteo del sistema está mal, **entonces** el movimiento se registra
  marcado como forzado y el stock puede quedar negativo.
- **Dado** un producto que se mide en unidades discretas, **cuando** intento una salida de
  2,5, **entonces** el sistema lo rechaza.
- **Dado** que confirmé una salida y se cayó la red, **cuando** la app reintenta el envío,
  **entonces** el movimiento queda registrado **una sola vez**.

### HU-05 — Registrar una entrada de mercancía

*Como dueña quiero sumar al stock lo que entra, con su costo, para saber qué tengo y cuánto
me costó.*
Realiza: `RF-INV-001`, `RF-INV-002`, `RF-COM-004`, `RF-COM-006`, `RF-COM-007`, `RF-COM-011`

- **Dado** que llega el proveedor sin aviso previo, **cuando** registro una recepción directa
  con sus líneas, **entonces** se generan automáticamente los movimientos de entrada
  correspondientes.
- **Dado** que registro una línea de recepción con costo unitario y moneda, **cuando**
  confirmo, **entonces** la línea conserva ese costo, su moneda y la tasa de cambio aplicada
  aunque después cambien.
- **Dado** que confirmo la recepción, **cuando** el costo unitario difiere del costo actual
  del producto, **entonces** el costo actual del producto se actualiza.

### HU-06 — Corregir un movimiento equivocado

*Como dueña quiero corregir un error de registro sin que el historial mienta.*
Realiza: `RF-INV-007`, `RF-INV-008`, `RF-INV-010`

- **Dado** un movimiento ya registrado, **cuando** intento editarlo o borrarlo, **entonces**
  el sistema no lo permite y me ofrece anularlo.
- **Dado** que anulo un movimiento indicando el motivo, **cuando** confirmo, **entonces** se
  crea un contramovimiento de igual cantidad y signo contrario que lo referencia, ambos
  quedan visibles en el historial y el stock vuelve al valor previo.
- **Dado** un movimiento ya anulado, **cuando** intento anularlo otra vez, **entonces** el
  sistema lo rechaza.

### HU-07 — Ajustar el stock después de un conteo físico

*Como dueña quiero declarar lo que realmente conté sin tener que calcular la diferencia de
cabeza.*
Realiza: `RF-INV-013`, `RF-INV-010`

- **Dado** un producto con 10 unidades en el sistema, **cuando** declaro que conté 8,
  **entonces** el sistema crea un ajuste de −2 y me muestra la diferencia antes de confirmar.
- **Dado** que registro un ajuste, **cuando** no indico un motivo, **entonces** el sistema no
  me deja confirmarlo.
- **Dado** que declaro exactamente la cantidad que el sistema ya tenía, **cuando** confirmo,
  **entonces** no se crea ningún movimiento y se me informa que el conteo coincidía.

### HU-08 — Registrar una merma

*Como dueña quiero registrar lo que se rompió, venció o se perdió, separado de las ventas,
para saber cuánto me está costando.*
Realiza: `RF-INV-001`, `RF-INV-010`, `RF-REP-006`

- **Dado** un producto dañado, **cuando** registro una merma con su motivo, **entonces** el
  stock baja y el movimiento queda clasificado como merma, no como salida.
- **Dado** un período, **cuando** consulto el reporte de mermas, **entonces** aparecen
  separadas de las salidas y valorizadas a costo.

### HU-09 — Llevar mis proveedores

*Como dueña quiero tener los datos de mis proveedores a mano para no buscar el número en el
celular.*
Realiza: `RF-COM-001`

- **Dado** un proveedor nuevo, **cuando** lo registro con nombre y datos de contacto,
  **entonces** queda disponible para recepciones, órdenes y facturas.
- **Dado** un proveedor con recepciones registradas, **cuando** intento eliminarlo,
  **entonces** el sistema no lo permite y me ofrece archivarlo.
- **Dado** un proveedor archivado, **cuando** creo una recepción, **entonces** no aparece
  entre los seleccionables, pero sus documentos históricos siguen mostrando su nombre.

### HU-10 — Planificar una compra

*Como dueña quiero anotar lo que voy a pedir para no olvidarme de la mitad cuando llame al
proveedor.*
Realiza: `RF-COM-002`, `RF-COM-003`, `RF-COM-010`

- **Dado** que voy a pedir mercancía, **cuando** creo una orden de compra con sus líneas,
  **entonces** queda en estado borrador y puedo modificarla.
- **Dado** una orden en borrador, **cuando** la emito, **entonces** pasa a estado emitida y
  sus líneas ya no se modifican.
- **Dado** una orden emitida sin recepciones, **cuando** la cancelo indicando el motivo,
  **entonces** pasa a cancelada y no admite recepciones.

### HU-11 — Recibir contra una orden de compra

*Como dueña quiero recibir contra lo que pedí y ver qué quedó pendiente.*
Realiza: `RF-COM-005`, `RF-COM-006`, `RF-COM-008`, `RF-COM-009`, `RF-COM-012`

- **Dado** una orden emitida de 100 unidades, **cuando** recibo 60, **entonces** entran 60 al
  stock, la orden queda **parcialmente recibida** y muestra 40 pendientes.
- **Dado** esa misma orden, **cuando** después recibo las 40 restantes, **entonces** la orden
  pasa a recibida completa.
- **Dado** una orden de 100, **cuando** intento recibir 120, **entonces** el sistema me avisa
  del exceso y solo lo acepta si lo confirmo explícitamente.
- **Dado** una orden parcialmente recibida que ya no llegará completa, **cuando** la cierro
  indicando el motivo, **entonces** queda cerrada con faltante y no admite más recepciones.
- **Dado** una recepción confirmada, **cuando** intento modificarla, **entonces** el sistema
  no lo permite: solo puedo anular sus movimientos.

### HU-12 — Registrar la factura del proveedor

*Como dueña quiero registrar la factura con su foto para no perder el papel y saber qué debo.*
Realiza: `RF-FAC-001` … `RF-FAC-006`

- **Dado** que recibí mercancía, **cuando** registro la factura con número, proveedor, fecha,
  base gravable, IVA y total, **entonces** queda registrada y vinculada a esa recepción.
- **Dado** que la base y el IVA no suman el total, **cuando** intento guardar, **entonces**
  el sistema lo rechaza y muestra la diferencia.
- **Dado** una factura ya registrada de ese proveedor con el mismo número, **cuando** intento
  registrarla otra vez, **entonces** el sistema la rechaza como duplicada.
- **Dado** que adjunto la foto de la factura, **cuando** la confirmo, **entonces** se guarda
  con calidad suficiente para leer número, fecha y monto sin ampliar.
- **Dado** una factura pendiente de pago, **cuando** la marco como pagada con su fecha,
  **entonces** deja de aparecer en el listado de pendientes.

### HU-13 — Saber qué me falta

*Como dueña quiero ver de un vistazo qué está por acabarse para no quedarme sin lo que más
vendo.*
Realiza: `RF-REP-001`, `RF-CAT-012`, `RF-INT-002`

- **Dado** un producto con stock mínimo 10, **cuando** su stock cae a 8, **entonces** aparece
  en la lista de bajo mínimo y se registra el hecho como evento.
- **Dado** un producto ya listado como bajo mínimo, **cuando** su stock sigue bajando,
  **entonces** el evento no se vuelve a emitir hasta que el stock se recupere por encima del
  mínimo y vuelva a caer.
- **Dado** un producto sin stock mínimo definido, **cuando** se agota, **entonces** no
  aparece en el reporte de bajo mínimo pero sí en el de agotados.

### HU-14 — Saber qué no se mueve

*Como dueña quiero ver qué llevo meses sin tocar para no seguir comprando lo que nadie
compra.*
Realiza: `RF-REP-002`

- **Dado** un umbral de 90 días, **cuando** consulto el reporte, **entonces** veo los
  productos sin movimientos en ese período, con su stock y su valor a costo.
- **Dado** un producto creado hace 10 días y sin movimientos, **cuando** consulto con umbral
  de 90 días, **entonces** no aparece: no ha tenido tiempo de moverse.

### HU-15 — Saber cuánto vale mi inventario

*Como dueña quiero saber cuánta plata tengo parada en el estante.*
Realiza: `RF-REP-003`

- **Dado** mi catálogo, **cuando** consulto la valorización, **entonces** veo el total a
  costo en la moneda base del negocio y el desglose por categoría.
- **Dado** un producto sin costo registrado, **cuando** consulto la valorización,
  **entonces** aparece listado aparte como no valorizable y **no** se cuenta como cero.

### HU-16 — Ver la historia de un producto

*Como dueña quiero ver todo lo que pasó con un producto para entender por qué el número es el
que es.*
Realiza: `RF-REP-004`, `RF-INV-012`

- **Dado** un producto, **cuando** abro su historial, **entonces** veo sus movimientos en
  orden cronológico inverso, paginados, cada uno con tipo, cantidad, motivo, origen y el
  stock resultante.
- **Dado** un movimiento anulado, **cuando** lo veo en el historial, **entonces** aparece
  marcado como anulado junto a su contramovimiento.

### HU-17 — Ver cuánto compré en el mes

*Como dueña quiero el resumen de compras del período para saber en qué se me fue la plata.*
Realiza: `RF-REP-005`

- **Dado** un rango de fechas, **cuando** consulto el resumen de compras, **entonces** veo el
  total comprado en moneda base, el desglose por proveedor y el desglose por categoría.
- **Dado** compras registradas en otra moneda, **cuando** consulto el resumen, **entonces**
  los montos se expresan en moneda base usando la tasa congelada en cada recepción.

### HU-18 — Entregarle las facturas al contador

*Como dueña quiero mandarle al contador las facturas del mes sin buscar papeles.*
Realiza: `RF-FAC-007`, `RF-FAC-008`

- **Dado** un período, **cuando** solicito la exportación de facturas, **entonces** obtengo
  el listado con sus datos y el acceso a las imágenes de ese período.
- **Dado** una imagen exportada, **cuando** el contador la abre, **entonces** número, fecha,
  NIT y monto se leen sin ampliar más allá del tamaño original.

### HU-19 — Que no se pierda nada con mala señal

*Como dueña quiero estar segura de que lo que confirmé quedó guardado, aunque el celular esté
peleando con la señal.*
Realiza: `RNF-06`, `RNF-07`, `RF-INV-011`

- **Dado** que confirmo un movimiento y la petición expira por tiempo, **cuando** la app
  reintenta, **entonces** el movimiento queda registrado exactamente una vez.
- **Dado** que no hay red, **cuando** confirmo un movimiento, **entonces** la app me dice con
  claridad que **no** se guardó y qué va a hacer, y nunca muestra un éxito falso.
- **Dado** un fallo del servidor, **cuando** la app lo recibe, **entonces** me muestra un
  mensaje comprensible y un identificador que sirve para reportarlo.

### HU-20 — Dejar lista la automatización

*Como dueña quiero poder conectar más adelante una herramienta que haga cosas por mí sin que
haya que reescribir la app.*
Realiza: `RF-INT-001` … `RF-INT-008`, `RF-AUT-005`

- **Dado** un hecho de negocio relevante, **cuando** ocurre, **entonces** queda registrado un
  evento de dominio con su tipo, su momento y su payload.
- **Dado** una credencial de servicio, **cuando** un proceso externo llama a la API,
  **entonces** puede hacer todo lo que hace la app, limitado al mismo negocio.
- **Dado** cualquier capacidad de la app móvil, **cuando** se revisa el contrato de la API,
  **entonces** existe un endpoint que la cubre.

---

## 3. Requisitos funcionales

### 3.1 Autenticación y negocio (`AUT`)

| ID | Requisito | HU |
|---|---|---|
| `RF-AUT-001` | Registro self-service con correo, contraseña y nombre del negocio. En una sola operación se crean el usuario y el negocio, y el usuario queda asociado a él. El correo es único en el sistema. | HU-01 |
| `RF-AUT-002` | Inicio de sesión que devuelve una credencial de acceso de vida corta y una de renovación de vida larga. La de renovación puede revocarse. | HU-01 |
| `RF-AUT-003` | Renovación de la credencial de acceso sin volver a pedir la contraseña. Sesión útil en el celular sin reautenticación durante al menos 30 días de uso regular. | HU-01 |
| `RF-AUT-004` | El negocio tiene nombre, moneda base (ISO 4217) y zona horaria. La moneda base se elige al crear el negocio y **no se puede cambiar** una vez existan movimientos valorizados. | HU-01, HU-15 |
| `RF-AUT-005` | Credenciales de servicio (API key) emitidas por el dueño, con nombre, fecha de creación, último uso y revocación. Autorizan las mismas operaciones que la sesión de usuario, limitadas a un único negocio. El secreto se muestra una sola vez. | HU-20 |
| `RF-AUT-006` | Cambio de contraseña por el propio usuario, que revoca las credenciales de renovación existentes. | HU-01 |
| `RF-AUT-007` | Toda operación se resuelve dentro del negocio de la credencial. Un recurso de otro negocio responde "no existe", nunca "no autorizado". | HU-01, HU-20 |

### 3.2 Catálogo (`CAT`)

| ID | Requisito | HU |
|---|---|---|
| `RF-CAT-001` | Alta de producto con: nombre (obligatorio), SKU, categoría, unidad de medida (obligatoria), costo actual, precio de venta, stock mínimo, foto y estado. | HU-02 |
| `RF-CAT-002` | El SKU, si se informa, es único dentro del negocio. Si no se informa, el sistema genera uno. | HU-02 |
| `RF-CAT-003` | Un producto admite **cero o más códigos de barras**. Cada código es único dentro del negocio y apunta a un solo producto. Se pueden añadir y quitar códigos después del alta. | HU-02, HU-03 |
| `RF-CAT-004` | La unidad de medida declara si es **discreta** (unidad, caja, paquete) o **continua** (kg, g, m, L). Determina la validación de cantidades de `RF-INV-009`. La unidad no se puede cambiar si el producto tiene movimientos. | HU-02, HU-04 |
| `RF-CAT-005` | Categorías propias del negocio, planas (sin jerarquía), con nombre único. Un producto pertenece como mucho a una. | HU-02, HU-15 |
| `RF-CAT-006` | Un producto admite una foto. Se sustituye reemplazándola; la anterior deja de estar accesible. | HU-02 |
| `RF-CAT-007` | Búsqueda por texto sobre nombre, SKU y categoría, tolerante a mayúsculas y tildes, con coincidencia parcial y resultados paginados por relevancia. | HU-03 |
| `RF-CAT-008` | Búsqueda por código de barras exacto, que devuelve el producto y su stock actual. | HU-03 |
| `RF-CAT-009` | Cuando un código escaneado no corresponde a ningún producto del negocio, el sistema lo indica explícitamente y ofrece **crear un producto nuevo con ese código precargado**. No se crea nada automáticamente. | HU-02, HU-03 |
| `RF-CAT-010` | Edición de los datos del producto. Los cambios de costo y precio **sobrescriben** el valor actual (ver `RN-09`). | HU-02 |
| `RF-CAT-011` | Un producto **no se borra** si tiene movimientos: se archiva. Un producto archivado no aparece en búsquedas de operación ni admite movimientos nuevos, pero conserva su historial y aparece en reportes históricos. Se puede desarchivar. | HU-02 |
| `RF-CAT-012` | Stock mínimo opcional por producto, expresado en su unidad de medida. Alimenta `RF-REP-001` y `EV-stock.bajo_minimo`. | HU-13 |
| `RF-CAT-013` | El producto expone su **costo actual** y su **precio de venta actual**, ambos en la moneda base del negocio. Sin historial (ver `RN-09`). | HU-15 |
| `RF-CAT-014` | Listado de productos paginado, con filtro por categoría, por estado (activo/archivado) y por condición de stock (bajo mínimo, agotado, con stock). | HU-03, HU-13 |

### 3.3 Movimientos de inventario (`INV`)

| ID | Requisito | HU |
|---|---|---|
| `RF-INV-001` | Tipos de movimiento: **entrada**, **salida**, **ajuste**, **merma** y **contramovimiento**. El tipo determina el signo y no se deduce de la cantidad. | HU-04, HU-05, HU-07, HU-08 |
| `RF-INV-002` | Todo movimiento registra: producto, tipo, cantidad, motivo, nota libre opcional, momento, autor (usuario o credencial de servicio) y origen (app, API, recepción). | HU-04, HU-05 |
| `RF-INV-003` | El stock actual de un producto es **siempre** la suma de sus movimientos. No existe ninguna operación que fije el stock a un valor directamente; un conteo físico se expresa como ajuste (`RF-INV-013`). | HU-04, HU-07 |
| `RF-INV-004` | El stock consultado y el stock derivado de los movimientos coinciden en todo momento, incluso ante registros concurrentes sobre el mismo producto. | HU-04 |
| `RF-INV-005` | Una salida o merma que dejaría el stock por debajo de cero se **rechaza** como conflicto de negocio, informando el stock disponible en el momento del rechazo. | HU-04 |
| `RF-INV-006` | El rechazo de `RF-INV-005` se puede **forzar** con una confirmación explícita del usuario. El movimiento resultante queda marcado como forzado, exige motivo, y el stock puede quedar negativo. Los movimientos forzados son consultables como reporte de discrepancias. | HU-04 |
| `RF-INV-007` | Un movimiento registrado **no se modifica ni se borra**, por ningún medio, ni por la app ni por la API. | HU-06 |
| `RF-INV-008` | Anulación de un movimiento: crea un **contramovimiento** de igual cantidad y signo contrario que referencia al original, exige motivo, y marca el original como anulado. Un movimiento anulado no se puede volver a anular. Un contramovimiento no se puede anular. | HU-06 |
| `RF-INV-009` | La cantidad es siempre mayor que cero. En productos de unidad **discreta** debe ser entera; en unidad **continua** admite hasta 3 decimales. | HU-04 |
| `RF-INV-010` | Todo movimiento exige un motivo tomado de una **lista cerrada por tipo**, más una nota libre opcional. La lista es fija (semilla del sistema, sin administración por el usuario) e incluye siempre **"otro"**, que obliga a escribir la nota. Ajustes, mermas, movimientos forzados y anulaciones exigen además que la nota no esté vacía. Lista: **entrada** — recepción de compra (la pone el sistema), carga inicial; **salida** — venta, consumo interno; **merma** — rotura, vencimiento, robo, pérdida; **ajuste** — conteo físico, corrección de carga; **contramovimiento** — anulación. | HU-06, HU-07, HU-08 |
| `RF-INV-011` | El registro de un movimiento es **idempotente**: reenviar la misma operación con la misma clave de idempotencia devuelve el movimiento ya creado, sin duplicarlo. | HU-04, HU-19 |
| `RF-INV-012` | Historial de movimientos de un producto, paginado, en orden cronológico inverso, con el stock resultante después de cada movimiento y la marca de anulado cuando corresponda. | HU-16 |
| `RF-INV-013` | Ajuste por conteo físico: el usuario declara la **cantidad contada**; el sistema calcula la diferencia contra el stock actual, la muestra antes de confirmar y registra un ajuste por esa diferencia. Si la diferencia es cero no se crea movimiento. | HU-07 |
| `RF-INV-014` | Los movimientos generados por una recepción quedan enlazados a ella y a su línea de origen, y son identificables como tales en el historial. | HU-05, HU-16 |

### 3.4 Compras y proveedores (`COM`)

| ID | Requisito | HU |
|---|---|---|
| `RF-COM-001` | Alta, consulta, edición y archivado de proveedores: nombre (obligatorio), identificación fiscal, contacto, teléfono, correo, dirección y notas. Un proveedor con documentos asociados no se borra, se archiva. | HU-09 |
| `RF-COM-002` | Orden de compra **opcional**, con proveedor, fecha esperada, moneda, notas y líneas (producto, cantidad, costo unitario estimado). | HU-10 |
| `RF-COM-003` | Estados de la orden: **borrador** → **emitida** → **parcialmente recibida** → **recibida** \| **cerrada con faltante** \| **cancelada**. Solo en borrador se editan las líneas. Solo desde emitida o parcialmente recibida se puede recibir. | HU-10, HU-11 |
| `RF-COM-004` | **Recepción directa sin orden de compra**: se registra contra un proveedor con sus líneas y no requiere ninguna orden previa. | HU-05 |
| `RF-COM-005` | Recepción contra una orden de compra, total o parcial, indicando por línea la cantidad efectivamente recibida. Una orden admite varias recepciones. | HU-11 |
| `RF-COM-006` | Confirmar una recepción genera automáticamente **un movimiento de entrada por cada línea**, en la misma operación: o se registran todos o no se registra ninguno. | HU-05, HU-11 |
| `RF-COM-007` | Cada línea de recepción **congela**: costo unitario, moneda del costo, tasa de cambio aplicada y costo unitario equivalente en moneda base. Estos valores no cambian nunca después de confirmar. | HU-05, HU-17 |
| `RF-COM-008` | Recibir **menos** de lo ordenado deja la orden en *parcialmente recibida*, con la cantidad pendiente por línea visible. La orden puede cerrarse con faltante indicando el motivo, y entonces no admite más recepciones. | HU-11 |
| `RF-COM-009` | Recibir **más** de lo ordenado se advierte al usuario y solo se acepta con confirmación explícita. El exceso queda registrado en la línea de recepción. | HU-11 |
| `RF-COM-010` | Una orden emitida sin recepciones puede cancelarse indicando el motivo. Una orden con recepciones no se cancela: se cierra con faltante. | HU-10 |
| `RF-COM-011` | Al confirmar una recepción, el **costo actual** de cada producto recibido se actualiza con el costo unitario en moneda base de esa línea: **último costo recibido**, no promedio ponderado. | HU-05, HU-15 |
| `RF-COM-012` | Una recepción confirmada es **inmutable**. Un error se corrige anulando sus movimientos (`RF-INV-008`), lo que marca la recepción como corregida sin borrarla. | HU-11 |
| `RF-COM-013` | Listado de recepciones y de órdenes, paginado, con filtro por proveedor, estado y rango de fechas. | HU-11, HU-17 |

### 3.5 Facturas de compra (`FAC`)

| ID | Requisito | HU |
|---|---|---|
| `RF-FAC-001` | Registro de factura **de compra** con: proveedor, número, fecha de emisión, fecha de vencimiento opcional, moneda, base gravable, impuesto, total, estado de pago y notas. La v1 **no emite** facturas de venta. | HU-12 |
| `RF-FAC-002` | El número de factura es único **por proveedor** dentro del negocio. Un duplicado se rechaza indicando la factura existente. | HU-12 |
| `RF-FAC-003` | Los importes se registran como base gravable + impuesto = total, y la suma debe cuadrar exactamente. Si la moneda no es la base del negocio, se registra la tasa de cambio y el total equivalente en moneda base. | HU-12, HU-17 |
| `RF-FAC-004` | Estado de pago: **pendiente**, **pagada** o **anulada**. Marcar como pagada exige fecha de pago. | HU-12 |
| `RF-FAC-005` | Una factura admite **una o varias imágenes** del documento, conservadas con calidad de lectura (`RNF-05`). | HU-12, HU-18 |
| `RF-FAC-006` | Una factura puede vincularse a **cero o más recepciones** del mismo proveedor, y una recepción puede aparecer en una sola factura. La vinculación es opcional: se puede registrar una factura sin recepción asociada. | HU-12 |
| `RF-FAC-007` | Exportación de facturas por rango de fechas como **un archivo ZIP** que contiene un CSV con todos los datos registrados y las imágenes del período, nombradas `AAAA-MM-DD_proveedor_numero.jpg`. Autocontenido y sin caducidad: el contador lo abre sin la app y sin conexión. La app lo descarga y lo entrega por el menú de compartir del sistema. | HU-18 |
| `RF-FAC-008` | Listado de facturas paginado, con filtro por proveedor, estado de pago y rango de fechas, y total acumulado del filtro aplicado. | HU-12, HU-18 |

### 3.6 Reportes y alertas (`REP`)

| ID | Requisito | HU |
|---|---|---|
| `RF-REP-001` | **Bajo stock mínimo**: productos activos cuyo stock actual es menor o igual a su stock mínimo, ordenados por criticidad (déficit relativo al mínimo), con stock actual, mínimo y déficit. Los productos sin mínimo definido se excluyen. | HU-13 |
| `RF-REP-002` | **Sin movimiento**: productos activos sin movimientos en los últimos N días (N parametrizable, 90 por defecto), excluyendo los creados dentro de ese mismo período, con su stock y su valor a costo. | HU-14 |
| `RF-REP-003` | **Valorización del inventario a costo**: suma de stock × costo actual en moneda base, con desglose por categoría. Los productos con stock pero sin costo se listan aparte como **no valorizables** y no se computan como cero. | HU-15 |
| `RF-REP-004` | **Historial de movimientos por producto** (ver `RF-INV-012`), exportable. | HU-16 |
| `RF-REP-005` | **Resumen de compras por período**: total recibido y total facturado en moneda base, con desglose por proveedor y por categoría, para un rango de fechas. | HU-17 |
| `RF-REP-006` | **Mermas por período**: cantidad y valor a costo de las mermas del rango, con desglose por motivo y por producto. | HU-08 |
| `RF-REP-007` | **Agotados**: productos activos con stock igual o menor que cero, independientemente de si tienen mínimo definido. | HU-13 |
| `RF-REP-008` | Todo reporte es consultable por la API con los mismos parámetros que usa la app y devuelve datos paginados cuando la colección puede crecer. | HU-20 |

### 3.7 Integración y eventos (`INT`)

| ID | Requisito | HU |
|---|---|---|
| `RF-INT-001` | Todo hecho de negocio del catálogo de la sección 6 genera un **evento de dominio persistido**, en la misma transacción que el hecho que lo origina: si el hecho se registra, el evento existe; si falla, no queda evento huérfano. | HU-20 |
| `RF-INT-002` | El catálogo de eventos de la sección 6 es parte del contrato. Añadir un evento no rompe compatibilidad; cambiar el significado o quitar un campo de un payload existente, sí. | HU-13, HU-20 |
| `RF-INT-003` | Todo evento comparte el mismo sobre: identificador único, tipo, versión del payload, negocio, momento de ocurrencia, autor y payload propio del tipo. | HU-20 |
| `RF-INT-004` | Los eventos son consultables por la API, paginados, filtrables por tipo y rango de fechas, en orden de ocurrencia, para que un consumidor externo pueda ponerse al día desde un punto conocido. | HU-20 |
| `RF-INT-005` | Contrato de **webhooks salientes**: suscripciones con URL de destino, lista de tipos de evento y secreto de firma; alta, baja y listado. **En v1 se define el contrato y se persisten las suscripciones; no se implementa la entrega.** | HU-20 |
| `RF-INT-006` | Cada entrega de webhook irá firmada con HMAC sobre el cuerpo usando el secreto de la suscripción, con el momento de firma incluido, para que el receptor verifique origen e integridad. Definido a nivel de contrato en v1. | HU-20 |
| `RF-INT-007` | La entrega de webhooks será **al menos una vez**, con reintentos y espera creciente. Cada entrega lleva el identificador del evento para que el receptor descarte duplicados. Definido a nivel de contrato en v1. | HU-20 |
| `RF-INT-008` | **Paridad API/app**: no existe ninguna capacidad de la app móvil sin endpoint equivalente accesible con credencial de servicio. Es un criterio de revisión de cada tarea, no solo un principio. | HU-20 |

---

## 4. Requisitos no funcionales

Todos verificables. La escala de referencia es **20.000 productos y 15.000 movimientos al
mes** por negocio, con al menos 12 meses de historia acumulada.

| ID | Requisito |
|---|---|
| `RNF-01` | **Búsqueda por código de barras**: la API responde en menos de **300 ms** en el percentil 95, con 20.000 productos y 180.000 movimientos en la base. En el celular, desde que el escáner reconoce el código hasta que se ve la ficha, menos de **500 ms** con red estable. |
| `RNF-02` | **Búsqueda por texto**: menos de **500 ms** en el percentil 95 con el mismo volumen, devolviendo la primera página. |
| `RNF-03` | **Registro de un movimiento**: menos de **400 ms** en el percentil 95, incluida la validación de stock y la escritura del evento. |
| `RNF-04` | **Reportes**: valorización, bajo mínimo y resumen de compras responden en menos de **2 s** en el percentil 95 con el volumen de referencia. |
| `RNF-05` | **Imágenes.** Producto: lado mayor máximo 1280 px, **≤ 300 KB**, JPEG con calidad ~80. Factura: lado mayor máximo 2048 px, **≤ 1,5 MB**, calidad ~85, sin recorte automático — debe permitir leer número, fecha y monto al 100% de zoom. La compresión ocurre **en el celular antes de subir**. El servidor rechaza lo que exceda estos límites. |
| `RNF-06` | **Ningún movimiento confirmado se pierde por un fallo de red.** Toda escritura de negocio es idempotente y reintentable; un reintento nunca duplica. Verificable enviando la misma operación 5 veces con la misma clave y comprobando que existe un solo movimiento. |
| `RNF-07` | **Honestidad ante el error.** La app nunca muestra éxito sin confirmación del servidor. Todo error visible dice qué pasó y qué hacer, en español y sin jerga, y los de servidor incluyen un identificador reportable. |
| `RNF-08` | **Operación con una mano.** En las pantallas de escaneo, registro de movimiento y recepción, todos los controles de acción están dentro del alcance del pulgar en una pantalla de 6". Registrar una salida no requiere más de **3 toques** después del escaneo. Área táctil mínima 48×48 dp. |
| `RNF-09` | **Legibilidad de pie.** Texto de contenido no menor a 16 sp; el stock actual y las cantidades, destacados. Contraste conforme a WCAG AA. La app es utilizable con el brillo al mínimo en interior. |
| `RNF-10` | **Arranque y escaneo.** La app abre y es utilizable en menos de **3 s** en un dispositivo de gama media con Android 8. La cámara de escaneo queda lista en menos de **1,5 s** desde que se abre la pantalla. |
| `RNF-11` | **Seguridad.** Contraseñas con hash de derivación lenta y salt. Transporte siempre cifrado. Credenciales de servicio almacenadas con hash, mostradas una sola vez. Todo dato aislado por negocio (`RF-AUT-007`). Las URLs de imágenes no son adivinables y caducan. |
| `RNF-12` | **Privacidad de errores.** Ningún mensaje de error expone trazas, consultas SQL, nombres internos ni la existencia de recursos de otro negocio. |
| `RNF-13` | **Auditoría.** Todo movimiento, recepción, factura y evento conserva autor y momento. Ningún proceso automático puede borrar registros de auditoría. |
| `RNF-14` | **Compatibilidad Android.** `minSdk 24` (Android 7.0). Funciona correctamente en pantalla de 5" y en dispositivos con 2 GB de RAM. |
| `RNF-15` | **Permisos.** La cámara se pide en el momento en que se va a usar, con explicación previa. Si se deniega, todos los flujos siguen siendo completables tecleando el código a mano. |
| `RNF-16` | **Retención.** El historial de movimientos y las imágenes de factura se conservan al menos **5 años**. Nada se depura automáticamente en v1. |
| `RNF-17` | **Contrato documentado.** El 100% de los endpoints figura en el documento OpenAPI publicado, con ejemplos de petición y de respuesta de error. |

---

## 5. Reglas de negocio

Reglas explícitas y no negociables. Cada una tiene al menos un test que la nombra.

| ID | Regla |
|---|---|
| `RN-01` | **El stock es derivado.** El stock actual es la suma de los movimientos del producto. No existe forma de fijarlo directamente. |
| `RN-02` | **Los movimientos son inmutables.** No se editan ni se borran. Se corrigen con un contramovimiento que referencia al original y exige motivo escrito. |
| `RN-03` | **El stock negativo está bloqueado por defecto.** Una salida o merma que lo produciría se rechaza informando el stock disponible. |
| `RN-04` | **El bloqueo se puede forzar.** Con confirmación explícita del usuario, el movimiento se registra marcado como forzado y con motivo obligatorio, y el stock puede quedar negativo. Los movimientos forzados son un reporte de discrepancias a resolver, no un estado normal. |
| `RN-05` | **Un código de barras pertenece a un solo producto** dentro del negocio. Un producto puede tener varios. |
| `RN-06` | **Una sola unidad de stock por producto.** No hay presentaciones ni factores de conversión: si la caja y la unidad se cuentan por separado, son dos productos. |
| `RN-07` | **Las cantidades respetan la unidad.** Unidades discretas exigen enteros; unidades continuas admiten hasta 3 decimales. Toda cantidad es mayor que cero; el sentido lo da el tipo de movimiento, nunca el signo. |
| `RN-08` | **La recepción es la fuente del costo histórico.** Cada línea de recepción congela costo unitario, moneda, tasa de cambio y equivalente en moneda base, y esos valores no cambian jamás. |
| `RN-09` | **El producto guarda solo el costo y el precio actuales.** No hay historial de precios: la historia del costo vive en las recepciones (`RN-08`) y la valorización usa el costo actual. Por tanto una valorización a fecha pasada **no** es un requisito de la v1. |
| `RN-10` | **La moneda base del negocio no cambia** una vez existan movimientos valorizados. Los documentos en otra moneda guardan su tasa y su equivalente en moneda base al momento de confirmarse. |
| `RN-11` | **La recepción no requiere orden de compra.** Las órdenes son una herramienta de planificación opcional. |
| `RN-12` | **Recibir menos de lo ordenado** deja la orden parcialmente recibida con el faltante visible; puede cerrarse con faltante indicando el motivo. **Recibir más** exige confirmación explícita. |
| `RN-13` | **Confirmar una recepción genera sus entradas atómicamente.** O entran todas las líneas o no entra ninguna. Una recepción confirmada es inmutable. |
| `RN-14` | **Un código escaneado desconocido nunca crea nada automáticamente.** El sistema lo informa y ofrece el alta con el código precargado. |
| `RN-15` | **Un ajuste se declara por cantidad contada**, no por diferencia. El sistema calcula el delta, lo muestra y lo registra. Delta cero no crea movimiento. |
| `RN-16` | **La merma es un tipo propio**, distinto de la salida, y se reporta por separado. |
| `RN-17` | **Nada que tenga historia se borra.** Productos y proveedores con documentos asociados se archivan. |
| `RN-18` | **El número de factura es único por proveedor**, y base + impuesto debe igualar exactamente el total. |
| `RN-19` | **Toda entidad de negocio pertenece a un negocio** y toda consulta se filtra por él, aunque la v1 sea mono-negocio. |
| `RN-20` | **Las escrituras de negocio son idempotentes.** El mismo hecho enviado dos veces con la misma clave produce un solo registro. |
| `RN-21` | **Todo hecho de negocio emite su evento en la misma transacción.** No hay hecho sin evento ni evento sin hecho. |
| `RN-22` | **La alerta de bajo mínimo no se repite.** El evento se emite en la transición de "por encima del mínimo" a "en o por debajo del mínimo", y no vuelve a emitirse hasta que el stock se recupere por encima. |

---

## 6. Eventos de dominio

Todos los eventos comparten el mismo sobre (`RF-INT-003`):

```
id             identificador único del evento
tipo           nombre del evento, p. ej. "stock.bajo_minimo"
version        versión del payload, entera, empieza en 1
business_id    negocio al que pertenece
ocurrido_en    momento del hecho, UTC
autor          { tipo: "usuario" | "servicio", id, nombre }
payload        contenido propio del tipo
```

| Evento | Se emite cuando | Payload |
|---|---|---|
| `EV-producto.creado` | Se da de alta un producto | `producto_id`, `nombre`, `sku`, `categoria`, `unidad`, `codigos_barras[]`, `costo_actual`, `precio_venta`, `stock_minimo` |
| `EV-producto.actualizado` | Cambian datos del producto | `producto_id`, `campos_cambiados{campo: {antes, despues}}` |
| `EV-producto.archivado` | Se archiva un producto | `producto_id`, `nombre`, `stock_al_archivar` |
| `EV-movimiento.registrado` | Se registra cualquier movimiento | `movimiento_id`, `producto_id`, `tipo`, `cantidad`, `motivo`, `nota`, `forzado`, `stock_resultante`, `origen`, `recepcion_id?` |
| `EV-movimiento.anulado` | Se anula un movimiento | `movimiento_id_original`, `contramovimiento_id`, `producto_id`, `cantidad`, `motivo_anulacion`, `stock_resultante` |
| `EV-stock.bajo_minimo` | El stock cruza hacia abajo el mínimo (`RN-22`) | `producto_id`, `nombre`, `stock_actual`, `stock_minimo`, `deficit`, `unidad`, `proveedor_habitual_id?` |
| `EV-stock.agotado` | El stock llega a cero o menos | `producto_id`, `nombre`, `stock_actual`, `unidad` |
| `EV-stock.repuesto` | El stock vuelve por encima del mínimo tras haber estado por debajo | `producto_id`, `stock_actual`, `stock_minimo` |
| `EV-proveedor.creado` | Se da de alta un proveedor | `proveedor_id`, `nombre`, `identificacion_fiscal`, `contacto` |
| `EV-compra.ordenada` | Una orden pasa a emitida | `orden_id`, `proveedor_id`, `fecha_esperada`, `moneda`, `total_estimado`, `lineas[]` |
| `EV-compra.recibida` | Se confirma una recepción | `recepcion_id`, `orden_id?`, `proveedor_id`, `fecha`, `moneda`, `tasa_cambio`, `total_moneda_base`, `lineas[{producto_id, cantidad, costo_unitario, costo_unitario_base}]`, `movimientos_generados[]` |
| `EV-compra.recibida_parcial` | Una recepción deja pendientes en la orden | `orden_id`, `recepcion_id`, `lineas_pendientes[{producto_id, cantidad_pendiente}]` |
| `EV-compra.cerrada_con_faltante` | Se cierra una orden con faltante | `orden_id`, `motivo`, `lineas_faltantes[]` |
| `EV-factura.registrada` | Se registra una factura de compra | `factura_id`, `proveedor_id`, `numero`, `fecha_emision`, `fecha_vencimiento?`, `moneda`, `base_gravable`, `impuesto`, `total`, `total_moneda_base`, `estado_pago`, `recepciones[]`, `imagenes[]` |
| `EV-factura.pagada` | Una factura se marca como pagada | `factura_id`, `proveedor_id`, `numero`, `total`, `fecha_pago` |
| `EV-inventario.discrepancia` | Se registra un movimiento forzado (`RN-04`) | `movimiento_id`, `producto_id`, `cantidad_solicitada`, `stock_disponible`, `stock_resultante`, `motivo`, `nota` |

### Contrato de webhooks salientes (`RF-INT-005` a `RF-INT-007`)

En v1 se persisten las suscripciones y se documenta el contrato. **La entrega no se
implementa.** Una suscripción tiene URL de destino, lista de tipos de evento (o comodín),
secreto de firma, estado activo/inactivo y descripción.

Cuando se implemente, cada entrega será un POST con el sobre completo del evento como
cuerpo, más las cabeceras de identificador de evento, tipo, momento de firma y firma HMAC
del cuerpo con el secreto. La semántica es **al menos una vez**: el receptor debe descartar
duplicados por el identificador del evento. Se reintenta con espera creciente ante fallo o
respuesta distinta de 2xx, y la suscripción se desactiva tras fallos persistentes.

---

## 7. Puntos de integración candidatos para n8n

Cinco propuestas concretas. **Son material para que decidas, no decisiones tomadas.** La v1
no implementa ninguna: solo garantiza que el evento y el endpoint existen.

### N8N-01 — Pedido automático al proveedor cuando algo baja del mínimo
**Disparador:** `EV-stock.bajo_minimo`.
**Entrada:** producto, stock actual, mínimo, déficit, proveedor habitual, historial de compras
del producto vía `RF-REP-004`.
**Qué haría:** agrupar los productos bajo mínimo del mismo proveedor durante el día, y a una
hora fija enviar por WhatsApp o correo el pedido ya redactado, creando la orden de compra en
estado emitida vía `RF-COM-002`.
**Valor:** hoy Marta se entera de que le falta algo cuando el cliente lo pide. Esto adelanta
el pedido y evita la venta perdida, que es el costo invisible más caro de estos negocios.

### N8N-02 — Recordatorio de facturas por vencer
**Disparador:** programado a diario, consultando `RF-FAC-008` filtrado por estado pendiente y
vencimiento próximo.
**Entrada:** facturas pendientes con fecha de vencimiento dentro de N días, proveedor y monto.
**Qué haría:** avisar por WhatsApp con el resumen de lo que vence esta semana y el total.
**Valor:** evita recargos y llamadas incómodas del proveedor. Es un dolor real y frecuente.

### N8N-03 — Carpeta mensual para el contador
**Disparador:** programado, el día 1 de cada mes.
**Entrada:** `RF-FAC-007` para el mes anterior — listado más imágenes.
**Qué haría:** depositar las imágenes y una hoja de cálculo en la carpeta compartida del
contador y avisarle por correo.
**Valor:** elimina el ritual mensual de buscar papeles y mandar fotos sueltas por WhatsApp.
Es la automatización con menos riesgo y más agradecimiento inmediato.

### N8N-04 — Vigilancia de discrepancias de inventario
**Disparador:** `EV-inventario.discrepancia` (movimiento forzado).
**Entrada:** producto, cantidad solicitada, stock disponible, motivo, nota, autor.
**Qué haría:** acumular las discrepancias y enviar un resumen semanal con los productos que
más se descuadran, sugiriendo cuáles conviene recontar.
**Valor:** un movimiento forzado es la señal de que el sistema y el estante se separaron.
Detectarlo temprano es lo que impide que Marta pierda la confianza en los números.

### N8N-05 — Digitalización asistida de la factura
**Disparador:** `EV-factura.registrada` con imagen adjunta.
**Entrada:** imagen de la factura y los datos que Marta tecleó.
**Qué haría:** pasar la imagen por un servicio de OCR o un modelo, comparar número, fecha y
total contra lo registrado, y avisar solo cuando no coincidan, con la corrección propuesta.
**Valor:** captura los errores de tecleo sin quitarle a Marta el control, y abre el camino a
que en el futuro solo tenga que tomar la foto. Nota: el OCR quedó **fuera del alcance de la
v1** a propósito; esta es la vía barata de probarlo sin tocar la app.

### Otras dos, más baratas, si quieres empezar por lo pequeño
- **Reporte semanal de valorización** (`RF-REP-003` programado) enviado por WhatsApp: una
  línea con cuánta plata hay parada y cuánto cambió respecto a la semana pasada.
- **Aviso de producto sin movimiento** (`RF-REP-002` mensual): lista de lo que lleva 90 días
  quieto, para liquidarlo antes de que se vuelva pérdida.

---

## 8. Glosario

| Término | Significado en este proyecto |
|---|---|
| **Ajuste** | Movimiento que corrige el stock del sistema para igualarlo a un conteo físico. Se declara por cantidad contada, no por diferencia. |
| **Archivar** | Retirar de la operación diaria un producto o proveedor que tiene historia, conservando todos sus registros. Alternativa al borrado, que no existe. |
| **Cantidad contada** | Lo que el usuario ve realmente en el estante durante un conteo físico. Entrada del ajuste. |
| **Contramovimiento** | Movimiento de igual cantidad y signo contrario que anula a otro y lo referencia. Única forma de corregir un movimiento. |
| **Costo actual** | Último costo unitario conocido del producto, en moneda base. Base de la valorización. Sin historial. |
| **Credencial de servicio** | API key emitida por el dueño para que un proceso externo (n8n) opere sobre su negocio sin sesión de usuario. |
| **Déficit** | Diferencia entre el stock mínimo y el stock actual cuando este es menor. Ordena la urgencia de reposición. |
| **Entrada** | Movimiento que suma stock. Normalmente generado por una recepción. |
| **Evento de dominio** | Registro persistido de un hecho de negocio, con su payload, destinado a consumidores externos. |
| **Merma** | Salida de stock por rotura, vencimiento, robo o pérdida. Tipo propio, distinto de la salida. |
| **Moneda base** | Moneda en la que el negocio expresa sus reportes. Se fija al crear el negocio y no cambia. |
| **Movimiento** | Hecho inmutable que altera el stock de un producto. Única fuente del stock. |
| **Movimiento forzado** | Movimiento registrado pese a dejar el stock negativo, con confirmación explícita y motivo. Señal de discrepancia. |
| **Orden de compra (OC)** | Documento de planificación de lo que se va a pedir. Opcional. |
| **Recepción** | Documento que registra la llegada de mercancía. Genera las entradas y congela el costo. Puede existir sin orden de compra. |
| **Salida** | Movimiento que resta stock por venta o consumo. |
| **SKU** | Código interno del negocio para identificar un producto. Único por negocio. Distinto del código de barras, que es del fabricante. |
| **Stock actual** | Suma de todos los movimientos del producto. Nunca un valor editado. |
| **Stock mínimo** | Umbral por debajo del cual el producto se considera en riesgo de agotarse. Opcional. |
| **Unidad continua** | Unidad de medida que admite fracciones (kg, g, m, L). |
| **Unidad discreta** | Unidad de medida que solo admite enteros (unidad, caja, paquete). |
| **Valorización** | Suma de stock × costo actual, en moneda base. |

---

## 9. Fuera del alcance de la v1

No se diseña, pero nada de lo anterior lo bloquea: ventas y punto de venta, facturación
electrónica ante entidad tributaria, múltiples bodegas, empleados y roles, pagos en línea,
OCR de facturas, operación sin conexión con cola local de sincronización, y valorización del
inventario a una fecha pasada (consecuencia de `RN-09`).

---

## 10. Decisiones pendientes

Todas resueltas al cerrar la Fase 3. Se conservan con su resolución para que quede el rastro
de qué se decidió y por qué.

| # | Pendiente | Afecta | Resolución |
|---|---|---|---|
| 1 | **Almacenamiento de imágenes** | `RF-CAT-006`, `RF-FAC-005`, `RNF-11` | **Resuelto**: contrato abstracto `AlmacenImagenes` con **una sola implementación, filesystem**. El adaptador S3 queda fuera del alcance de v1; añadirlo después es una tarea aislada. `plan.md` §6 |
| 2 | **Origen de la tasa de cambio** | `RF-COM-007`, `RF-FAC-003`, `RN-10` | **Resuelto**: manual, tecleada por el usuario y congelada en el documento |
| 3 | **Método de costo actual** | `RF-COM-011`, `RF-REP-003` | **Resuelto**: último costo recibido, por ser explicable a un usuario no técnico |
| 4 | **Umbral de "sin movimiento"** | `RF-REP-002` | **Resuelto**: parámetro de la consulta, 90 días por defecto |
| 5 | **Formato de la exportación de facturas** | `RF-FAC-007` | **Resuelto**: ZIP con CSV más imágenes, entregado por el menú de compartir del celular |
| 6 | **Lista cerrada de motivos** | `RF-INV-010` | **Resuelto**: lista fija de semilla, sin administración, con "otro" de escape y nota obligatoria |

**No queda ninguna decisión abierta.** La enmienda a `constitution.md` §2 sobre la
representación del dinero se aprobó el 2026-09-01 y está registrada como **E-01** en la
constitución; el motivo está en `plan.md` §0.
