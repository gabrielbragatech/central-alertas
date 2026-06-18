package com.centralalertas.service;

import com.centralalertas.dao.AlertaEnviadoDAO;
import com.centralalertas.dao.FaturaDAO;
import com.centralalertas.model.Fatura;
import com.centralalertas.model.StatusFatura;
import com.centralalertas.model.TipoAlerta;

import java.sql.SQLException;
import java.util.List;

/**
 * Regras de cobranca: descobre quais faturas precisam de alerta e dispara os e-mails.
 *
 * <p>Recebe os DAOs e o EmailService prontos (injecao por construtor) — assim a classe
 * nao se preocupa em criar suas dependencias e fica facil de testar.</p>
 */
public class CobrancaService {

    private final FaturaDAO faturaDao;
    private final AlertaEnviadoDAO alertaDao;
    private final EmailService emailService;

    public CobrancaService(FaturaDAO faturaDao, AlertaEnviadoDAO alertaDao, EmailService emailService) {
        this.faturaDao = faturaDao;
        this.alertaDao = alertaDao;
        this.emailService = emailService;
    }

    /**
     * Executa um ciclo de cobranca:
     *  1) busca faturas a vencer (em 2 dias) e vencidas;
     *  2) marca as vencidas como ATRASADA;
     *  3) envia o e-mail de cada uma (sem repetir o que ja saiu com sucesso hoje).
     * Retorna um resumo do que aconteceu.
     */
    public ResumoCobranca executarCobranca() throws SQLException {
        ResumoCobranca resumo = new ResumoCobranca();

        List<Fatura> aVencer = faturaDao.listarPendentesAVencer(2);
        List<Fatura> vencidas = faturaDao.listarVencidas();
        resumo.setAVencer(aVencer.size());
        resumo.setVencidas(vencidas.size());

        // 1) Atualiza o status das vencidas para ATRASADA (se ainda nao estiverem).
        for (Fatura f : vencidas) {
            if (f.getStatus() != StatusFatura.ATRASADA) {
                f.setStatus(StatusFatura.ATRASADA);
                faturaDao.atualizar(f);
            }
        }

        // 2) Dispara os alertas. Cada lista usa um tipo de alerta diferente.
        processar(aVencer, TipoAlerta.VENCIMENTO_PROXIMO, resumo);
        processar(vencidas, TipoAlerta.ATRASADA, resumo);

        return resumo;
    }

    // Envia o alerta de cada fatura da lista, respeitando o "nao repetir no mesmo dia".
    private void processar(List<Fatura> faturas, TipoAlerta tipo, ResumoCobranca resumo) throws SQLException {
        for (Fatura f : faturas) {
            String alvo = f.getCliente().getEmail();

            // Se ja houve um alerta COM SUCESSO hoje para esta fatura, nao reenvia.
            if (alertaDao.existeAlertaHoje(f.getId())) {
                resumo.incPulados();
                resumo.addDetalhe("Fatura #" + f.getId() + " [" + tipo + "] " + alvo
                        + " -> PULADO (ja enviado hoje)");
                continue;
            }

            boolean ok = emailService.enviar(f, tipo); // envia E grava o log (sucesso/falha)
            if (ok) {
                resumo.incEnviados();
                resumo.addDetalhe("Fatura #" + f.getId() + " [" + tipo + "] " + alvo + " -> ENVIADO");
            } else {
                resumo.incFalhas();
                resumo.addDetalhe("Fatura #" + f.getId() + " [" + tipo + "] " + alvo + " -> FALHA");
            }
        }
    }
}
