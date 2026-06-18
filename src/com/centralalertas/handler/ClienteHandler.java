package com.centralalertas.handler;

import com.centralalertas.dao.ClienteDAO;
import com.centralalertas.model.Cliente;
import com.centralalertas.util.Http;
import com.centralalertas.util.Json;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Rotas REST de cliente:
 *  - GET    /api/clientes        -> lista
 *  - POST   /api/clientes        -> cria
 *  - GET    /api/clientes/{id}   -> busca um
 *  - PUT    /api/clientes/{id}   -> atualiza
 *  - DELETE /api/clientes/{id}   -> exclui
 *
 * <p>O HttpServer casa o context por prefixo, entao este mesmo handler recebe tanto
 * "/api/clientes" quanto "/api/clientes/{id}"; decidimos a acao pelo metodo e por
 * existir ou nao um id no caminho.</p>
 */
public class ClienteHandler implements HttpHandler {

    private static final String BASE = "/api/clientes";
    private final ClienteDAO dao = new ClienteDAO();

    @Override
    public void handle(HttpExchange troca) throws IOException {
        // Responde o preflight (OPTIONS) e para por aqui se for o caso.
        if (Http.tratarPreflight(troca)) return;

        String metodo = troca.getRequestMethod();
        String path = troca.getRequestURI().getPath();

        try {
            Long id = Http.idDoCaminho(path, BASE);

            if (id == null) {
                // Rotas de COLECAO: /api/clientes
                switch (metodo) {
                    case "GET":  listar(troca);  break;
                    case "POST": criar(troca);   break;
                    default:     Http.enviarErro(troca, 405, "Metodo nao permitido em " + BASE);
                }
            } else {
                // Rotas de ITEM: /api/clientes/{id}
                switch (metodo) {
                    case "GET":    buscar(troca, id);    break;
                    case "PUT":    atualizar(troca, id); break;
                    case "DELETE": excluir(troca, id);   break;
                    default:       Http.enviarErro(troca, 405, "Metodo nao permitido em " + BASE + "/{id}");
                }
            }
        } catch (NumberFormatException | JsonSyntaxException e) {
            // id nao numerico no caminho ou JSON malformado no corpo -> requisicao invalida.
            Http.enviarErro(troca, 400, "Requisicao invalida: " + e.getMessage());
        } catch (SQLException e) {
            // SQLState que comeca com "23" = violacao de integridade (ex.: excluir cliente
            // que tem faturas vinculadas por chave estrangeira). Respondemos 409 (Conflito)
            // com uma mensagem clara, em vez de um 500 generico.
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                Http.enviarErro(troca, 409,
                        "Nao e possivel excluir: este cliente tem faturas vinculadas "
                        + "(exclua as faturas dele primeiro).");
            } else {
                Http.enviarErro(troca, 500, "Erro no banco: " + e.getMessage());
            }
        } catch (Exception e) {
            Http.enviarErro(troca, 500, "Erro inesperado: " + e.getMessage());
        }
    }

    private void listar(HttpExchange troca) throws IOException, SQLException {
        List<Cliente> lista = dao.listar();
        Http.enviarJson(troca, 200, lista);
    }

    private void criar(HttpExchange troca) throws IOException, SQLException {
        Cliente c = Json.deJson(Http.lerCorpo(troca), Cliente.class);
        if (c == null || vazio(c.getNome()) || vazio(c.getEmail())) {
            Http.enviarErro(troca, 400, "Campos obrigatorios: nome e email.");
            return;
        }
        c.setId(0); // ignoramos qualquer id vindo no corpo: o banco gera o id
        dao.inserir(c);
        Http.enviarJson(troca, 201, c); // 201 = criado
    }

    private void buscar(HttpExchange troca, long id) throws IOException, SQLException {
        Cliente c = dao.buscarPorId(id);
        if (c == null) {
            Http.enviarErro(troca, 404, "Cliente " + id + " nao encontrado.");
            return;
        }
        Http.enviarJson(troca, 200, c);
    }

    private void atualizar(HttpExchange troca, long id) throws IOException, SQLException {
        Cliente existente = dao.buscarPorId(id);
        if (existente == null) {
            Http.enviarErro(troca, 404, "Cliente " + id + " nao encontrado.");
            return;
        }
        Cliente c = Json.deJson(Http.lerCorpo(troca), Cliente.class);
        if (c == null || vazio(c.getNome()) || vazio(c.getEmail())) {
            Http.enviarErro(troca, 400, "Campos obrigatorios: nome e email.");
            return;
        }
        c.setId(id);                            // o id vem do caminho, nao do corpo
        c.setCriadoEm(existente.getCriadoEm()); // criado_em nao muda; mantemos p/ a resposta
        dao.atualizar(c);
        Http.enviarJson(troca, 200, c);
    }

    private void excluir(HttpExchange troca, long id) throws IOException, SQLException {
        Cliente existente = dao.buscarPorId(id);
        if (existente == null) {
            Http.enviarErro(troca, 404, "Cliente " + id + " nao encontrado.");
            return;
        }
        dao.excluir(id);
        Http.enviarJson(troca, 200, Map.of("mensagem", "Cliente " + id + " removido."));
    }

    // Ajuda a validar: true se a string for nula ou so espacos.
    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
