package org.nakii.valmora.module.pack.validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The accumulated result of validating one (or a batch of) pack(s) — structural manifest problems,
 * engine/plugin/pack dependency issues, and reference-integrity findings, all collected rather than
 * failing fast (matches {@code YamlLoader}/{@code ModifierValidator}'s existing "collect everything,
 * report once" convention).
 *
 * <p>{@code errors} are blocking — an install using this report must abort before touching disk.
 * {@code warnings} are advisory (e.g. a missing soft dependency, or a reference-integrity finding
 * from an optional checker) and don't by themselves prevent an install.
 */
public final class PackValidationReport {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void addError(String message) {
        errors.add(message);
    }

    public void addWarning(String message) {
        warnings.add(message);
    }

    public void merge(PackValidationReport other) {
        errors.addAll(other.errors);
        warnings.addAll(other.warnings);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
