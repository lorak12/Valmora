package org.nakii.valmora.infrastructure.config.diag;

/** How serious a {@link ConfigDiagnostic} is. */
public enum Severity {
    /** The entry (or file) could not be loaded as written — it was skipped or kept its previous version. */
    ERROR,
    /** Loaded, but something looks wrong (unknown key, dangling reference, value clamped, ...). */
    WARN,
    /** Informational only; never shown in reload summaries, only in {@code /valmora report all}. */
    INFO
}
