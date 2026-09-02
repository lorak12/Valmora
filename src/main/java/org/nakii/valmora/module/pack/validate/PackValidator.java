package org.nakii.valmora.module.pack.validate;

import org.nakii.valmora.module.pack.PackContentFolders;
import org.nakii.valmora.module.pack.SemVer;
import org.nakii.valmora.module.pack.manifest.PackDependency;
import org.nakii.valmora.module.pack.manifest.PackManifest;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Validates a pack before anything about it is written to disk (docs/modules/design/pack.md §5),
 * mirroring {@code ModifierValidator}/{@code MachineModule}'s existing "collect problems, don't
 * fail fast" convention via {@link PackValidationReport} — but unlike those two (which validate
 * against already-live registries after loading), every check here runs against the manifest and
 * dependency graph alone, before any content is parsed or copied, so a bad pack never touches the
 * live data folder at all.
 */
public final class PackValidator {

    private PackValidator() {}

    /**
     * Runs every pre-install check for one pack: manifest structure, engine-version compatibility,
     * Bukkit plugin dependencies, and (via {@link PackDependencyResolver}, folded in by the caller
     * for a whole batch) pack-to-pack dependencies. Does not run reference-integrity checks — see
     * {@link #validateReferences}, which needs the pack's content actually staged/parsed first.
     *
     * @param pack               the manifest to validate
     * @param runningEngineVersion the currently running plugin version (e.g. from
     *                           {@code plugin.getDescription().getVersion()})
     * @param pluginPresent      predicate answering whether a named Bukkit plugin is installed
     */
    public static PackValidationReport validateManifest(PackManifest pack, String runningEngineVersion,
                                                          Predicate<String> pluginPresent) {
        PackValidationReport report = new PackValidationReport();
        validateStructure(pack, report);
        validateEngineVersion(pack, runningEngineVersion, report);
        validatePluginDependencies(pack, pluginPresent, report);
        return report;
    }

    private static void validateStructure(PackManifest pack, PackValidationReport report) {
        if (SemVer.tryParse(pack.version()) == null) {
            report.addError("Pack '" + pack.id() + "' has an unparseable version: '" + pack.version() + "'");
        }
        if (SemVer.tryParse(pack.engineVersionMin()) == null) {
            report.addError("Pack '" + pack.id() + "' has an unparseable engine_version_min: '" + pack.engineVersionMin() + "'");
        }
        if (pack.engineVersionMax() != null && SemVer.tryParse(pack.engineVersionMax()) == null) {
            report.addError("Pack '" + pack.id() + "' has an unparseable engine_version_max: '" + pack.engineVersionMax() + "'");
        }

        for (String entry : pack.providesContent()) {
            if (!PackContentFolders.isKnownContentEntry(entry)) {
                report.addError("Pack '" + pack.id() + "' declares provides.content entry '" + entry
                        + "' which is not a known content folder");
            }
        }
        for (String file : pack.providesShared()) {
            if (!PackContentFolders.isKnownSharedConfig(file)) {
                report.addError("Pack '" + pack.id() + "' declares provides.shared entry '" + file
                        + "' which is not a known non-destructively-mergeable shared config");
            }
        }
        for (PackDependency dep : pack.dependsPacks()) {
            validateConstraint(pack, dep, report);
        }
        for (PackDependency dep : pack.softDependsPacks()) {
            validateConstraint(pack, dep, report);
        }
    }

    private static void validateConstraint(PackManifest pack, PackDependency dep, PackValidationReport report) {
        if (dep.versionConstraint() == null || dep.versionConstraint().isBlank()) return;
        try {
            SemVer.parse(dep.versionConstraint().replaceAll("^(>=|<=|>|<|==|=)", "").trim());
        } catch (IllegalArgumentException e) {
            report.addError("Pack '" + pack.id() + "' has an unparseable version constraint on '"
                    + dep.id() + "': '" + dep.versionConstraint() + "'");
        }
    }

    private static void validateEngineVersion(PackManifest pack, String runningEngineVersion, PackValidationReport report) {
        SemVer running = SemVer.tryParse(runningEngineVersion);
        SemVer min = SemVer.tryParse(pack.engineVersionMin());
        if (running == null || min == null) {
            return; // unparseable versions are already reported by validateStructure / caller misuse
        }
        if (running.compareTo(min) < 0) {
            report.addError("Pack '" + pack.id() + "' requires engine version >= " + pack.engineVersionMin()
                    + " but this server runs " + runningEngineVersion);
        }
        SemVer max = SemVer.tryParse(pack.engineVersionMax());
        if (pack.engineVersionMax() != null && max != null && running.compareTo(max) > 0) {
            report.addError("Pack '" + pack.id() + "' requires engine version <= " + pack.engineVersionMax()
                    + " but this server runs " + runningEngineVersion);
        }
    }

    private static void validatePluginDependencies(PackManifest pack, Predicate<String> pluginPresent, PackValidationReport report) {
        for (String pluginName : pack.dependsPlugins()) {
            if (!pluginPresent.test(pluginName)) {
                report.addError("Pack '" + pack.id() + "' requires plugin '" + pluginName + "' which is not installed");
            }
        }
    }

    /**
     * Validates the pack-to-pack dependency graph for a whole install batch — thin wrapper around
     * {@link PackDependencyResolver#resolve} so callers doing full validation don't need to know
     * about that class separately.
     */
    public static PackDependencyResolver.Result validateDependencyGraph(
            Map<String, PackManifest> candidates, Map<String, PackManifest> alreadyInstalled) {
        return PackDependencyResolver.resolve(candidates, alreadyInstalled);
    }

    /**
     * Runs every registered {@link PackReferenceChecker} against {@code packId} and folds their
     * findings in as warnings (non-blocking, matching {@code ModifierValidator}'s posture — see this
     * class's doc for why nothing is registered here yet).
     */
    public static PackValidationReport validateReferences(String packId, PackReferenceCheckerRegistry registry) {
        PackValidationReport report = new PackValidationReport();
        for (String finding : registry.checkAll(packId)) {
            report.addWarning(finding);
        }
        return report;
    }
}
