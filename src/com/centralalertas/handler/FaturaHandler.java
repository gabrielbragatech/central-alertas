package com.centralalertas.handler;

import com.centralalertas.dao.ClienteDAO;
import com.centralalertas.dao.FaturaDAO;
import com.centralalertas.model.Cliente;
import com.centralalertas.model.Fatura;
import com.centralalertas.model.StatusFatura;
import com.centralalertas.util.Http;
import com.centralalertas.util.Json;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Rotas REST de fatura (mesma estrutura do ClienteHandler).
 *
 * <p>No POST/PUT o corpo traz o cliente como {@code clienteId} (e nao um objeto aninhado).
 * Buscamos esse cliente no banco para validar que existe e para associa-lo a fatura;
 * a resposta sai com o cliente completo aninhado (os SELECT do FaturaDAO ja fazem JOIN).</p>
 */
public class FaturaHandler implements HttpHandler {

    private static final String BASE = "/api/faturas";
    private final FaturaDAO faturaDao = new FaturaDAO();
    private final ClienteDAO clienteDao = new ClienteDAO();

    /**
     * Formato esperado do corpo (JSON) ao criar/editar uma fatura. O Gson preenche estes
     * campos a partir do JSON; "status" e opcional. Usamos um DTO (e nao a Fatura direto)
     * porque a Fatura guarda um objeto Cliente, e aqui recebemos apenas o clienteId.
     */
    private static class FaturaRequest {
        long clienteId;
        String descricao;
        BigDecimal valor;
        LocalDate dataVencimento;
        StatusFatura status; // opcional
    }

    @Override
    public void handle(HttpExchange troca) throws IOException {
        // Responde o preflight (OPTIONS) e para por aqui se for o caso.
        if (Http.tratarPreflight(troca)) return;

        String metodo = troca.getRequestMethod();
        String path = troca.getRequestURI().getPath();

        try {
            Long id = Http.idDoCaminho(path, BASE);

            if (id == null) {
                switch (metodo) {
                    case "GET":  listar(troca); break;
                    case "POST": criar(troca);  break;
                    default:     Http.enviarErro(troca, 405, "Metodo nao permitido em " + BASE);
                }
            } else {
                switch (metodo) {
                    case "GET":    buscar(troca, id);    break;
                    case "PUT":    atualizar(troca, id); break;
                    case "DELETE": excluir(troca, id);   break;
                    default:       Http.enviarErro(troca, 405, "Metodo nao permitido em " + BASE + "/{id}");
                }
            }
        } catch (NumberFormatException | JsonSyntaxException e) {
            Http.enviarErro(troca, 400, "Requisicao invalida: " + e.getMessage());
        } catch (SQLException e) {
            // SQLState que comeca com "23" = violacao de integridade (ex.: excluir fatura
            // que ja tem alertas de cobranca vinculados). Respondemos 409 (Conflito).
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                Http.enviarErro(troca, 409,
                        "Nao e possivel excluir: esta fatura ja tem alertas de cobranca vinculados.");
            } else {
                Http.enviarErro(troca, 500, "Erro no banco: " + e.getMessage());
            }
        } catch (Exception e) {
            Http.enviarErro(troca, 500, "Erro inesperado: " + e.getMessage());
        }
    }

    private void listar(HttpExchange troca) throws IOException, SQLException {
        List<Fatura> lista = faturaDao.listar();
        Http.enviarJson(troca, 200, lista);
    }

    private void criar(HttpExchange troca) throws IOException, SQLException {
        FaturaRequest req = Json.deJson(Http.lerCorpo(troca), FaturaRequest.class);
        String erro = validar(req);
        if (erro != null) {
            Http.enviarErro(troca, 400, erro);
            return;
        }
        Cliente cliente = clienteDao.buscarPorId(req.clienteId);
        if (cliente == null) {
            Http.enviarErro(troca, 400, "Cliente " + req.clienteId + " nao existe.");
            return;
        }
        Fatura f = new Fatura(cliente, req.descricao, req.valor, req.dataVencimento);
        if (req.status != null) {
            f.setStatus(req.status); // se nao veio, o DAO grava PENDENTE por padrao
        }
        faturaDao.inserir(f);
        Http.enviarJson(troca, 201, f);
    }

    private void buscar(HttpExchange troca, long id) throws IOException, SQLException {
        Fatura f = faturaDao.buscarPorId(id);
        if (f == null) {
            Http.enviarErro(troca, 404, "Fatura " + id + " nao encontrada.");
            return;
        }
        Http.enviarJson(troca, 200, f);
    }

    private void atualizar(HttpExchange troca, long id) throws IOException, SQLException {
        Fatura existente = faturaDao.buscarPorId(id);
        if (existente == null) {
            Http.enviarErro(troca, 404, "Fatura " + id + " nao encontrada.");
            return;
        }
        FaturaRequest req = Json.deJson(Http.lerCorpo(troca), FaturaRequest.class);
        String erro = validar(req);
        if (erro != null) {
            Http.enviarErro(troca, 400, erro);
            return;
        }
        Cliente cliente = clienteDao.buscarPorId(req.clienteId);
        if (cliente == null) {
            Http.enviarErro(troca, 400, "Cliente " + req.clienteId + " nao existe.");
            return;
        }
        Fatura f = new Fatura(cliente, req.descricao, req.valor, req.dataVencimento);
        f.setId(id);                                   // id vem do caminho
        // Se o status nao veio no corpo, mantemos o que ja estava gravado.
        f.setStatus(req.status != null ? req.status : existente.getStatus());
        f.setCriadoEm(existente.getCriadoEm());        // criado_em nao muda; para a resposta
        faturaDao.atualizar(f);
        Http.enviarJson(troca, 200, f);
    }

    private void excluir(HttpExchange troca, long id) throws IOException, SQLException {
        Fatura existente = faturaDao.buscarPorId(id);
        if (existente == null) {
            Http.enviarErro(troca, 404, "Fatura " + id + " nao encontrada.");
            return;
        }
        faturaDao.excluir(id);
        Http.enviarJson(troca, 200, Map.of("mensagem", "Fatura " + id + " removida."));
    }

    /** Valida o corpo. Retorna null se estiver ok, ou a mensagem de erro. */
    private String validar(FaturaRequest req) {
        if (req == null) return "Corpo da requisicao vazio ou invalido.";
        if (req.clienteId <= 0) return "Campo obrigatorio: clienteId (> 0).";
        if (req.valor == null) return "Campo obrigatorio: valor.";
        if (req.dataVencimento == null) return "Campo obrigatorio: dataVencimento (formato yyyy-MM-dd).";
        return null;
    }
}
