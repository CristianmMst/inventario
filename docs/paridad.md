# Paridad API/app (T-098)

Revisión de `RF-INT-008` y `RNF-17`, cerrada el 2026-09-02. Dos afirmaciones, cada una con su
prueba automática en `backend/tests/integracion/test_openapi_contrato.py`:

1. **Ninguna capacidad de la app móvil carece de endpoint accesible con credencial de
   servicio (`X-API-Key`).** El test `test_rf_int_008_toda_ruta_de_la_app_existe_y_acepta_credencial_de_servicio`
   lee las rutas de la interfaz Retrofit (`android/core/data/.../InventarioApi.kt`), comprueba
   que cada una existe en el documento OpenAPI y que declara `credencialServicio` en su
   seguridad. Si alguien añade una llamada a la app sin endpoint, el build rompe.
2. **El 100% de las rutas registradas figura en OpenAPI con ejemplo de petición y de error.**
   `app/infra/openapi.py` completa lo que FastAPI genera: los dos esquemas de seguridad, la
   seguridad por operación, un ejemplo para todo cuerpo de petición y respuestas `401`, `404`,
   `409` y `422` con el sobre único `{"error": {"code", "message", "details"}}`. Lo exigen
   `test_rnf_17_toda_operacion_declara_seguridad_o_es_publica`,
   `test_rnf_17_toda_peticion_con_cuerpo_trae_ejemplo` y
   `test_rnf_17_toda_operacion_documenta_un_error_con_el_sobre_unico`.

Documento: `GET /openapi.json` (75 operaciones al cierre). Interfaz interactiva en `/docs`.

## Credenciales

| Esquema | Cabecera | Quién lo usa |
|---|---|---|
| `sesionUsuario` | `Authorization: Bearer <JWT de 15 min>` | La app, tras iniciar sesión (`RF-AUT-002`). |
| `credencialServicio` | `X-API-Key: inv_<prefijo>_<secreto>` | Integraciones (`RF-AUT-005`). Mismo negocio, mismas operaciones. |

Rutas **públicas** por diseño (`RNF-11`): `POST /auth/login`, `POST /auth/registro`,
`POST /auth/refresh`, `GET /salud` y `GET /imagenes/{identificador}` cuando lleva el token
firmado `t`. Rutas **solo de usuario** porque operan sobre una sesión: `POST /auth/logout` y
`PATCH /auth/password`; una credencial de servicio no tiene sesión que cerrar ni contraseña.

## Capacidad ↔ endpoint

Todas las rutas van bajo `/api/v1`. Las marcadas con ⚿ exigen `Idempotency-Key` (`RNF-06`).

| Capacidad en la app | Pantalla | Endpoint(s) |
|---|---|---|
| Registro de cuenta y negocio | Registro | `POST /auth/registro` |
| Inicio y cierre de sesión, renovación | Login, Ajustes | `POST /auth/login`, `POST /auth/logout`, `POST /auth/refresh` |
| Datos del negocio (moneda base) | Menú | `GET /negocio`, `PATCH /negocio` |
| Escanear o teclear un código | Escaneo → Resolver código | `GET /productos/por-codigo/{codigo}` |
| Código desconocido → alta precargada | Alta de producto | `POST /productos` (con `codigos_barras`) |
| Ficha con stock | Ficha | `GET /productos/{id}`, `GET /productos/{id}/stock` |
| Alta y edición, archivar/desarchivar | Alta/Edición, Ficha | `POST /productos`, `PATCH /productos/{id}`, `POST /productos/{id}/archivar`, `POST /productos/{id}/desarchivar` |
| Foto del producto (≤ 300 KB, ≤ 1280 px) | Alta/Edición | `PUT /productos/{id}/imagen`, `GET /imagenes/{id}` |
| Códigos de barras adicionales | Alta | `POST /productos/{id}/codigos-barras`, `DELETE /productos/{id}/codigos-barras/{codigo}` |
| Búsqueda por texto y listado con filtros | Búsqueda | `GET /productos/buscar`, `GET /productos` |
| Categorías y unidades | Alta, Búsqueda | `GET /categorias`, `POST /categorias`, `PATCH /categorias/{id}`, `GET /unidades-medida` |
| Motivos de la lista cerrada | Movimiento | `GET /motivos-movimiento` |
| Salida, entrada, merma, ajuste (con override) | Movimiento | ⚿ `POST /movimientos` |
| Conteo físico | Conteo | ⚿ `POST /productos/{id}/conteo` |
| Historial y anulación | Historial | `GET /productos/{id}/movimientos`, `GET /movimientos`, `GET /movimientos/{id}`, ⚿ `POST /movimientos/{id}/anular` |
| Proveedores (alta, edición, archivado, eliminación) | Proveedores | `GET /proveedores`, `POST /proveedores`, `GET/PATCH/DELETE /proveedores/{id}`, `POST /proveedores/{id}/archivar`, `POST /proveedores/{id}/desarchivar` |
| Órdenes: borrador, emitir, cancelar, cerrar con faltante | Órdenes | `GET /ordenes-compra`, `POST /ordenes-compra`, `GET/PATCH /ordenes-compra/{id}`, `POST /ordenes-compra/{id}/emitir`, `.../cancelar`, `.../cerrar-con-faltante` |
| Recepción directa o contra orden, con exceso confirmado | Recepción | `GET /recepciones`, `POST /recepciones`, `GET/PATCH /recepciones/{id}`, ⚿ `POST /recepciones/{id}/confirmar` |
| Factura de compra con cuadre y fotos | Nueva factura | ⚿ `POST /facturas`, `POST /facturas/{id}/imagenes`, `DELETE /facturas/{id}/imagenes/{imagenId}` |
| Listado con total del filtro, pago, anulación, vinculación | Facturas | `GET /facturas`, `GET/PATCH /facturas/{id}`, `POST /facturas/{id}/pagar`, `POST /facturas/{id}/anular`, `PUT /facturas/{id}/recepciones` |
| Exportación ZIP para el contador | Facturas | `GET /facturas/exportacion?desde&hasta` |
| Siete reportes | Reportes | `GET /reportes/bajo-minimo`, `/agotados`, `/sin-movimiento`, `/valorizacion`, `/compras`, `/mermas`, `/discrepancias` |
| Credenciales de servicio | Ajustes | `GET /api-keys`, `POST /api-keys`, `DELETE /api-keys/{id}` |
| Webhooks (contrato, sin entrega en v1) | Ajustes | `GET /webhooks`, `POST /webhooks`, `DELETE /webhooks/{id}` |
| Eventos de negocio (solo API) | — | `GET /eventos` |

Sin huecos: toda fila de la app tiene endpoint, y las dos capacidades que solo existen en la
API (`GET /eventos`, `PATCH /negocio`) están ahí para integraciones, no porque falten en la app.

## Cómo volver a comprobarlo

```
cd backend && uv run pytest tests/integracion/test_openapi_contrato.py -q
```
