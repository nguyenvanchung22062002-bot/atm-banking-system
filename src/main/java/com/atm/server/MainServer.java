package com.atm.server;

import com.atm.config.AppConfig;
import com.atm.handler.Handlers;
import com.atm.scheduler.ReportScheduler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class MainServer {

    private static final Logger log = LoggerFactory.getLogger(MainServer.class);

    public static void main(String[] args) throws Exception {
        System.out.println("Starting ATM API Server...");

        AnnotationConfigApplicationContext ctx =
                new AnnotationConfigApplicationContext(AppConfig.class);

        java.util.Properties props = (java.util.Properties) ctx.getBean("appProperties");
        int port = Integer.parseInt(props.getProperty("server.port", "8080"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        Handlers h = ctx.getBean(Handlers.class);
        server.createContext("/api/auth/login",         h.login());
        server.createContext("/api/auth/logout",        h.logout());
        server.createContext("/api/account/info",       h.info());
        server.createContext("/api/account/deposit",    h.deposit());
        server.createContext("/api/account/withdraw",   h.withdraw());
        server.createContext("/api/account/change-pin", h.changePin());
        server.createContext("/api/account/history",    h.history());
        server.createContext("/api/report/monthly",     h.report());

        server.createContext("/", new StaticFileHandler());

        server.setExecutor(Executors.newFixedThreadPool(20));
        server.start();

        ctx.getBean(ReportScheduler.class);

        System.out.println("==============================================");
        System.out.println(" ATM API running at http://localhost:" + port);
        System.out.println(" Open browser: http://localhost:" + port + "/index.html");
        System.out.println("==============================================");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down ATM API...");
            server.stop(3);
            ctx.close();
        }));
    }
}