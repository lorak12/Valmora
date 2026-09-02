package org.nakii.valmora.module.pack;

import java.util.Locale;
import java.util.Objects;

/**
 * A minimal semantic-version parser/comparator ({@code MAJOR.MINOR.PATCH[-PRERELEASE]}) — the
 * engine has no existing versioning convention (docs/modules/design/pack.md, "no schema/version
 * field convention exists" gap), so this is written fresh for pack manifests and version-constraint
 * checks ({@code engine_version_min/max}, {@code depends.packs[].version}). Comparison ignores
 * build-metadata (a {@code +...} suffix, if present, is stripped and not compared); a version
 * with a pre-release suffix sorts below the same MAJOR.MINOR.PATCH without one, per semver §11.
 */
public final class SemVer implements Comparable<SemVer> {

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease; // null = no pre-release (release build)
    private final String raw;

    private SemVer(int major, int minor, int patch, String preRelease, String raw) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
        this.raw = raw;
    }

    /**
     * Parses a version string. Accepts a bare {@code MAJOR}, {@code MAJOR.MINOR}, or
     * {@code MAJOR.MINOR.PATCH}, each with an optional {@code -prerelease} suffix (missing
     * components default to 0) — lenient because this project's own version string
     * ({@code 1.0.0-beta1}) and pack authors' version strings both need to parse cleanly.
     * @throws IllegalArgumentException if the string isn't parseable at all
     */
    public static SemVer parse(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Version string is empty");
        }
        String noBuildMeta = version.split("\\+", 2)[0];
        String[] releaseAndPre = noBuildMeta.split("-", 2);
        String[] parts = releaseAndPre[0].split("\\.");
        if (parts.length == 0 || parts.length > 3) {
            throw new IllegalArgumentException("Invalid version string: '" + version + "'");
        }
        try {
            int major = Integer.parseInt(parts[0].trim());
            int minor = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
            String preRelease = releaseAndPre.length > 1 ? releaseAndPre[1] : null;
            return new SemVer(major, minor, patch, preRelease, version);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid version string: '" + version + "'", e);
        }
    }

    /** Parses a version string, returning {@code null} instead of throwing on malformed input. */
    public static SemVer tryParse(String version) {
        try {
            return parse(version);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public int compareTo(SemVer o) {
        int cmp = Integer.compare(major, o.major);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(minor, o.minor);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(patch, o.patch);
        if (cmp != 0) return cmp;
        if (preRelease == null && o.preRelease == null) return 0;
        if (preRelease == null) return 1;  // release > pre-release
        if (o.preRelease == null) return -1;
        return preRelease.compareTo(o.preRelease);
    }

    public boolean isAtLeast(SemVer other) {
        return compareTo(other) >= 0;
    }

    public boolean isAtMost(SemVer other) {
        return compareTo(other) <= 0;
    }

    /**
     * Evaluates a simple version constraint of the form {@code >=1.0.0}, {@code >1.0.0},
     * {@code <=1.0.0}, {@code <1.0.0}, {@code ==1.0.0}/{@code =1.0.0}, or a bare {@code 1.0.0}
     * (treated as {@code ==}) against this version.
     * @throws IllegalArgumentException if the constraint's operator or version isn't recognized
     */
    public boolean satisfies(String constraint) {
        if (constraint == null || constraint.isBlank()) return true;
        String trimmed = constraint.trim();
        String op;
        String rest;
        if (trimmed.startsWith(">=") || trimmed.startsWith("<=") || trimmed.startsWith("==")) {
            op = trimmed.substring(0, 2);
            rest = trimmed.substring(2);
        } else if (trimmed.startsWith(">") || trimmed.startsWith("<") || trimmed.startsWith("=")) {
            op = trimmed.substring(0, 1);
            rest = trimmed.substring(1);
        } else {
            op = "==";
            rest = trimmed;
        }
        SemVer target = SemVer.parse(rest.trim());
        int cmp = compareTo(target);
        return switch (op) {
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            case "==", "=" -> cmp == 0;
            default -> throw new IllegalArgumentException("Unknown version constraint operator: '" + op + "'");
        };
    }

    public String raw() {
        return raw;
    }

    @Override
    public String toString() {
        return raw;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemVer other)) return false;
        return compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease == null ? "" : preRelease.toLowerCase(Locale.ROOT));
    }
}
