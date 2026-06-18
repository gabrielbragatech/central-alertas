package com.centralalertas.handler;

import com.centralalertas.util.Http;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;

/**
 * Rota de saude (health check): confirma rapidamente que o servidor esta no ar.
 * Responde via Http.enviarJson, entao ja sai com os cabecalhos de CORS.
 */
public class HealthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange troca) throws IOException {
        // Responde o preflight (OPTIONS) e para por aqui se for o caso.
        if (Http.tratarPreflight(troca)) return;

        // Map.of vira o JSON {"status":"ok","app":"central-alertas"}.
        Http.enviarJson(troca, 200, Map.of("status", "ok", "app", "central-alertas"));
    }
}
