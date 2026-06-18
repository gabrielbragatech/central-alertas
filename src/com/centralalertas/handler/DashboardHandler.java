package com.centralalertas.handler;

import com.centralalertas.dao.FaturaDAO;
import com.centralalertas.model.Fatura;
import com.centralalertas.model.StatusFatura;
import com.centralalertas.util.Http;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Rota do painel "Saude Financeira":
 *  - GET /api/dashboard/resumo -> numeros agregados das faturas (em JSON).
 *
 * <p>Para os totais, lemos todas as faturas com o FaturaDAO e somamos/contamos em Java
 * por status (simples e suficiente para o MVP).</p>
 */
public class DashboardHandler implements HttpHandler {

    private final FaturaDAO faturaDao = new FaturaDAO();

    /** Formato da resposta JSON do resumo financeiro. */
    private static class ResumoFinanceiro {
        BigDecimal totalAReceber; // soma do valor das faturas PENDENTES
        BigDecimal totalAtrasado; // soma do valor das faturas ATRASADAS
        int pendentes;            // quantidade de faturas PENDENTES
        int atrasadas;            // quantidade de faturas ATRASADAS
    }

    @Override
    public void handle(HttpExchange troca) throws IOException {
        // Responde o preflight (OPTIONS) e para por aqui se for o caso.
        if (Http.tratarPreflight(troca)) return;

        try {
            if (!"GET".equals(troca.getRequestMethod())) {
                Http.enviarErro(troca, 405, "Use GET em /api/dashboard/resumo.");
                return;
            }
            Http.enviarJson(troca, 200, calcularResumo());
        } catch (SQLException e) {
            Http.enviarErro(troca, 500, "Erro no banco: " + e.getMessage());
        } catch (Exception e) {
            Http.enviarErro(troca, 500, "Erro inesperado: " + e.getMessage());
        }
    }

    // Le todas as faturas e agrega os numeros por status.
    private ResumoFinanceiro calcularResumo() throws SQLException {
        ResumoFinanceiro r = new ResumoFinanceiro();
        r.totalAReceber = BigDecimal.ZERO;
        r.totalAtrasado = BigDecimal.ZERO;

        List<Fatura> faturas = faturaDao.listar();
        for (Fatura f : faturas) {
            if (f.getStatus() == StatusFatura.PENDENTE) {
                r.totalAReceber = r.totalAReceber.add(f.getValor());
                r.pendentes++;
            } else if (f.getStatus() == StatusFatura.ATRASADA) {
                r.totalAtrasado = r.totalAtrasado.add(f.getValor());
                r.atrasadas++;
            }
            // PAGA nao entra em nenhum dos totais.
        }
        return r;
    }
}
