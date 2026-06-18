package com.centralalertas.handler;

import com.centralalertas.service.CobrancaService;
import com.centralalertas.service.ResumoCobranca;
import com.centralalertas.util.Http;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Rota para disparar a cobranca manualmente, sem esperar o agendador:
 *  - POST /api/cobrancas/disparar -> executa agora e devolve o resumo em JSON.
 *
 * <p>Recebe o CobrancaService pronto (injetado pelo Main).</p>
 */
public class CobrancaHandler implements HttpHandler {

    private final CobrancaService cobrancaService;

    public CobrancaHandler(CobrancaService cobrancaService) {
        this.cobrancaService = cobrancaService;
    }

    @Override
    public void handle(HttpExchange troca) throws IOException {
        // Responde o preflight (OPTIONS) e para por aqui se for o caso.
        if (Http.tratarPreflight(troca)) return;

        try {
            // So aceitamos POST: disparar uma cobranca e uma acao que muda estado.
            if (!"POST".equals(troca.getRequestMethod())) {
                Http.enviarErro(troca, 405, "Use POST em /api/cobrancas/disparar.");
                return;
            }
            ResumoCobranca resumo = cobrancaService.executarCobranca();
            Http.enviarJson(troca, 200, resumo);
        } catch (SQLException e) {
            Http.enviarErro(troca, 500, "Erro no banco: " + e.getMessage());
        } catch (Exception e) {
            Http.enviarErro(troca, 500, "Erro inesperado: " + e.getMessage());
        }
    }
}
