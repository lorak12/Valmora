package org.nakii.valmora.module.zone;

import org.bukkit.Material;
import java.util.List;

public class ResourceStage {
    private final List<ZoneResourceDrop> drops;
    private final Material nextMaterial;

    public ResourceStage(List<ZoneResourceDrop> drops, Material nextMaterial) {
        this.drops = drops;
        this.nextMaterial = nextMaterial;
    }

    public List<ZoneResourceDrop> getDrops() { return drops; }

    /**
     * The material this block transitions into when mined at this stage.
     * If null, the block is set to AIR and regen begins immediately.
     */
    public Material getNextMaterial() { return nextMaterial; }
}
