package com.centralalertas.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Dispara a cobranca automaticamente de tempos em tempos.
 *
 * <p>Usa um {@link ScheduledExecutorService}: ele mantem UMA thread propria que executa
 * a tarefa nos horarios agendados, em SEGUNDO PLANO — sem travar a main nem o servidor HTTP.</p>
 */
public class Agendador {

    private final CobrancaService cobrancaService;
    private ScheduledExecutorService executor;

    public Agendador(CobrancaService cobrancaService) {
        this.cobrancaService = cobrancaService;
    }

    /** Liga o agendamento periodico da cobranca. */
    public void iniciar() {
        // Cria um executor com 1 thread agendada (suficiente para esta tarefa unica).
        executor = Executors.newSingleThreadScheduledExecutor();

        Runnable tarefa = () -> {
            // IMPORTANTE: capturamos QUALQUER excecao aqui dentro. No scheduleAtFixedRate,
            // se a tarefa lancar uma excecao nao tratada, o agendador PARA de repetir
            // (silenciosamente, sem erro visivel). O try/catch garante os proximos ciclos.
            try {
                System.out.println("[Agendador] Executando cobranca automatica...");
                ResumoCobranca r = cobrancaService.executarCobranca();
                System.out.println("[Agendador] " + r);
            } catch (Exception e) {
                System.err.println("[Agendador] Erro na cobranca: " + e.getMessage());
            }
        };

        // Parametros: (tarefa, atrasoInicial, periodo, unidade de tempo).
        // Agora esta a CADA 1 MINUTO (para voce testar).
        // >>> Para rodar 1x POR DIA, troque a linha abaixo por:
        //     executor.scheduleAtFixedRate(tarefa, 0, 1, TimeUnit.DAYS);
        executor.scheduleAtFixedRate(tarefa, 1, 1, TimeUnit.MINUTES);

        System.out.println("[Agendador] Cobranca automatica agendada (a cada 1 minuto).");
    }

    /** Encerra o agendador de forma ordenada (opcional; util em testes/desligamento). */
    public void parar() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
