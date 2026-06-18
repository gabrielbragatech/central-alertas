package com.centralalertas.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Abre conexoes JDBC com o banco "central_alertas" (MySQL/MariaDB do XAMPP).
 * Os dados de conexao ficam fixos em constantes aqui mesmo.
 */
public class Conexao {

    // Dados do banco local (XAMPP). Parametros da URL:
    //  - useSSL=false               -> conexao local nao precisa de SSL (evita aviso).
    //  - allowPublicKeyRetrieval=true -> evita erro de chave publica em auth local.
    private static final String URL =
            "jdbc:mysql://localhost:3306/central_alertas?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USUARIO = "root";
    private static final String SENHA = ""; // root sem senha (padrao do XAMPP)

    // So tem metodo estatico aqui, entao nao faz sentido instanciar.
    private Conexao() {
    }

    /**
     * Abre e devolve UMA nova conexao com o banco.
     *
     * <p>Nao precisamos de {@code Class.forName(...)}: a partir do JDBC 4, o driver
     * presente no classpath (mysql-connector-j em lib/) se registra sozinho no
     * {@link DriverManager}. Quem chama deve FECHAR a conexao (use try-with-resources).</p>
     */
    public static Connection abrir() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, SENHA);
    }
}
