package org.nakii.valmora.module.pack.validate;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.module.pack.manifest.PackDependency;
import org.nakii.valmora.module.pack.manifest.PackManifest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PackValidatorTest {

    private static PackManifest wellFormed() {
        return new PackManifest("frostspire", "Frostspire", "1.0.0", "author", "desc",
                "1.0.0-beta1", null, List.of(), List.of(), List.of(),
                List.of("items", "modifiers/groups"), List.of("rarities.yml"), null);
    }

    @Test
    void wellFormedManifestPassesStructuralAndVersionChecks() {
        PackValidationReport report = PackValidator.validateManifest(wellFormed(), "1.0.0-beta1", name -> true);
        assertTrue(report.isValid(), () -> report.getErrors().toString());
    }

    @Test
    void unparseableVersionsAreErrors() {
        PackManifest bad = new PackManifest("pack", "Pack", "not-a-version", "author", "desc",
                "1.0.0", null, List.of(), List.of(), List.of(), List.of("items"), List.of(), null);
        PackValidationReport report = PackValidator.validateManifest(bad, "1.0.0", name -> true);
        assertFalse(report.isValid());
    }

    @Test
    void unknownProvidesContentEntryIsAnError() {
        PackManifest bad = new PackManifest("pack", "Pack", "1.0.0", "author", "desc",
                "1.0.0", null, List.of(), List.of(), List.of(), List.of("not_a_real_folder"), List.of(), null);
        PackValidationReport report = PackValidator.validateManifest(bad, "1.0.0", name -> true);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("not_a_real_folder")));
    }

    @Test
    void unknownProvidesSharedEntryIsAnError() {
        PackManifest bad = new PackManifest("pack", "Pack", "1.0.0", "author", "desc",
                "1.0.0", null, List.of(), List.of(), List.of(), List.of("items"), List.of("not_a_real_config.yml"), null);
        PackValidationReport report = PackValidator.validateManifest(bad, "1.0.0", name -> true);
        assertFalse(report.isValid());
    }

    @Test
    void engineVersionBelowMinimumIsAnError() {
        PackManifest pack = wellFormed();
        PackValidationReport report = PackValidator.validateManifest(pack, "0.5.0", name -> true);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("engine version")));
    }

    @Test
    void engineVersionAboveMaximumIsAnError() {
        PackManifest pack = new PackManifest("pack", "Pack", "1.0.0", "author", "desc",
                "1.0.0", "1.5.0", List.of(), List.of(), List.of(), List.of("items"), List.of(), null);
        PackValidationReport report = PackValidator.validateManifest(pack, "2.0.0", name -> true);
        assertFalse(report.isValid());
    }

    @Test
    void missingRequiredPluginIsAnError() {
        PackManifest pack = new PackManifest("pack", "Pack", "1.0.0", "author", "desc",
                "1.0.0", null, List.of("Vault"), List.of(), List.of(), List.of("items"), List.of(), null);
        PackValidationReport report = PackValidator.validateManifest(pack, "1.0.0", name -> false);
        assertFalse(report.isValid());
        assertTrue(report.getErrors().stream().anyMatch(e -> e.contains("Vault")));
    }

    @Test
    void presentRequiredPluginPasses() {
        PackManifest pack = new PackManifest("pack", "Pack", "1.0.0", "author", "desc",
                "1.0.0", null, List.of("Vault"), List.of(), List.of(), List.of("items"), List.of(), null);
        PackValidationReport report = PackValidator.validateManifest(pack, "1.0.0", "Vault"::equals);
        assertTrue(report.isValid());
    }

    @Test
    void unparseableDependencyConstraintIsAnError() {
        PackManifest pack = new PackManifest("pack", "Pack", "1.0.0", "author", "desc",
                "1.0.0", null, List.of(), List.of(new PackDependency("other", ">=nope")), List.of(),
                List.of("items"), List.of(), null);
        PackValidationReport report = PackValidator.validateManifest(pack, "1.0.0", name -> true);
        assertFalse(report.isValid());
    }

    @Test
    void validateReferencesDelegatesToRegisteredCheckers() {
        PackReferenceCheckerRegistry registry = new PackReferenceCheckerRegistry();
        registry.register(packId -> List.of("dangling reference in " + packId));

        PackValidationReport report = PackValidator.validateReferences("frostspire", registry);

        assertTrue(report.isValid(), "reference findings are warnings, not blocking errors");
        assertTrue(report.hasWarnings());
        assertEquals(Set.of("dangling reference in frostspire"), Set.copyOf(report.getWarnings()));
    }

    @Test
    void validateReferencesWithNoCheckersProducesNoWarnings() {
        PackValidationReport report = PackValidator.validateReferences("frostspire", new PackReferenceCheckerRegistry());
        assertTrue(report.isValid());
        assertFalse(report.hasWarnings());
    }
}
