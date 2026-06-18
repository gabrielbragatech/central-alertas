package com.centralalertas.dao;

import com.centralalertas.model.Cliente;
import com.centralalertas.util.Conexao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso a tabela "cliente" com JDBC puro.
 *
 * <p>Padrao usado em todos os metodos:
 * abrimos a conexao com try-with-resources (que fecha sozinho), usamos
 * {@link PreparedStatement} (parametros com "?", protege contra SQL Injection) e
 * deixamos a {@link SQLException} subir para quem chamou tratar.</p>
 */
public class ClienteDAO {

    /**
     * Insere um cliente novo. Retorna o id gerado pelo banco (e tambem o seta no objeto).
     */
    public long inserir(Cliente cliente) throws SQLException {
        String sql = "INSERT INTO cliente (nome, email, telefone, criado_em) VALUES (?, ?, ?, ?)";

        try (Connection con = Conexao.abrir();
             // RETURN_GENERATED_KEYS: pedimos ao banco a chave (id) gerada no AUTO_INCREMENT.
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // criado_em e NOT NULL no banco: se o objeto nao trouxe, usamos o instante atual.
            LocalDateTime criadoEm = (cliente.getCriadoEm() != null)
                    ? cliente.getCriadoEm() : LocalDateTime.now();

            ps.setString(1, cliente.getNome());
            ps.setString(2, cliente.getEmail());
            ps.setString(3, cliente.getTelefone()); // se for null, vira NULL no banco
            ps.setObject(4, criadoEm);              // LocalDateTime -> DATETIME (Connector/J 8+)

            ps.executeUpdate();

            // Le a chave gerada e guarda no proprio objeto.
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (chaves.next()) {
                    cliente.setId(chaves.getLong(1));
                }
            }
            cliente.setCriadoEm(criadoEm); // reflete no objeto o que foi de fato gravado
            return cliente.getId();
        }
    }

    /** Lista todos os clientes, ordenados pelo id. */
    public List<Cliente> listar() throws SQLException {
        String sql = "SELECT id, nome, email, telefone, criado_em FROM cliente ORDER BY id";

        try (Connection con = Conexao.abrir();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Cliente> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        }
    }

    /** Busca um cliente pelo id. Retorna o objeto ou {@code null} se nao existir. */
    public Cliente buscarPorId(long id) throws SQLException {
        String sql = "SELECT id, nome, email, telefone, criado_em FROM cliente WHERE id = ?";

        try (Connection con = Conexao.abrir();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /**
     * Atualiza nome, email e telefone do cliente (criado_em nao muda).
     * Retorna true se alguma linha foi alterada (ou seja, se o id existia).
     */
    public boolean atualizar(Cliente cliente) throws SQLException {
        String sql = "UPDATE cliente SET nome = ?, email = ?, telefone = ? WHERE id = ?";

        try (Connection con = Conexao.abrir();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, cliente.getNome());
            ps.setString(2, cliente.getEmail());
            ps.setString(3, cliente.getTelefone());
            ps.setLong(4, cliente.getId());

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Exclui o cliente pelo id, junto com tudo que depende dele: os alertas das faturas
     * dele, as faturas e, por fim, o cliente. Retorna true se o cliente foi removido.
     *
     * <p>Sigo a ordem filho -> pai (alerta_enviado, fatura, cliente) para nao esbarrar nas
     * FKs. Os tres DELETE ficam numa transacao (setAutoCommit(false) + commit() no fim;
     * rollback() no catch), entao ou apaga tudo ou nada — sem deixar fatura/alerta orfaos.</p>
     */
    public boolean excluir(long id) throws SQLException {
        // O primeiro DELETE apaga de alerta_enviado usando uma subconsulta em fatura
        // (tabelas diferentes, entao o MySQL permite) para pegar os alertas das faturas deste cliente.
        String delAlertas = "DELETE FROM alerta_enviado WHERE fatura_id IN (SELECT id FROM fatura WHERE cliente_id = ?)";
        String delFaturas = "DELETE FROM fatura WHERE cliente_id = ?";
        String delCliente = "DELETE FROM cliente WHERE id = ?";

        try (Connection con = Conexao.abrir()) {
            con.setAutoCommit(false); // comeca a transacao
            try (PreparedStatement psAlertas = con.prepareStatement(delAlertas);
                 PreparedStatement psFaturas = con.prepareStatement(delFaturas);
                 PreparedStatement psCliente = con.prepareStatement(delCliente)) {

                psAlertas.setLong(1, id);
                psAlertas.executeUpdate();

                psFaturas.setLong(1, id);
                psFaturas.executeUpdate();

                psCliente.setLong(1, id);
                int removidos = psCliente.executeUpdate();

                con.commit();
                return removidos > 0;
            } catch (SQLException e) {
                con.rollback(); // deu errado: desfaz os deletes ja feitos
                throw e;
            }
        }
    }

    /** Converte a linha atual do ResultSet em um objeto Cliente. */
    private Cliente mapear(ResultSet rs) throws SQLException {
        Cliente c = new Cliente();
        c.setId(rs.getLong("id"));
        c.setNome(rs.getString("nome"));
        c.setEmail(rs.getString("email"));
        c.setTelefone(rs.getString("telefone"));
        c.setCriadoEm(rs.getObject("criado_em", LocalDateTime.class)); // DATETIME -> LocalDateTime
        return c;
    }
}
