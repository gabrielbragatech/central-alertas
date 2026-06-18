package com.centralalertas;

import com.centralalertas.dao.AlertaEnviadoDAO;
import com.centralalertas.dao.FaturaDAO;
import com.centralalertas.handler.ClienteHandler;
import com.centralalertas.handler.CobrancaHandler;
import com.centralalertas.handler.DashboardHandler;
import com.centralalertas.handler.FaturaHandler;
import com.centralalertas.handler.HealthHandler;
import com.centralalertas.service.Agendador;
import com.centralalertas.service.CobrancaService;
import com.centralalertas.service.EmailService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Ponto de entrada: sobe o servidor HTTP do proprio JDK, registra todas as rotas
 * e liga o agendador da cobranca automatica.
 */
public class Main {

    // Porta padrao. Usamos 8090 porque a 8080 (mais comum) costuma estar ocupada
    // por outros servicos. Para usar OUTRA porta, passe-a como 1o argumento, ex.:
    //   java -cp "out;lib/*" com.centralalertas.Main 9000
    private static final int PORTA_PADRAO = 8090;

    public static void main(String[] args) throws IOException {
        // Se um argumento foi passado, ele e a porta; caso contrario usa a padrao.
        int porta = (args.length > 0) ? Integer.parseInt(args[0]) : PORTA_PADRAO;

        // O HttpServer ja vem no JDK, entao nao preciso de framework. O segundo argumento
        // (0) e o backlog da fila de conexoes — deixo o sistema operacional decidir.
        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);

        // Monto os services na mao e passo as dependencias pelo construtor.
        // O EmailService le config/email.properties ja no construtor, entao esse arquivo
        // precisa existir (copie de config/email.properties.exemplo).
        EmailService emailService = new EmailService();
        CobrancaService cobrancaService = new CobrancaService(
                new FaturaDAO(), new AlertaEnviadoDAO(), emailService);

        // Registra as ROTAS (contexts). O HttpServer casa por PREFIXO: o context
        // "/api/clientes" atende tanto "/api/clientes" quanto "/api/clientes/{id}".
        servidor.createContext("/api/health", new HealthHandler());
        servidor.createContext("/api/clientes", new ClienteHandler());
        servidor.createContext("/api/faturas", new FaturaHandler());
        servidor.createContext("/api/dashboard/resumo", new DashboardHandler());
        servidor.createContext("/api/cobrancas/disparar", new CobrancaHandler(cobrancaService));

        // Pool de 10 threads para atender varias requisicoes em paralelo
        // (com setExecutor(null) seria tudo numa thread so).
        servidor.setExecutor(Executors.newFixedThreadPool(10));

        // start() nao bloqueia: o servidor passa a escutar em background e o programa
        // segue vivo por causa das threads dele.
        servidor.start();

        // Liga o agendador da cobranca automatica (roda em thread propria, em segundo plano).
        new Agendador(cobrancaService).iniciar();

        String base = "http://localhost:" + porta;
        System.out.println("Servidor em " + base);
        System.out.println("Rotas disponiveis:");
        System.out.println("  GET    " + base + "/api/health");
        System.out.println("  GET/POST            " + base + "/api/clientes");
        System.out.println("  GET/PUT/DELETE      " + base + "/api/clientes/{id}");
        System.out.println("  GET/POST            " + base + "/api/faturas");
        System.out.println("  GET/PUT/DELETE      " + base + "/api/faturas/{id}");
        System.out.println("  GET    " + base + "/api/dashboard/resumo");
        System.out.println("  POST   " + base + "/api/cobrancas/disparar");
    }
}
