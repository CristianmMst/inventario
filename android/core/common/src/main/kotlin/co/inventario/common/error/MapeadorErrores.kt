package co.inventario.common.error

import co.inventario.common.ErrorApp

/**
 * Único sitio que traduce el `code` de la API a texto para el dueño del negocio (RNF-07,
 * RNF-12, plan.md §8.5). El `message` crudo del servidor nunca llega a la UI: aquí se decide
 * qué dice la app, en español y sin jerga.
 */
object MapeadorErrores {

    const val SIN_RED = "SIN_RED"
    const val TIEMPO_AGOTADO = "TIEMPO_AGOTADO"
    const val ERROR_SERVIDOR = "ERROR_SERVIDOR"
    const val DESCONOCIDO = "DESCONOCIDO"

    /** Detalles que algunos códigos usan para completar el texto. */
    private val plantillas: Map<String, (Map<String, String>) -> String> = mapOf(
        // Transporte
        SIN_RED to { _ -> "No se guardó. Sin conexión: reintentando…" },
        TIEMPO_AGOTADO to { _ -> "No se guardó. El servidor tardó demasiado: reintentando…" },
        ERROR_SERVIDOR to { d -> "Algo falló en el servidor. Intenta de nuevo; si sigue, reporta el código ${d["request_id"] ?: "desconocido"}." },
        "ERROR_INTERNO" to { d -> "Algo falló en el servidor. Intenta de nuevo; si sigue, reporta el código ${d["request_id"] ?: "desconocido"}." },
        // Genéricos
        "PAYLOAD_MALFORMADO" to { _ -> "La app envió datos que el servidor no entendió. Actualízala si el problema sigue." },
        "VALIDACION" to { _ -> "Revisa los datos marcados." },
        "RECURSO_NO_ENCONTRADO" to { _ -> "Eso ya no existe." },
        "METODO_NO_PERMITIDO" to { _ -> "Esa acción no está disponible." },
        "LIMITE_DE_TASA" to { _ -> "Demasiadas operaciones seguidas. Espera un momento." },
        "CURSOR_INVALIDO" to { _ -> "La lista cambió. Vuelve a cargarla desde el principio." },
        // Identidad
        "CREDENCIAL_REQUERIDA" to { _ -> "Inicia sesión para continuar." },
        "CREDENCIAL_INVALIDA" to { _ -> "Correo o contraseña incorrectos." },
        "CORREO_YA_REGISTRADO" to { _ -> "Ese correo ya tiene una cuenta. Inicia sesión." },
        "CONTRASENA_ACTUAL_INCORRECTA" to { _ -> "La contraseña actual no coincide." },
        "SIN_PERMISO" to { _ -> "No tienes permiso para esto." },
        "API_KEY_NO_ENCONTRADA" to { _ -> "Esa credencial de servicio no existe." },
        "MONEDA_BASE_INMUTABLE" to { _ -> "Ya hay compras registradas: la moneda del negocio no se puede cambiar." },
        // Catálogo
        "PRODUCTO_NO_ENCONTRADO" to { d -> d["codigo"]?.let { "Ningún producto tiene el código $it. ¿Quieres crearlo?" } ?: "Ese producto no existe." },
        "SKU_DUPLICADO" to { d -> "Ya hay un producto con el SKU ${d["sku"] ?: ""}." },
        "CODIGO_BARRAS_DUPLICADO" to { d -> "Ese código ya es de «${d["producto_nombre"] ?: "otro producto"}»." },
        "CODIGO_BARRAS_NO_ENCONTRADO" to { _ -> "Ese producto no tiene ese código." },
        "CATEGORIA_DUPLICADA" to { d -> "Ya existe la categoría «${d["nombre"] ?: ""}»." },
        "CATEGORIA_NO_ENCONTRADA" to { _ -> "Esa categoría no existe." },
        "CATEGORIA_DESCONOCIDA" to { _ -> "Esa categoría no existe." },
        "UNIDAD_DESCONOCIDA" to { _ -> "Esa unidad de medida no existe." },
        "MONEDA_NO_ES_LA_BASE" to { d -> "El costo y el precio van en ${d["moneda_base"] ?: "la moneda del negocio"}." },
        "PRODUCTO_ARCHIVADO" to { _ -> "Este producto está archivado. Desarchívalo para moverlo." },
        "BUSQUEDA_VACIA" to { _ -> "Escribe algo para buscar." },
        "MONEDA_INVALIDA" to { _ -> "La moneda debe ser un código de tres letras, como COP." },
        "MONTO_INVALIDO" to { _ -> "El monto no es válido: usa números con hasta cuatro decimales." },
        "UNIDAD_INVALIDA" to { _ -> "Esa unidad de medida no es válida." },
        // Movimientos
        "STOCK_INSUFICIENTE" to { d -> "Solo hay ${d["disponible"] ?: "menos"} en stock." },
        "CANTIDAD_INVALIDA_PARA_UNIDAD" to { _ -> "Esta unidad se cuenta por enteros." },
        "CANTIDAD_INVALIDA" to { _ -> "La cantidad no es válida." },
        "CANTIDAD_NO_POSITIVA" to { _ -> "La cantidad debe ser mayor que cero." },
        "MOTIVO_INVALIDO" to { _ -> "Elige un motivo de la lista." },
        "MOTIVO_RESERVADO" to { _ -> "Ese motivo lo pone el sistema al recibir una compra." },
        "NOTA_OBLIGATORIA" to { _ -> "Escribe una nota que explique el motivo." },
        "TIPO_NO_PERMITIDO" to { _ -> "Ese tipo de movimiento no se registra a mano." },
        "DIRECCION_OBLIGATORIA" to { _ -> "Indica si el ajuste suma o resta." },
        "MOVIMIENTO_NO_ENCONTRADO" to { _ -> "Ese movimiento no existe." },
        "MOVIMIENTO_YA_ANULADO" to { _ -> "Ese movimiento ya estaba anulado." },
        "CONTRAMOVIMIENTO_NO_ANULABLE" to { _ -> "Una anulación no se anula. Registra el movimiento de nuevo." },
        // Idempotencia
        "CLAVE_IDEMPOTENCIA_REUTILIZADA" to { _ -> "Esta operación ya se envió con otros datos. Vuelve a intentarlo." },
        "OPERACION_EN_CURSO" to { _ -> "Todavía se está guardando. Espera un momento." },
        // Compras
        "PROVEEDOR_NO_ENCONTRADO" to { _ -> "Ese proveedor no existe." },
        "PROVEEDOR_ARCHIVADO" to { _ -> "Ese proveedor está archivado." },
        "PROVEEDOR_CON_DOCUMENTOS" to { _ -> "Este proveedor tiene documentos. Archívalo en vez de borrarlo." },
        "ORDEN_NO_ENCONTRADA" to { _ -> "Esa orden no existe." },
        "ORDEN_NO_EDITABLE" to { _ -> "Solo un borrador se puede editar." },
        "ORDEN_NO_RECIBIBLE" to { _ -> "Esa orden ya no admite recepciones." },
        "ORDEN_CON_RECEPCIONES" to { _ -> "La orden ya tiene recepciones: ciérrala con faltante." },
        "TRANSICION_INVALIDA" to { _ -> "Esa acción no aplica al estado actual de la orden." },
        "PRODUCTO_REPETIDO" to { _ -> "Un producto solo puede ir una vez; suma las cantidades." },
        "PRODUCTO_FUERA_DE_ORDEN" to { _ -> "Ese producto no está en la orden. Regístralo en una recepción directa." },
        "PROVEEDOR_NO_COINCIDE" to { _ -> "La orden es de otro proveedor." },
        "RECEPCION_NO_ENCONTRADA" to { _ -> "Esa recepción no existe." },
        "RECEPCION_INMUTABLE" to { _ -> "Una recepción confirmada no se edita. Anula sus movimientos para corregirla." },
        "EXCESO_SOBRE_ORDEN" to { _ -> "Estás recibiendo más de lo ordenado. Confirma el exceso para continuar." },
        "TASA_OBLIGATORIA" to { _ -> "Indica la tasa de cambio." },
        "TASA_INVALIDA" to { _ -> "La tasa de cambio debe ser mayor que cero." },
        "MONEDAS_DISTINTAS" to { _ -> "Todos los importes deben ir en la misma moneda." },
        // Facturas
        "FACTURA_NO_ENCONTRADA" to { _ -> "Esa factura no existe." },
        "FACTURA_DUPLICADA" to { d -> "El número ${d["numero"] ?: ""} ya está registrado para este proveedor." },
        "FACTURA_NO_CUADRA" to { d -> "Base más impuesto no da el total: hay una diferencia de ${d["diferencia"] ?: "?"}." },
        "FACTURA_YA_PAGADA" to { _ -> "Esa factura ya está pagada." },
        "FACTURA_ANULADA" to { _ -> "Esa factura está anulada." },
        "RECEPCION_YA_FACTURADA" to { _ -> "Esa recepción ya está en otra factura." },
        "RECEPCION_DE_OTRO_PROVEEDOR" to { _ -> "Esa recepción es de otro proveedor." },
        "RECEPCION_SIN_CONFIRMAR" to { _ -> "Confirma la recepción antes de facturarla." },
        "RANGO_INVALIDO" to { _ -> "La fecha inicial no puede ser posterior a la final." },
        // Imágenes
        "IMAGEN_DEMASIADO_PESADA" to { _ -> "La foto pesa demasiado. La app la comprime; vuelve a intentarlo." },
        "IMAGEN_DEMASIADO_GRANDE" to { _ -> "La foto es demasiado grande. Vuelve a tomarla." },
        "IMAGEN_INVALIDA" to { _ -> "Ese archivo no es una imagen." },
        "FORMATO_NO_ADMITIDO" to { _ -> "Solo se admiten fotos JPEG, PNG o WebP." },
        "IMAGEN_NO_ENCONTRADA" to { _ -> "Esa imagen ya no está disponible." },
        // Integración y reportes
        "TIPO_DE_EVENTO_DESCONOCIDO" to { _ -> "Uno de los tipos de evento no existe." },
        "SUSCRIPCION_NO_ENCONTRADA" to { _ -> "Esa suscripción no existe." },
        "VALORIZACION_SOLO_ACTUAL" to { _ -> "La valorización es siempre a hoy." },
    )

    val codigosConocidos: Set<String> get() = plantillas.keys

    /** Texto para el usuario. Un código desconocido recibe un texto genérico, nunca el crudo. */
    fun mensajePara(codigo: String, detalles: Map<String, String> = emptyMap()): String =
        plantillas[codigo]?.invoke(detalles) ?: "No se pudo completar la operación. Intenta de nuevo."

    fun error(codigo: String, detalles: Map<String, String> = emptyMap(), requestId: String? = null): ErrorApp =
        ErrorApp(codigo = codigo, mensaje = mensajePara(codigo, detalles), requestId = requestId, detalles = detalles)
}
