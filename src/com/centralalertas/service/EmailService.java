package com.centralalertas.service;

import com.centralalertas.dao.AlertaEnviadoDAO;
import com.centralalertas.model.AlertaEnviado;
import com.centralalertas.model.Fatura;
import com.centralalertas.model.TipoAlerta;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;

/**
 * Envia e-mails de cobranca usando o Jakarta Mail (SMTP do Gmail).
 *
 * <p>As credenciais (remetente e senha de app) vem do arquivo "config/email.properties",
 * que NAO esta no codigo nem no Git. Cada tentativa de envio e registrada na tabela
 * alerta_enviado (sucesso true/false).</p>
 */
public class EmailService {

    private static final String CONFIG_PATH = "config/email.properties";
    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String remetente;
    private final String nomeRemetente;
    private final Session session;
    private final AlertaEnviadoDAO alertaDao = new AlertaEnviadoDAO();

    public EmailService() {
        // Le as credenciais do arquivo de config (erro claro se faltar).
        Properties cfg = carregarConfig();
        this.remetente = exigir(cfg, "email.remetente");
        String senha = exigir(cfg, "email.senha");
        this.nomeRemetente = cfg.getProperty("email.remetente.nome", "Central de Alertas");

        // Propriedades da sessao SMTP do Gmail.
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");             // o servidor exige login
        props.put("mail.smtp.starttls.enable", "true");  // criptografa a conexao (TLS)

        // A Session guarda as propriedades + um Authenticator que fornece usuario/senha
        // quando o Gmail pedir autenticacao. A senha fica so nesta variavel local/closure,
        // nunca em campo publico nem no codigo-fonte.
        this.session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(remetente, senha);
            }
        });
    }

    /**
     * Envia o e-mail de cobranca de uma fatura e GRAVA o resultado em alerta_enviado.
     * Nunca lanca excecao para fora: captura falhas e devolve true/false.
     */
    public boolean enviar(Fatura fatura, TipoAlerta tipo) {
        boolean sucesso = false;
        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(remetente, nomeRemetente, "UTF-8"));
            msg.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(fatura.getCliente().getEmail()));
            msg.setSubject(assunto(fatura, tipo), "UTF-8");
            msg.setText(corpo(fatura, tipo), "UTF-8");

            Transport.send(msg); // conecta no SMTP, autentica e envia
            sucesso = true;
            System.out.println("[EmailService] Enviado para " + fatura.getCliente().getEmail()
                    + " (fatura " + fatura.getId() + ", " + tipo + ")");
        } catch (Exception e) {
            // MessagingException, UnsupportedEncodingException, etc. -> tratamos como falha.
            System.err.println("[EmailService] FALHA ao enviar e-mail da fatura " + fatura.getId()
                    + ": " + e.getMessage());
        }

        // Independente de ter dado certo ou nao, registramos a tentativa no log.
        try {
            alertaDao.inserir(new AlertaEnviado(fatura.getId(), tipo, "EMAIL", sucesso));
        } catch (Exception e) {
            System.err.println("[EmailService] FALHA ao gravar alerta_enviado da fatura "
                    + fatura.getId() + ": " + e.getMessage());
        }
        return sucesso;
    }

    // ----- montagem do assunto e do corpo (personalizados por cliente e tipo) -----

    private String assunto(Fatura f, TipoAlerta tipo) {
        if (tipo == TipoAlerta.ATRASADA) {
            return "Fatura em atraso: " + descricaoCurta(f);
        }
        return "Sua fatura vence em breve: " + descricaoCurta(f);
    }

    private String corpo(Fatura f, TipoAlerta tipo) {
        String nome = f.getCliente().getNome();
        String valor = NumberFormat.getCurrencyInstance(BR).format(f.getValor()); // ex.: R$ 199,90
        String venc = f.getDataVencimento().format(DATA_BR);                       // ex.: 16/06/2026
        String desc = (f.getDescricao() != null && !f.getDescricao().isBlank())
                ? f.getDescricao() : "(sem descricao)";

        String situacao = (tipo == TipoAlerta.ATRASADA)
                ? "Identificamos que a fatura abaixo esta ATRASADA (venceu em " + venc + ")."
                : "Passando para lembrar que a fatura abaixo vence em " + venc + ".";

        return "Ola, " + nome + "!\n\n"
                + situacao + "\n\n"
                + "Descricao: " + desc + "\n"
                + "Valor: " + valor + "\n"
                + "Vencimento: " + venc + "\n\n"
                + "Por favor, efetue o pagamento o quanto antes.\n\n"
                + "Atenciosamente,\n" + nomeRemetente;
    }

    private String descricaoCurta(Fatura f) {
        return (f.getDescricao() != null && !f.getDescricao().isBlank())
                ? f.getDescricao() : ("fatura #" + f.getId());
    }

    // ----- leitura do arquivo de configuracao -----

    private Properties carregarConfig() {
        Properties cfg = new Properties();
        try (InputStream in = new FileInputStream(CONFIG_PATH)) {
            cfg.load(in); // formato chave=valor
        } catch (IOException e) {
            throw new IllegalStateException("Nao consegui ler " + CONFIG_PATH
                    + " (copie de config/email.properties.exemplo e preencha). Detalhe: " + e.getMessage(), e);
        }
        return cfg;
    }

    private String exigir(Properties cfg, String chave) {
        String v = cfg.getProperty(chave);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Chave obrigatoria ausente em " + CONFIG_PATH + ": " + chave);
        }
        return v;
    }
}
