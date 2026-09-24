package org.nakii.valmora.infrastructure.config.diag;

/** Receives diagnostics — a loader's per-type collector, the global {@link LoadReport}, or a dry-run list. */
@FunctionalInterface
public interface DiagnosticSink {
    void accept(ConfigDiagnostic diagnostic);
}
