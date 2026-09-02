package org.nakii.valmora.module.pack.manifest;

/**
 * One entry of a {@code pack.yml}'s {@code depends.packs}/{@code soft_depends.packs} list: another
 * pack this one relies on, with an optional version constraint (e.g. {@code ">=1.0.0"}) evaluated
 * via {@link org.nakii.valmora.module.pack.SemVer#satisfies(String)}.
 */
public record PackDependency(String id, String versionConstraint) {
}
