package org.nakii.valmora.module.enchant.state;

/**
 * Parsed {@code state.persistent.<key>:} entry — an on-item (PDC-backed, via
 * {@link org.nakii.valmora.module.enchant.EnchantStateStore}) counter that survives across
 * hits/sessions, e.g. a "kills with this weapon" tally.
 *
 * @param defaultValue value read for an item that has never had this key written yet.
 */
public record PersistentStateDefinition(int defaultValue) {
}
