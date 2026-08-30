package org.nakii.valmora.module.recipe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.nakii.valmora.util.Keys;

import java.util.List;
import java.util.Map;

/**
 * An explicit YAML anvil recipe (coworker anvil spec §5) — {@code type: UPGRADE} (base item's
 * identity changes but its data carries forward, e.g. a tiered weapon upgrade) or
 * {@code type: TRANSMUTE} (pure base+addition -&gt; result, no data carry-forward, e.g. a machine
 * tier upgrade). Checked by the unified {@link AnvilMachineHandler} BEFORE the modifier-recipe step
 * and the standard combination engine, matching the coworker's evaluation-pipeline diagram.
 */
public class AnvilRecipeDefinition {

    public enum Type { UPGRADE, TRANSMUTE }

    /** One side of a match: {@code material}/{@code valmoraId} are OR'd (either may be null), plus
     *  a required stack amount. Mirrors the identity check {@code RecipeEngine.isSameItem} already
     *  uses elsewhere (PDC id first, else vanilla Material name) rather than inventing a new one. */
    public record ItemMatch(String material, String valmoraId, int amount) {
        public boolean matches(ItemStack stack) {
            if (stack == null || stack.getType() == Material.AIR) return false;
            if (stack.getAmount() < amount) return false;
            if (valmoraId != null) {
                String actual = stack.hasItemMeta()
                        ? stack.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_ID_KEY, PersistentDataType.STRING)
                        : null;
                if (actual != null && actual.equalsIgnoreCase(strip(valmoraId))) return true;
            }
            if (material != null) {
                Material mat = Material.matchMaterial(strip(material));
                return mat != null && stack.getType() == mat;
            }
            return false;
        }

        private static String strip(String id) {
            int idx = id.indexOf(':');
            return idx >= 0 ? id.substring(idx + 1) : id;
        }
    }

    public record ResultSpec(String material, String valmoraId, String name, List<String> addLore,
                              Map<String, Integer> addEnchants, Map<String, Object> addNbt) {}

    private final String id;
    private final Type type;
    private final ItemMatch base;
    private final ItemMatch addition; // nullable — TRANSMUTE/UPGRADE both require one in practice, but not enforced here
    private final ResultSpec result;
    private final boolean keepDataOnUpgrade;
    private final boolean increaseWorkPenalty;
    private final int costXpLevels;
    private final int costCoins;

    public AnvilRecipeDefinition(String id, Type type, ItemMatch base, ItemMatch addition, ResultSpec result,
                                  boolean keepDataOnUpgrade, boolean increaseWorkPenalty, int costXpLevels, int costCoins) {
        this.id = id;
        this.type = type;
        this.base = base;
        this.addition = addition;
        this.result = result;
        this.keepDataOnUpgrade = keepDataOnUpgrade;
        this.increaseWorkPenalty = increaseWorkPenalty;
        this.costXpLevels = costXpLevels;
        this.costCoins = costCoins;
    }

    public boolean matches(ItemStack baseItem, ItemStack additionItem) {
        if (!base.matches(baseItem)) return false;
        if (addition == null) return additionItem == null || additionItem.getType() == Material.AIR;
        return addition.matches(additionItem);
    }

    public String getId() { return id; }
    public Type getType() { return type; }
    public ItemMatch getBase() { return base; }
    public ItemMatch getAddition() { return addition; }
    public ResultSpec getResult() { return result; }
    public boolean isKeepDataOnUpgrade() { return keepDataOnUpgrade; }
    public boolean isIncreaseWorkPenalty() { return increaseWorkPenalty; }
    public int getCostXpLevels() { return costXpLevels; }
    public int getCostCoins() { return costCoins; }
}
