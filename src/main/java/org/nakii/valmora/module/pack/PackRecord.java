package org.nakii.valmora.module.pack;

import java.util.List;
import java.util.Map;

/**
 * A persisted record of one installed content pack — the row shape backing
 * {@code valmora_installed_packs}. This is the source of truth {@link org.nakii.valmora.module.pack}
 * uses to know exactly which files and shared-config keys a pack owns, so uninstall can remove
 * precisely that and nothing else.
 *
 * @param packId the pack's own id (also the namespace prefix its content ids carry)
 * @param version the pack's own semver version string
 * @param checksum the archive checksum it was installed from, or {@code null} for a local install
 * @param installedAt epoch millis of install time
 * @param fileManifest relative paths (under the data folder) of every file this pack owns
 * @param sharedDiff for each shared/misc config file this pack touched, the diff
 *                   {@code org.nakii.valmora.module.pack.install.SharedConfigMerger.merge}
 *                   produced (top-level key -&gt; added sub-keys/list-entries, or the single
 *                   {@code "*"} sentinel for a whole key the pack added fresh) — precise enough for
 *                   {@code SharedConfigMerger.revert} to undo exactly this pack's contribution and
 *                   nothing else
 * @param dependsOn ids of packs this pack declared a hard dependency on
 */
public record PackRecord(
        String packId,
        String version,
        String checksum,
        long installedAt,
        List<String> fileManifest,
        Map<String, Map<String, List<String>>> sharedDiff,
        List<String> dependsOn
) {
}
