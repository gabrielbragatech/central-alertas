package com.centralalertas.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Funcoes de apoio para os handlers HTTP: ler o corpo da requisicao e escrever a
 * resposta em JSON. Centralizar aqui evita repetir esse codigo em cada handler.
 */
public class Http {

    private Http() {
    }

    /** Le todo o corpo da requisicao e devolve como texto UTF-8. */
    public static String lerCorpo(HttpExchange troca) throws IOException {
        // readAllBytes consome o stream inteiro; depois interpretamos os bytes como UTF-8.
        byte[] bytes = troca.getRequestBody().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Cabecalhos de CORS — a permissao para uma pagina de OUTRA origem (o front no Live Server,
     * porta 5500) chamar esta API (porta 8090). Sem isso o navegador bloqueia a chamada; "*"
     * libera qualquer origem (ok para dev local). Tem que ser chamado ANTES de sendResponseHeaders,
     * porque os cabecalhos vao antes do corpo.
     */
    public static void aplicarCors(HttpExchange troca) {
        troca.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        troca.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        troca.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    /**
     * Trata o "preflight": antes de um POST/PUT/DELETE com JSON, o navegador manda um
     * pedido OPTIONS perguntando se pode. Respondemos 204 (sem conteudo) com os cabecalhos
     * de CORS. Retorna true se ERA um OPTIONS (o handler deve entao parar com "return").
     */
    public static boolean tratarPreflight(HttpExchange troca) throws IOException {
        if ("OPTIONS".equals(troca.getRequestMethod())) {
            aplicarCors(troca);
            troca.sendResponseHeaders(204, -1); // 204 = No Content; -1 = sem corpo
            return true;
        }
        return false;
    }

    /** Serializa o objeto em JSON e envia a resposta com o status HTTP informado. */
    public static void enviarJson(HttpExchange troca, int status, Object corpo) throws IOException {
        byte[] bytes = Json.paraJson(corpo).getBytes(StandardCharsets.UTF_8);
        aplicarCors(troca); // toda resposta JSON sai liberada para o front (CORS)
        troca.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        // O 2o parametro e o tamanho do corpo em bytes (precisa ser > 0 para corpo fixo).
        troca.sendResponseHeaders(status, bytes.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }

    /** Atalho para responder um erro no formato {"erro": "..."}. */
    public static void enviarErro(HttpExchange troca, int status, String mensagem) throws IOException {
        enviarJson(troca, status, Map.of("erro", mensagem));
    }

    /**
     * Extrai o {id} que vem depois da base do caminho.
     * Ex.: path="/api/clientes/12", base="/api/clientes" -> 12L.
     * Retorna null quando NAO ha id (path == base, com ou sem "/" no fim) -> rota de colecao.
     * Lanca NumberFormatException se o trecho nao for numero (o handler trata como 400).
     */
    public static Long idDoCaminho(String path, String base) {
        String resto = path.substring(base.length());   // ex.: "/12", "", "/"
        resto = resto.replaceAll("^/+", "").replaceAll("/+$", ""); // tira barras das pontas
        if (resto.isEmpty()) {
            return null; // sem id -> rota de colecao
        }
        return Long.valueOf(resto); // pode lancar NumberFormatException -> vira 400
    }
}
