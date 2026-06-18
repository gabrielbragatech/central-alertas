package com.centralalertas.dao;

import com.centralalertas.model.Cliente;
import com.centralalertas.model.Fatura;
import com.centralalertas.model.StatusFatura;
import com.centralalertas.util.Conexao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso a tabela "fatura" com JDBC puro.
 *
 * <p>As consultas que leem faturas fazem JOIN com "cliente" para ja trazer o objeto
 * {@link Cliente} preenchido (precisaremos do e-mail dele para os alertas).</p>
 */
public class FaturaDAO {

    // SELECT base reutilizado pelas leituras. Usamos APELIDOS (AS) nas colunas porque
    // tanto fatura quanto cliente tem uma coluna "id" e uma "criado_em": os apelidos
    // (f_... e c_...) evitam ambiguidade na hora de ler o ResultSet.
    private static final String SELECT_BASE =
            "SELECT f.id AS f_id, f.descricao AS f_descricao, f.valor AS f_valor, "
          + "       f.data_vencimento AS f_data_vencimento, f.status AS f_status, "
          + "       f.criado_em AS f_criado_em, "
          + "       c.id AS c_id, c.nome AS c_nome, c.email AS c_email, "
          + "       c.telefone AS c_telefone, c.criado_em AS c_criado_em "
          + "FROM fatura f "
          + "JOIN cliente c ON c.id = f.cliente_id ";

    /**
     * Insere uma fatura nova. Retorna o id gerado (e tambem o seta no objeto).
     * Usa o id do cliente associado (fatura.getCliente().getId()).
     */
    public long inserir(Fatura fatura) throws SQLException {
        String sql = "INSERT INTO fatura (cliente_id, descricao, valor, data_vencimento, status, criado_em) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection con = Conexao.abrir();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Valores padrao quando o objeto nao trouxe (status PENDENTE, criado_em agora).
            StatusFatura status = (fatura.getStatus() != null) ? fatura.getStatus() : StatusFatura.PENDENTE;
            LocalDateTime criadoEm = (fatura.getCriadoEm() != null) ? fatura.getCriadoEm() : LocalDateTime.now();

            ps.setLong(1, fatura.getCliente().getId());
            ps.setString(2, fatura.getDescricao());
            ps.setBigDecimal(3, fatura.getValor());            // DECIMAL <-> BigDecimal
            ps.setObject(4, fatura.getDataVencimento());        // LocalDate -> DATE
            ps.setString(5, status.name());                     // enum gravado como texto
            ps.setObject(6, criadoEm);                          // LocalDateTime -> DATETIME

            ps.executeUpdate();

            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (chaves.next()) {
                    fatura.setId(chaves.getLong(1));
                }
            }
            // Reflete no objeto o que foi gravado.
            fatura.setStatus(status);
            fatura.setCriadoEm(criadoEm);
            return fatura.getId();
        }
    }

    /** Lista todas as faturas (com o cliente preenchido), ordenadas pelo id. */
    public List<Fatura> listar() throws SQLException {
        String sql = SELECT_BASE + "ORDER BY f.id";

        try (Connection con = Conexao.abrir();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Fatura> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        }
    }

    /** Busca uma fatura pelo id (com o cliente). Retorna o objeto ou {@code null}. */
    public Fatura buscarPorId(long id) throws SQLException {
        String sql = SELECT_BASE + "WHERE f.id = ?";

        try (Connection con = Conexao.abrir();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Atualiza os campos da fatura. Retorna true se alguma linha foi alterada. */
    public boolean atualizar(Fatura fatura) throws SQLException {
        String sql = "UPDATE fatura SET cliente_id = ?, descricao = ?, valor = ?, "
                   + "data_vencimento = ?, status = ? WHERE id = ?";

        try (Connection con = Conexao.abrir();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, fatura.getCliente().getId());
            ps.setString(2, fatura.getDescricao());
            ps.setBigDecimal(3, fatura.getValor());
            ps.setObject(4, fatura.getDataVencimento());
            ps.setString(5, fatura.getStatus().name());
            ps.setLong(6, fatura.getId());

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Exclui a fatura pelo id, junto com os alertas vinculados a ela. Retorna true se a
     * fatura foi removida.
     *
     * <p>Apago primeiro os alerta_enviado (senao a FK fk_alerta_fatura barra) e depois a
     * fatura. Os dois DELETE ficam numa transacao: ligo setAutoCommit(false), faco os
     * dois e dou commit() no fim; se algo falha no meio, rollback() desfaz tudo — assim
     * nunca sobra alerta orfao.</p>
     */
    public boolean excluir(long id) throws SQLException {
        String delAlertas = "DELETE FROM alerta_enviado WHERE fatura_id = ?";
        String delFatura  = "DELETE FROM fatura WHERE id = ?";

        try (Connection con = Conexao.abrir()) {
            con.setAutoCommit(false); // comeca a transacao
            try (PreparedStatement psAlertas = con.prepareStatement(delAlertas);
                 PreparedStatement psFatura  = con.prepareStatement(delFatura)) {

                psAlertas.setLong(1, id);
                psAlertas.executeUpdate();

                psFatura.setLong(1, id);
                int removidas = psFatura.executeUpdate();

                con.commit();
                return removidas > 0;
            } catch (SQLException e) {
                con.rollback(); // deu errado: desfaz os deletes ja feitos
                throw e;
            }
        }
    }

    /**
     * Faturas que vao vencer em "diasAntes" dias e ainda estao PENDENTES.
     * Ex.: diasAntes = 2 -> faturas pendentes cujo vencimento e exatamente daqui a 2 dias.
     * Servem para o alerta de VENCIMENTO_PROXIMO.
     */
    public List<Fatura> listarPendentesAVencer(int diasAntes) throws SQLException {
        String sql = SELECT_BASE
                   + "WHERE f.status = 'PENDENTE' AND f.data_vencimento = ? "
                   + "ORDER BY f.id";

        try (Connection con = Conexao.abrir();
             PreparedStatement ps = con.prepareStatement(sql)) {

            // A data-alvo e calculada em Java e passada como parametro (sem concatenar SQL).
            ps.setObject(1, LocalDate.now().plusDays(diasAntes));
            try (ResultSet rs = ps.executeQuery()) {
                List<Fatura> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    /**
     * Faturas vencidas: vencimento ANTES de hoje e que ainda nao foram pagas
     * (status diferente de PAGA). Servem para o alerta de fatura ATRASADA.
     */
    public List<Fatura> listarVencidas() throws SQLException {
        String sql = SELECT_BASE
                   + "WHERE f.data_vencimento < ? AND f.status <> 'PAGA' "
                   + "ORDER BY f.data_vencimento";

        try (Connection con = Conexao.abrir();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setObject(1, LocalDate.now());
            try (ResultSet rs = ps.executeQuery()) {
                List<Fatura> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    /** Monta uma Fatura (com o Cliente aninhado) a partir da linha atual do ResultSet. */
    private Fatura mapear(ResultSet rs) throws SQLException {
        // Primeiro o cliente (colunas com apelido c_...).
        Cliente cliente = new Cliente();
        cliente.setId(rs.getLong("c_id"));
        cliente.setNome(rs.getString("c_nome"));
        cliente.setEmail(rs.getString("c_email"));
        cliente.setTelefone(rs.getString("c_telefone"));
        cliente.setCriadoEm(rs.getObject("c_criado_em", LocalDateTime.class));

        // Depois a fatura (colunas com apelido f_...).
        Fatura f = new Fatura();
        f.setId(rs.getLong("f_id"));
        f.setCliente(cliente);
        f.setDescricao(rs.getString("f_descricao"));
        f.setValor(rs.getBigDecimal("f_valor"));
        f.setDataVencimento(rs.getObject("f_data_vencimento", LocalDate.class));
        // Texto do banco -> enum. Se vier um valor fora do enum, valueOf lanca (integridade).
        f.setStatus(StatusFatura.valueOf(rs.getString("f_status")));
        f.setCriadoEm(rs.getObject("f_criado_em", LocalDateTime.class));
        return f;
    }
}
