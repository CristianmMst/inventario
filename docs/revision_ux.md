# Revisión de operación con una mano, legibilidad y arranque (T-100)

Cierre de `RNF-08`, `RNF-09` y `RNF-10` al 2026-09-02. Lo que se pudo comprobar con tests o en
el emulador queda marcado; lo que exige un teléfono real de gama media queda listado con su
procedimiento, sin darse por hecho.

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

## Pendiente de teléfono real de gama media (no se da por hecho)

El emulador corre con `swiftshader` (GPU por software) y no representa a un Android 8 de gama
media, así que estos tiempos **no se han medido** todavía:

| Medición | Umbral | Procedimiento |
|---|---|---|
| Arranque hasta pantalla usable | < 3 s (RNF-10) | `adb shell am start -W -n co.inventario.app/.MainActivity` tres veces en frío (`am force-stop` antes); leer `TotalTime`. Compilación `release` con R8 para la medida definitiva. |
| Cámara lista desde que se abre el escaneo | < 1,5 s (RNF-10) | Cronometrar desde el toque hasta el primer fotograma en `PreviewView` (logcat `CameraX` `Camera opened` → `onSurfaceRequested`). Reproducible con `adb logcat -v time` filtrando `Camera`. |
| Lectura de etiqueta real | una lectura en 5 s (RF-CAT-008) | Apuntar a un EAN-13 impreso; debe llegar a la ficha una sola vez. |
| Linterna | funciona (T-078) | Toque en «Linterna» con la cámara abierta. |
| Pulgar en 6" | todo alcanzable | Sostener el teléfono con una mano y recorrer escaneo → salida → confirmar sin cambiar de mano. |

## Hallazgos y decisiones de esta revisión

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
