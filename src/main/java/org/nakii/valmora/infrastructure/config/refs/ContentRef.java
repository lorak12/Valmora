package org.nakii.valmora.infrastructure.config.refs;

import org.nakii.valmora.infrastructure.config.diag.ConfigSource;

/** One piece of content referring to another: {@code source} mentions {@code id} of {@code kind}. */
public record ContentRef(String kind, String id, ConfigSource source) {}
