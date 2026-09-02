package org.nakii.valmora.module.pack.download;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GitHubReleaseResolverTest {

    @Test
    void parsesShorthandWithTag() {
        var ref = GitHubReleaseResolver.parseShorthand("github:someauthor/frostspire-pack@v1.2.0").orElseThrow();
        assertEquals("someauthor", ref.owner());
        assertEquals("frostspire-pack", ref.repo());
        assertEquals("v1.2.0", ref.tag());
    }

    @Test
    void parsesShorthandWithoutTagAsNull() {
        var ref = GitHubReleaseResolver.parseShorthand("github:someauthor/frostspire-pack").orElseThrow();
        assertNull(ref.tag());
    }

    @Test
    void nonGithubSourceIsEmpty() {
        assertTrue(GitHubReleaseResolver.parseShorthand("https://example.com/pack.zip").isEmpty());
        assertTrue(GitHubReleaseResolver.parseShorthand("/local/path").isEmpty());
        assertTrue(GitHubReleaseResolver.parseShorthand(null).isEmpty());
    }

    @Test
    void releaseApiUrlUsesTagsEndpointWhenTagPresent() {
        var ref = new GitHubReleaseResolver.ShorthandRef("owner", "repo", "v1.0.0");
        assertEquals("https://api.github.com/repos/owner/repo/releases/tags/v1.0.0", GitHubReleaseResolver.releaseApiUrl(ref));
    }

    @Test
    void releaseApiUrlUsesLatestEndpointWhenTagAbsent() {
        var ref = new GitHubReleaseResolver.ShorthandRef("owner", "repo", null);
        assertEquals("https://api.github.com/repos/owner/repo/releases/latest", GitHubReleaseResolver.releaseApiUrl(ref));
    }

    @Test
    void extractsTheFirstZipAssetUrl() {
        String json = """
                {
                  "assets": [
                    {"name": "README.md", "browser_download_url": "https://example.com/README.md"},
                    {"name": "frostspire-pack.zip", "browser_download_url": "https://example.com/frostspire-pack.zip"},
                    {"name": "other.zip", "browser_download_url": "https://example.com/other.zip"}
                  ]
                }
                """;
        Optional<String> url = GitHubReleaseResolver.extractZipAssetUrl(json);
        assertEquals("https://example.com/frostspire-pack.zip", url.orElseThrow());
    }

    @Test
    void noZipAssetReturnsEmpty() {
        String json = """
                { "assets": [ {"name": "README.md", "browser_download_url": "https://example.com/README.md"} ] }
                """;
        assertTrue(GitHubReleaseResolver.extractZipAssetUrl(json).isEmpty());
    }

    @Test
    void missingAssetsFieldReturnsEmpty() {
        assertTrue(GitHubReleaseResolver.extractZipAssetUrl("{}").isEmpty());
    }

    @Test
    void malformedJsonReturnsEmptyRatherThanThrowing() {
        assertTrue(GitHubReleaseResolver.extractZipAssetUrl("not json at all").isEmpty());
    }
}
