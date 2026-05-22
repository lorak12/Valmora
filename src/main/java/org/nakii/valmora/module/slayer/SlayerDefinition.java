package org.nakii.valmora.module.slayer;

import java.util.Map;

public class SlayerDefinition {

    private final String id;
    private final String name;
    private final Map<Integer, SlayerTier> tiers;

    public SlayerDefinition(String id, String name, Map<Integer, SlayerTier> tiers) {
        this.id = id;
        this.name = name;
        this.tiers = tiers;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Map<Integer, SlayerTier> getTiers() { return tiers; }

    public SlayerTier getTier(int tier) { return tiers.get(tier); }
}
