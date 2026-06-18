package com.centralalertas.model;

/**
 * Tipo do alerta de cobranca enviado.
 * Guardado no banco como TEXTO (coluna alerta_enviado.tipo, VARCHAR) usando o name():
 *  - VENCIMENTO_PROXIMO -> fatura que esta para vencer;
 *  - ATRASADA           -> fatura ja vencida.
 */
public enum TipoAlerta {
    VENCIMENTO_PROXIMO,
    ATRASADA
}
