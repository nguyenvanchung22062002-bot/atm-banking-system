package com.atm.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class StaticFileHandler implements HttpHandler {

    private static final Map<String, String> MIME = new HashMap<>();
    static {
        MIME.put("html", "text/html; charset=UTF-8");
        MIME.put("css",  "text/css");
        MIME.put("js",   "application/javascript");
        MIME.put("png",  "image/png");
        MIME.put("ico",  "image/x-icon");
        MIME.put("svg",  "image/svg+xml");
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        if ("/".equals(path) || path.isEmpty()) path = "/index.html";

        String resource = "/static" + path;

        try (InputStream is = getClass().getResourceAsStream(resource)) {
            if (is == null) {
                try (InputStream fallback = getClass().getResourceAsStream("/static/index.html")) {
                    if (fallback == null) { send404(ex); return; }
                    serveStream(ex, fallback, "text/html; charset=UTF-8");
                }
                return;
            }
            String ext = getExt(path);
            String mime = MIME.getOrDefault(ext, "application/octet-stream");
            serveStream(ex, is, mime);
        }
    }

    private void serveStream(HttpExchange ex, InputStream is, String mime) throws IOException {
        byte[] bytes = is.readAllBytes();
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void send404(HttpExchange ex) throws IOException {
        byte[] body = "404 Not Found".getBytes();
        ex.sendResponseHeaders(404, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private String getExt(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1).toLowerCase() : "";
    }
}
