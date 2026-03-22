package com.atm.handler;

import com.atm.service.AuthService;
import com.atm.util.AppException;
import com.atm.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class BaseHandler implements HttpHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public final void handle(HttpExchange ex) throws IOException {
        // CORS
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            ex.sendResponseHeaders(204, -1);
            return;
        }

        try {
            doHandle(ex);
        } catch (AppException e) {
            JsonUtil.sendError(ex, e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unhandled error", e);
            JsonUtil.sendError(ex, 500, "Lỗi hệ thống nội bộ");
        }
    }

    protected abstract void doHandle(HttpExchange ex) throws Exception;

    protected boolean requireMethod(HttpExchange ex, String method) throws IOException {
        if (!method.equalsIgnoreCase(ex.getRequestMethod())) {
            JsonUtil.sendError(ex, 405, "Method Not Allowed");
            return false;
        }
        return true;
    }

    protected Long authenticate(HttpExchange ex, AuthService authService) {
        String token = AuthService.extractToken(ex);
        return authService.resolveSession(token);
    }
}
