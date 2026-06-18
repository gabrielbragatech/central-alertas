package com.centralalertas.dao;

import com.centralalertas.model.AlertaEnviado;
import com.centralalertas.util.Conexao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Acesso a tabela "alerta_enviado" (log dos alertas de cobranca enviados).
 */
public class AlertaEnviadoDAO {

    /**
     * Registra um alerta enviado. Retorna o id gerado (e tambem o seta no objeto).
     */
    public long inserir(AlertaEnviado alerta) throws SQLException {
        String sql = "INSERT INTO alerta_enviado (fatura_id, tipo, canal, enviado_em, sucesso) "
                   + "VALUES (?, ?, ?, ?, ?)";

        try (Connection con = Conexao.abrir();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // enviado_em e NOT NULL: se o objeto nao trouxe, usamos o instante atual.
            LocalDateTime enviadoEm = (alerta.getEnviadoEm() != null)
                    ? alerta.getEnviadoEm() : LocalDateTime.now();

            ps.setLong(1, alerta.getFaturaId());
            ps.setString(2, alerta.getTipo().name()); // enum gravado como texto
            ps.setString(3, alerta.getCanal());
            ps.setObject(4, enviadoEm);
            ps.setBoolean(5, alerta.isSucesso());      // boolean <-> TINYINT(1)

            ps.executeUpdate();

            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (chaves.next()) {
                    alerta.setId(chaves.getLong(1));
                }
            }
            alerta.setEnviadoEm(enviadoEm);
            return alerta.getId();
        }
    }

    /**
     * Diz se JA existe um alerta enviado COM SUCESSO para esta fatura HOJE.
     *
     * <p>Serve para nao enviar o mesmo alerta varias vezes no mesmo dia. Consideramos
     * apenas envios bem-sucedidos (sucesso = 1): assim, uma tentativa que falhou nao
     * bloqueia uma nova tentativa no proximo ciclo.
     * DATE(enviado_em) descarta a hora e compara so a data; CURDATE() e a data de hoje
     * no banco. Retorna true se encontrou pelo menos uma linha.</p>
     */
    public boolean existeAlertaHoje(long faturaId) throws SQLException {
        String sql = "SELECT 1 FROM alerta_enviado "
                   + "WHERE fatura_id = ? AND sucesso = 1 AND DATE(enviado_em) = CURDATE() LIMIT 1";

        try (Connection con = Conexao.abrir();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, faturaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // se ha linha, existe alerta hoje
            }
        }
    }
}
