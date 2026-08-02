package org.nakii.valmora.module.item.set;

import java.util.List;
import java.util.Map;

/**
 * An armor set bonus: a set id (matching armor pieces' {@code set:} field) and a list of tiers.
 * Each tier grants a stat map once the player wears at least {@code piecesRequired} pieces of the
 * set. Tiers are cumulative, so tiered bonuses (e.g. Melon/Cropie/Squash) and simple full-set
 * bonuses are both expressed naturally.
 */
public record SetBonusDefinition(String setId, String name, List<Tier> tiers) {

    public record Tier(int piecesRequired, Map<String, Double> stats) {}
}
