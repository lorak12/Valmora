package org.nakii.valmora.module.pack.download;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link PackDownloader} against a real (local) HTTP server rather than mocking
 * {@link java.net.http.HttpClient} — proves the actual streaming/checksum logic end-to-end without
 * depending on external network access.
 */
class PackDownloaderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private String startServer(byte[] body, int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pack.zip", exchange -> {
            exchange.sendResponseHeaders(status, status == 200 ? body.length : -1);
            if (status == 200) {
                try (var os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/pack.zip";
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Test
    void downloadsAndComputesTheCorrectChecksum(@TempDir Path work) throws Exception {
        byte[] content = "pretend zip bytes".getBytes();
        String url = startServer(content, 200);
        String expectedHash = sha256Hex(content);

        PackDownloader downloader = new PackDownloader();
        File dest = work.resolve("downloaded.zip").toFile();
        PackDownloader.DownloadResult result = downloader.download(url, dest, null);

        assertEquals(expectedHash, result.sha256());
        assertArrayEquals(content, Files.readAllBytes(dest.toPath()));
    }

    @Test
    void acceptsAMatchingExpectedChecksum(@TempDir Path work) throws Exception {
        byte[] content = "pack contents".getBytes();
        String url = startServer(content, 200);
        String expectedHash = sha256Hex(content);

        PackDownloader downloader = new PackDownloader();
        File dest = work.resolve("downloaded.zip").toFile();
        assertDoesNotThrow(() -> downloader.download(url, dest, "sha256:" + expectedHash));
    }

    @Test
    void rejectsAMismatchedChecksumAndDeletesThePartialFile(@TempDir Path work) throws Exception {
        byte[] content = "pack contents".getBytes();
        String url = startServer(content, 200);

        PackDownloader downloader = new PackDownloader();
        File dest = work.resolve("downloaded.zip").toFile();

        IOException ex = assertThrows(IOException.class, () -> downloader.download(url, dest, "deadbeef"));
        assertTrue(ex.getMessage().contains("Checksum mismatch"));
        assertFalse(dest.exists(), "a checksum-mismatched download must not be left on disk");
    }

    @Test
    void nonSuccessStatusThrows(@TempDir Path work) throws Exception {
        String url = startServer(new byte[0], 404);

        PackDownloader downloader = new PackDownloader();
        File dest = work.resolve("downloaded.zip").toFile();

        IOException ex = assertThrows(IOException.class, () -> downloader.download(url, dest, null));
        assertTrue(ex.getMessage().contains("404"));
    }
}
