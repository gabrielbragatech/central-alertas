package com.centralalertas.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de uma execucao de cobranca. E devolvido pelo CobrancaService e
 * serializado em JSON na rota de disparo.
 */
public class ResumoCobranca {

    private int aVencer;   // qtd de faturas "a vencer" encontradas
    private int vencidas;  // qtd de faturas vencidas encontradas
    private int enviados;  // e-mails enviados com sucesso
    private int pulados;   // pulados por ja haver alerta com sucesso hoje
    private int falhas;    // tentativas de envio que falharam
    private final List<String> detalhes = new ArrayList<>(); // 1 linha por fatura processada

    // Mutadores usados pelo CobrancaService enquanto processa.
    public void setAVencer(int aVencer) {
        this.aVencer = aVencer;
    }

    public void setVencidas(int vencidas) {
        this.vencidas = vencidas;
    }

    public void incEnviados() {
        this.enviados++;
    }

    public void incPulados() {
        this.pulados++;
    }

    public void incFalhas() {
        this.falhas++;
    }

    public void addDetalhe(String linha) {
        this.detalhes.add(linha);
    }

    // Getters (o Gson serializa os campos; estes ajudam quem usa o objeto em Java).
    public int getAVencer() {
        return aVencer;
    }

    public int getVencidas() {
        return vencidas;
    }

    public int getEnviados() {
        return enviados;
    }

    public int getPulados() {
        return pulados;
    }

    public int getFalhas() {
        return falhas;
    }

    public List<String> getDetalhes() {
        return detalhes;
    }

    @Override
    public String toString() {
        return "ResumoCobranca{aVencer=" + aVencer + ", vencidas=" + vencidas
                + ", enviados=" + enviados + ", pulados=" + pulados + ", falhas=" + falhas
                + ", detalhes=" + detalhes + "}";
    }
}
