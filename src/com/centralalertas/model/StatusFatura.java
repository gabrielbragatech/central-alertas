package com.centralalertas.model;

/**
 * Situacao de uma fatura.
 * Guardado no banco como TEXTO (coluna fatura.status, VARCHAR) usando o name() do enum:
 * "PENDENTE", "PAGA" ou "ATRASADA".
 */
public enum StatusFatura {
    PENDENTE,
    PAGA,
    ATRASADA
}
