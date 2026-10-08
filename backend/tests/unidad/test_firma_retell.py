"""RF-AST-004: solo Retell, con nuestra API key, puede llamar a las herramientas del asistente."""

from app.infra.retell import VENTANA_FIRMA_MS, ClienteRetell, firmar

CLAVE = "key_prueba"
CUERPO = '{"name":"stock_bajo","args":{},"chat":{"chat_id":"c1"}}'
AHORA = 1_791_417_798_060


def _cliente(clave: str = CLAVE) -> ClienteRetell:
    return ClienteRetell(clave, "agent_x", "https://retell.invalid")


def test_acepta_la_firma_hecha_con_la_misma_clave() -> None:
    assert _cliente().firma_valida(CUERPO, firmar(CUERPO, CLAVE, AHORA), AHORA)


def test_rechaza_otra_clave_otro_cuerpo_o_sin_firma() -> None:
    assert not _cliente().firma_valida(CUERPO, firmar(CUERPO, "key_otra", AHORA), AHORA)
    assert not _cliente().firma_valida(CUERPO + " ", firmar(CUERPO, CLAVE, AHORA), AHORA)
    assert not _cliente().firma_valida(CUERPO, None, AHORA)
    assert not _cliente().firma_valida(CUERPO, "basura", AHORA)


def test_rechaza_firmas_viejas_para_que_no_se_puedan_repetir() -> None:
    vieja = firmar(CUERPO, CLAVE, AHORA - VENTANA_FIRMA_MS - 1)
    assert not _cliente().firma_valida(CUERPO, vieja, AHORA)


def test_sin_clave_configurada_nada_es_valido() -> None:
    assert not _cliente("").firma_valida(CUERPO, firmar(CUERPO, "", AHORA), AHORA)
