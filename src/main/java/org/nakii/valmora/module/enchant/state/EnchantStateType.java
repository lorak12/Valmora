package org.nakii.valmora.module.enchant.state;

/**
 * Declares which shape a {@code state.transient.<key>:}/{@code state.persistent.<key>:} YAML entry
 * has. Only one shape exists per tier today ({@code HIT_COUNTER} for transient, {@code INTEGER} for
 * persistent) — the enum exists so the schema can grow a second shape later (e.g. a transient
 * boolean flag) without a breaking change, and so an enchant author's typo in `type:` gets a
 * load-time warning instead of silently parsing as whatever the default happened to be.
 */
public enum EnchantStateType {
    HIT_COUNTER,
    INTEGER
}
