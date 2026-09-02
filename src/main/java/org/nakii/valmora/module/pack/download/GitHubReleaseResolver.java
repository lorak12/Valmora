package org.nakii.valmora.module.pack.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the {@code github:owner/repo@tag} install-source shorthand (docs/modules/design/pack.md
 * §1/§8) to a downloadable {@code .zip} release-asset URL via GitHub's public Releases API. Split
 * into pure, independently-testable steps ({@link #parseShorthand}, {@link #extractZipAssetUrl}) and
 * the thin network glue ({@link #resolve}) that wires them together — only the glue needs a live
 * HTTP call; both pure steps are covered without any network access in tests.
 */
public final class GitHubReleaseResolver {

    private static final Pattern SHORTHAND = Pattern.compile("^github:([^/\\s]+)/([^@\\s]+)(?:@(\\S+))?$");
    private static final String USER_AGENT = "Valmora-PackManager/1.0";

    private GitHubReleaseResolver() {}

    /** @param tag the release tag, or {@code null} to mean "the latest release" */
    public record ShorthandRef(String owner, String repo, String tag) {
    }

    /** Parses {@code "github:owner/repo"} or {@code "github:owner/repo@v1.2.0"}; empty if {@code source} isn't that shorthand at all. */
    public static Optional<ShorthandRef> parseShorthand(String source) {
        if (source == null) return Optional.empty();
        Matcher m = SHORTHAND.matcher(source.trim());
        if (!m.matches()) return Optional.empty();
        return Optional.of(new ShorthandRef(m.group(1), m.group(2), m.group(3)));
    }

    /** The GitHub Releases API URL for a parsed shorthand ref. */
    public static String releaseApiUrl(ShorthandRef ref) {
        String path = (ref.tag() != null && !ref.tag().isBlank())
                ? "/releases/tags/" + ref.tag()
                : "/releases/latest";
        return "https://api.github.com/repos/" + ref.owner() + "/" + ref.repo() + path;
    }

    /**
     * Pure parse of a GitHub Releases API JSON response body: returns the first {@code .zip}
     * asset's {@code browser_download_url}, or empty if the response has no such asset (or isn't
     * parseable release JSON at all).
     */
    public static Optional<String> extractZipAssetUrl(String releaseJson) {
        try {
            JsonObject root = JsonParser.parseString(releaseJson).getAsJsonObject();
            JsonArray assets = root.getAsJsonArray("assets");
            if (assets == null) return Optional.empty();
            for (var element : assets) {
                JsonObject asset = element.getAsJsonObject();
                String name = asset.has("name") ? asset.get("name").getAsString() : "";
                if (name.toLowerCase(Locale.ROOT).endsWith(".zip") && asset.has("browser_download_url")) {
                    return Optional.of(asset.get("browser_download_url").getAsString());
                }
            }
        } catch (Exception ignored) {
            // Malformed/unexpected JSON — treated the same as "no zip asset found".
        }
        return Optional.empty();
    }

    /**
     * Resolves a {@code github:owner/repo[@tag]} source to a downloadable {@code .zip} asset URL by
     * calling the GitHub Releases API. Returns empty (not an exception) both when {@code source}
     * isn't {@code github:} shorthand at all and when the API call succeeds but no zip asset exists.
     */
    public static Optional<String> resolve(String source, HttpClient httpClient) throws IOException, InterruptedException {
        Optional<ShorthandRef> ref = parseShorthand(source);
        if (ref.isEmpty()) return Optional.empty();

        HttpRequest request = HttpRequest.newBuilder(URI.create(releaseApiUrl(ref.get())))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) return Optional.empty();
        return extractZipAssetUrl(response.body());
    }
}
