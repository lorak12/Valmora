package org.nakii.valmora.infrastructure.config.refs;

/**
 * A cross-content consistency check run once everything has loaded (after startup, every reload
 * and by {@code /valmora validate}) — e.g. "every machine's GUI exists and has the right slots".
 * Problems are reported as warnings; the content involved stays loaded.
 */
public interface ReferenceCheck {

    /** Short name, used in logs if the check itself fails. */
    String name();

    void check(ReferenceContext context);
}
