package org.nakii.valmora.module.npc;

import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * Minimal HTTP server that serves PNG files from plugins/Valmora/skins/.
 * Used so clients can download skin images placed locally on the server.
 *
 * Start/stop is managed by NpcModule lifecycle.
 */
public class SkinFileServer {

    private final File skinsDir;
    private final int port;
    private final String host;
    private final Logger logger;
    private HttpServer server;

    public SkinFileServer(File pluginDataFolder, int port, String host, Logger logger) {
        this.skinsDir = new File(pluginDataFolder, "skins");
        this.port = port;
        this.host = host;
        this.logger = logger;
    }

    public void start() throws IOException {
        skinsDir.mkdirs();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/skins/", exchange -> {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                // Strip leading /skins/
                String filename = path.startsWith("/skins/") ? path.substring(7) : path;
                // Block path traversal
                File requested = new File(skinsDir, filename).getCanonicalFile();
                if (!requested.getPath().startsWith(skinsDir.getCanonicalPath() + File.separator)
                        && !requested.equals(skinsDir.getCanonicalFile())) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                if (!requested.exists() || !requested.isFile()) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] data = Files.readAllBytes(requested.toPath());
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
            } catch (Exception e) {
                try { exchange.sendResponseHeaders(500, -1); } catch (Exception ignored) {}
                logger.warning("[NPC/SkinServer] Error serving request: " + e.getMessage());
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(null); // default executor
        server.start();
        logger.info("[NPC] Skin file server started on port " + port + ". Serving from: " + skinsDir.getPath());
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    /** Returns the URL a client can use to download the given skin file. */
    public String urlFor(String filename) {
        return "http://" + host + ":" + port + "/skins/" + filename;
    }

    public File getSkinsDir() {
        return skinsDir;
    }
}
