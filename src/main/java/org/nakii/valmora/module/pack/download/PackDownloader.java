package org.nakii.valmora.module.pack.download;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * Downloads a pack archive from a direct URL and verifies its SHA-256 checksum before anything else
 * touches it — docs/modules/design/pack.md §4. Mirrors {@code SkinResolver}'s async-HTTP idiom
 * ({@link java.net.http.HttpClient}, a bounded connect/request timeout) but stays a plain blocking
 * method here rather than taking a callback: callers (see {@code PackManager}) already run the whole
 * download+extract sequence off the main thread themselves via
 * {@code Bukkit.getScheduler().runTaskAsynchronously}, then hop back to the main thread only for the
 * install step, which is the one part that touches Bukkit/module state.
 */
public class PackDownloader {

    private static final String USER_AGENT = "Valmora-PackManager/1.0";

    private final HttpClient httpClient;

    public PackDownloader() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    /** Package-visible for tests that want a client tuned differently (e.g. no redirect following). */
    PackDownloader(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public record DownloadResult(File file, String sha256) {
    }

    /**
     * Downloads {@code url} to {@code destFile}, computing its SHA-256 as it streams to disk. If
     * {@code expectedSha256} is non-blank (a bare hex string or {@code "sha256:<hex>"}), a mismatch
     * deletes {@code destFile} and throws before returning — the caller never sees a corrupted or
     * tampered-with download.
     *
     * @throws IOException          on a non-2xx response, a transport failure, or a checksum mismatch
     * @throws InterruptedException if the calling thread is interrupted mid-request
     */
    public DownloadResult download(String url, File destFile, String expectedSha256) throws IOException, InterruptedException {
        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " downloading pack from " + url);
        }

        String actualSha256;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = response.body();
                 DigestInputStream digestIn = new DigestInputStream(in, digest);
                 var out = java.nio.file.Files.newOutputStream(destFile.toPath())) {
                digestIn.transferTo(out);
            }
            actualSha256 = toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable on this JVM", e);
        }

        String normalizedExpected = normalize(expectedSha256);
        if (normalizedExpected != null && !normalizedExpected.equalsIgnoreCase(actualSha256)) {
            destFile.delete();
            throw new IOException("Checksum mismatch downloading pack from " + url
                    + ": expected " + normalizedExpected + " but got " + actualSha256);
        }

        return new DownloadResult(destFile, actualSha256);
    }

    private static String normalize(String expected) {
        if (expected == null || expected.isBlank()) return null;
        String trimmed = expected.trim();
        return trimmed.startsWith("sha256:") ? trimmed.substring("sha256:".length()) : trimmed;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
