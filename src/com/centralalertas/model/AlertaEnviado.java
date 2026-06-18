package com.centralalertas.model;

import java.time.LocalDateTime;

/**
 * Log de um alerta de cobranca enviado (tabela alerta_enviado).
 *
 * <p>Aqui guardamos apenas o {@code faturaId} (e nao o objeto Fatura inteiro): e um
 * registro de log, basta saber a qual fatura ele se refere.</p>
 */
public class AlertaEnviado {

    private long id;                 // 0 enquanto nao foi salvo
    private long faturaId;           // qual fatura gerou este alerta (mapeia fatura_id)
    private TipoAlerta tipo;         // VENCIMENTO_PROXIMO ou ATRASADA
    private String canal;            // por onde foi enviado, ex.: "EMAIL"
    private LocalDateTime enviadoEm; // momento do envio (preenchido pelo DAO se nulo)
    private boolean sucesso;         // o envio deu certo? (coluna TINYINT(1) 0/1)

    public AlertaEnviado() {
    }

    // Construtor de conveniencia para registrar um novo envio.
    public AlertaEnviado(long faturaId, TipoAlerta tipo, String canal, boolean sucesso) {
        this.faturaId = faturaId;
        this.tipo = tipo;
        this.canal = canal;
        this.sucesso = sucesso;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getFaturaId() {
        return faturaId;
    }

    public void setFaturaId(long faturaId) {
        this.faturaId = faturaId;
    }

    public TipoAlerta getTipo() {
        return tipo;
    }

    public void setTipo(TipoAlerta tipo) {
        this.tipo = tipo;
    }

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }

    public LocalDateTime getEnviadoEm() {
        return enviadoEm;
    }

    public void setEnviadoEm(LocalDateTime enviadoEm) {
        this.enviadoEm = enviadoEm;
    }

    public boolean isSucesso() {
        return sucesso;
    }

    public void setSucesso(boolean sucesso) {
        this.sucesso = sucesso;
    }

    @Override
    public String toString() {
        return "AlertaEnviado{id=" + id + ", faturaId=" + faturaId + ", tipo=" + tipo
                + ", canal='" + canal + "', enviadoEm=" + enviadoEm + ", sucesso=" + sucesso + "}";
    }
}
