# Revisión de operación con una mano, legibilidad y arranque (T-100)

Cierre de `RNF-08`, `RNF-09` y `RNF-10` al 2026-09-02, con medidas tomadas en un **Galaxy A21s
real** (gama media) además del emulador. Lo verificado lleva su cifra; lo que todavía necesita
una mano humana está listado al final, sin darse por hecho.

## Lo que garantiza el código (verificado)

| Requisito | Garantía | Dónde se comprueba |
|---|---|---|
| RNF-08 · área táctil ≥ 48 dp, botón principal 56 dp | `Dimensiones.areaTactilMinima`, `alturaBotonPrincipal`; todos los botones y campos pasan por `BotonPrincipal`, `BotonSecundario`, `CampoTexto` del design system | `core/designsystem` · `TemaTest` |
| RNF-09 · texto ≥ 16 sp y contraste AA | Ningún estilo de `Tipografia.material` baja de 16 sp; `paresDeContraste` verifica ≥ 4,5:1 con `contraste()` (WCAG) | `TemaTest` |
| RNF-09 · stock legible de pie | `StockDestacado` a 40 sp en negrita, primer elemento de la ficha, del movimiento y del conteo | `PantallaFicha`, `PantallaMovimiento`, `PantallaConteo` |
| RNF-08 · salida en 3 toques desde el escaneo | Escaneo → ficha (1: «Registrar salida») → movimiento precargado con cantidad 1 y primer motivo (2: «Confirmar»); el 409 se resuelve en el mismo diálogo (3: «Forzar»). Con red y stock, son dos toques | `MovimientoViewModelTest` · recorrido en AVD el 2026-09-02 |
| RNF-08 · controles al alcance del pulgar | Las acciones van al **pie** de cada pantalla (escaneo, ficha, movimiento, listados); los títulos y datos arriba. Los `Row` de dos botones dividen el ancho en mitades para que el pulgar no cruce la pantalla | Estructura de las pantallas (`Column` con `weight(1f)` para el contenido y botones abajo) |
| RNF-15 · sin cámara todo se completa tecleando | Permiso denegado abre el teclado numérico; el flujo continúa igual | `EscaneoViewModelTest` · recorrido en AVD |
| RNF-07 · sin éxito falso | Toda escritura pasa por la bandeja; la UI solo navega con `Confirmada` | `BandejaSalidaTest`, `MovimientoViewModelTest` |

## Comprobado en el emulador (Pixel-like 6,1", API 36, sin aceleración de GPU)

- Registro → sesión iniciada → ficha → salida forzada → entrada → conteo → historial →
  anulación: todo el recorrido de H8 sobre el backend real.
- Código tecleado desconocido → alta precargada → producto creado → ficha.
- Menú → proveedores → nuevo proveedor → recepción directa (pantalla y buscador de productos).

## Medido en teléfono real (Galaxy A21s, LineageOS Android 15, 720x1600 a 280 dpi)

Gama media de verdad, sobre Wi-Fi contra el backend en la LAN (`-PbackendUrl=http://IP:8000/`).
Las cifras de arranque y cámara se toman con la variante **`medicion`** (igual que release: no
depurable, con R8), porque la de depuración no representa lo que usa nadie: en ella el mismo
arranque tarda 4,9 s.

| Medición | Umbral | Resultado | Cómo |
|---|---|---|---|
| Arranque en frío hasta pantalla usable | < 3 s (RNF-10) | **1,31 s** (1,31 / 1,39 / 1,31 / 1,34) | `am start -W`, cuatro arranques en frío |
| Cámara lista desde que se abre la pantalla | < 1,5 s (RNF-10) | **1,04 s** (1,18 / 1,15 / 1,04 / 1,02) | De `Displayed` a `first frame is DONE` en logcat |
| Código → ficha visible | < 500 ms (RNF-01) | **216 ms** | Una sola petición `por-codigo`; la ficha reutiliza el producto |
| Búsqueda por texto sobre 20.000 productos | < 500 ms (RNF-02) | **134 ms** | `GET /productos/buscar?q=resma`, 24 KB de respuesta |
| Registro de un movimiento | < 400 ms API (RNF-03) | **459 ms** de extremo a extremo | `POST /movimientos` desde el teléfono; la API sola da 113 ms (T-097) y la Wi-Fi añade ~300 ms |
| Salida desde la ficha | 3 toques (RNF-08) | **2 toques** | «Registrar salida» → «Confirmar»; el stock pasó de 39 a 38 |
| Texto ≥ 16 sp y controles al alcance | — | Se cumple | Captura de la ficha y del escaneo; los botones quedan en el tercio inferior |

Latencia de red de referencia en esa Wi-Fi: 5-30 ms de ida y vuelta al router y 60-160 ms por
petición HTTP completa desde el teléfono.

## Pendiente de mano humana

| Medición | Por qué sigue pendiente |
|---|---|
| Lectura de una etiqueta impresa (RF-CAT-008) y linterna | Hay que apuntar el teléfono a un código real: ninguna herramienta lo hace desde aquí. La cámara y el analizador ya se ven vivos en el teléfono, y el antirrebote está cubierto por `AntirreboteLecturasTest`. |
| `java.time` en API 24-25 (RNF-14) | El teléfono disponible es API 35. El desugaring está activado en todos los módulos; falta un dispositivo o AVD de Android 7 para confirmarlo. |
| Recorrido en pantalla de recepción, factura, reportes y ajustes | Verificados por tests de ViewModel; en el teléfono solo se recorrieron escaneo, ficha, movimientos y búsqueda. |
| Sostener el teléfono con una mano y recorrer el flujo | Los controles están en el tercio inferior, pero quién decide si «llega el pulgar» es una persona. |

## Hallazgos y decisiones de esta revisión

- **La compilación de release se caía al abrir el escaneo.** R8 borraba los registradores de
  componentes de ML Kit, porque el manifiesto los nombra dentro de la **clave** del
  `<meta-data>` y no en `android:name`: `BarcodeScanning.getClient()` devolvía un cliente con
  su fábrica interna en null. Se detectó al medir en el teléfono real, nunca en depuración
  (que no usa R8). Corregido con reglas en `app/proguard-rules.pro`.
- **El escaneo hacía dos peticiones**: `por-codigo` y otra vez el producto al abrir la ficha,
  441 ms de red donde RNF-01 concede 500 ms. `por-codigo` ya devuelve el producto con su
  stock, así que la ficha lo reutiliza una sola vez; al volver de un movimiento recarga de
  verdad, para no mostrar un stock viejo (RF-INV-003). Cubierto por test.
- **Entrada rápida de texto**: al inyectar texto con `adb shell input text` de golpe, los
  campos cuyo estado vive en un `StateFlow` del ViewModel pierden caracteres. Una persona
  tecleando no lo nota; un lector de códigos por teclado (modo "keyboard wedge") sí lo haría.
  Queda anotado como mejora para v1.1: migrar los campos a `TextFieldState` de Compose 1.7+.
  El escaneo de v1 va por cámara, no por teclado, así que no afecta al flujo principal.
- **Nombre del negocio en el menú**: las sesiones iniciadas antes de este cierre no guardaron
  nombre ni moneda del negocio; el menú muestra «Mi negocio» hasta volver a iniciar sesión.
- **Conteo con nota obligatoria**: la spec exige nota en todo ajuste (RF-INV-010); la pantalla
  lo dice en la etiqueta y valida antes de enviar. Se detectó en el AVD cuando el servidor
  rechazó un conteo sin nota.
