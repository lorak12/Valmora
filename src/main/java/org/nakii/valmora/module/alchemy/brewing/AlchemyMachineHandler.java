package org.nakii.valmora.module.alchemy.brewing;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.alchemy.AlchemyManager;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffect;
import org.nakii.valmora.module.alchemy.effect.AlchemyEffectType;
import org.nakii.valmora.module.alchemy.modifier.AlchemyModifier;
import org.nakii.valmora.module.alchemy.modifier.AlchemyModifierType;
import org.nakii.valmora.module.recipe.DynamicMachineHandler;
import org.nakii.valmora.module.recipe.RecipeDefinition;
import org.nakii.valmora.util.Formatter;
import org.nakii.valmora.util.Keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AlchemyMachineHandler implements DynamicMachineHandler {

    private static final String AWKWARD_POTION_ID = "awkward_potion";

    private final Valmora plugin;
    private final AlchemyManager alchemyManager;

    public AlchemyMachineHandler(Valmora plugin, AlchemyManager alchemyManager) {
        this.plugin = plugin;
        this.alchemyManager = alchemyManager;
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs) {
        return match(inputs, null);
    }

    @Override
    public Optional<RecipeDefinition> match(Map<String, ItemStack> inputs, @Nullable Player player) {
        ItemStack base = inputs.get("base");
        ItemStack ingredient = inputs.get("ingredient");

        if (isEmpty(base) || isEmpty(ingredient)) return Optional.empty();

        // ── Priority 1: Modifier application ─────────────────────────────
        // Check ingredient before base-brew so glowstone doesn't accidentally
        // match as a brewing ingredient for some future effect.
        Optional<RecipeDefinition> modifierMatch = matchModifier(base, ingredient);
        if (modifierMatch.isPresent()) return modifierMatch;

        // ── Priority 2: Water Bottle + Nether Wart → Awkward Potion ──────
        if (isWaterBottle(base) && ingredient.getType() == Material.NETHER_WART) {
            ItemStack awkward = buildAwkwardPotion();
            return Optional.of(RecipeDefinition.dynamic("alchemy", awkward, inp -> {
                consume(inp, "base", 1);
                consume(inp, "ingredient", 1);
            }));
        }

        // ── Priority 3: Awkward Potion + ingredient → Brewed Potion ──────
        if (!isAwkwardPotion(base)) return Optional.empty();

        String ingredientKey = getItemKey(ingredient);
        Optional<AlchemyManager.BrewTier> tierOpt = alchemyManager.getBrewTier(ingredientKey);
        if (tierOpt.isEmpty()) return Optional.empty();

        AlchemyManager.BrewTier tier = tierOpt.get();
        AlchemyEffect effect = tier.effect();
        int baseLevel = Math.min(tier.baseLevel(), effect.getMaxLevel());
        int alchemyBonus = getAlchemyLevel(player);
        // Alchemy skill adds a small duration bonus (1% per level)
        double alchemyMult = 1.0 + alchemyBonus * 0.01;
        int duration = (int) (effect.getDuration(baseLevel) * alchemyMult);

        ItemStack potion = buildPotionItem(effect, baseLevel, duration, false, false, false);

        return Optional.of(RecipeDefinition.dynamic("alchemy", potion, inp -> {
            consume(inp, "base", 1);
            consume(inp, "ingredient", 1);
        }));
    }

    // ── Modifier matching ─────────────────────────────────────────────────

    private Optional<RecipeDefinition> matchModifier(ItemStack base, ItemStack ingredient) {
        String ingredientKey = getItemKey(ingredient);
        Optional<AlchemyModifier> modOpt = alchemyManager.getModifier(ingredientKey);
        if (modOpt.isEmpty()) return Optional.empty();
        AlchemyModifier modifier = modOpt.get();

        // Base must be a Valmora alchemy potion (not awkward, not water)
        if (!base.hasItemMeta()) return Optional.empty();
        var pdc = base.getItemMeta().getPersistentDataContainer();
        String effectId = pdc.get(Keys.ALCHEMY_EFFECT_ID, PersistentDataType.STRING);
        if (effectId == null) return Optional.empty();

        AlchemyEffect effect = alchemyManager.getEffect(effectId).orElse(null);
        if (effect == null) return Optional.empty();

        int currentLevel    = pdc.getOrDefault(Keys.ALCHEMY_EFFECT_LEVEL,   PersistentDataType.INTEGER, 1);
        int currentDuration = pdc.getOrDefault(Keys.ALCHEMY_DURATION,        PersistentDataType.INTEGER, effect.getDuration(currentLevel));
        boolean isSplash         = pdc.getOrDefault(Keys.ALCHEMY_IS_SPLASH,       PersistentDataType.BYTE, (byte) 0) == 1;
        boolean levelModified    = pdc.getOrDefault(Keys.ALCHEMY_LEVEL_MODIFIED,   PersistentDataType.BYTE, (byte) 0) == 1;
        boolean durationModified = pdc.getOrDefault(Keys.ALCHEMY_DURATION_MODIFIED,PersistentDataType.BYTE, (byte) 0) == 1;

        int maxBase = effect.getMaxBaseLevel();

        return switch (modifier.getType()) {
            case LEVEL -> {
                if (levelModified) yield Optional.empty(); // already levelled up
                if (modifier.isRequiresMaxBase() && currentLevel != maxBase) yield Optional.empty();
                int newLevel = currentLevel + modifier.getLevelBonus();
                if (newLevel > effect.getMaxLevel()) yield Optional.empty();
                ItemStack result = buildPotionItem(effect, newLevel, currentDuration, isSplash, true, durationModified);
                yield Optional.of(recipe(result));
            }
            case DURATION -> {
                if (durationModified) yield Optional.empty();
                if (modifier.isRequiresMaxBase() && currentLevel != maxBase) yield Optional.empty();
                // Absolute duration set; if already splash, apply the splash multiplier again
                // so the combination is consistent regardless of application order.
                int newDuration = isSplash
                        ? (int) (modifier.getDurationSeconds() * getSplashMultiplier())
                        : modifier.getDurationSeconds();
                ItemStack result = buildPotionItem(effect, currentLevel, newDuration, isSplash, levelModified, true);
                yield Optional.of(recipe(result));
            }
            case SPLASH -> {
                if (isSplash) yield Optional.empty();
                if (modifier.isRequiresMaxBase() && currentLevel != maxBase) yield Optional.empty();
                int splashDuration = (int) (currentDuration * modifier.getDurationMultiplier());
                ItemStack result = buildPotionItem(effect, currentLevel, splashDuration, true, levelModified, durationModified);
                yield Optional.of(recipe(result));
            }
        };
    }

    /** Returns the lowest splash multiplier registered (used when a duration modifier is applied to an already-splash potion). */
    private double getSplashMultiplier() {
        // Default 0.5 — duration modifiers assume the gunpowder path if already splash.
        // This could be refined to track which splash modifier was used via an extra PDC key.
        return 0.5;
    }

    private static RecipeDefinition recipe(ItemStack output) {
        return RecipeDefinition.dynamic("alchemy", output, inp -> {
            consume(inp, "base", 1);
            consume(inp, "ingredient", 1);
        });
    }

    // ── Builders ─────────────────────────────────────────────────────────

    private ItemStack buildAwkwardPotion() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(org.bukkit.potion.PotionType.MUNDANE);
        meta.setColor(Color.fromRGB(100, 80, 150));
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(Keys.ITEM_ID_KEY, PersistentDataType.STRING, AWKWARD_POTION_ID);
        meta.getPersistentDataContainer().set(Keys.RARITY_KEY, PersistentDataType.STRING, "COMMON");
        meta.displayName(Formatter.format("<white>Awkward Potion"));
        List<Component> lore = new ArrayList<>();
        lore.add(Formatter.format("<gray>A base for brewing custom potions."));
        lore.add(Component.empty());
        lore.add(Formatter.format("<white>COMMON"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildPotion(AlchemyEffect effect, int level, int durationSeconds,
                                 boolean isSplash, boolean levelModified, boolean durationModified) {
        return buildPotionItem(effect, level, durationSeconds, isSplash, levelModified, durationModified);
    }

    private ItemStack buildPotionItem(AlchemyEffect effect, int level, int durationSeconds,
                                      boolean isSplash, boolean levelModified, boolean durationModified) {
        Material mat = isSplash ? Material.SPLASH_POTION : Material.POTION;
        ItemStack item = new ItemStack(mat);
        PotionMeta meta = (PotionMeta) item.getItemMeta();

        if (effect.getColor() != null) meta.setColor(effect.getColor());
        meta.setBasePotionType(org.bukkit.potion.PotionType.MUNDANE);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        meta.getPersistentDataContainer().set(Keys.ALCHEMY_EFFECT_ID,        PersistentDataType.STRING,  effect.getId());
        meta.getPersistentDataContainer().set(Keys.ALCHEMY_EFFECT_LEVEL,      PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(Keys.ALCHEMY_DURATION,          PersistentDataType.INTEGER, durationSeconds);
        meta.getPersistentDataContainer().set(Keys.ALCHEMY_IS_SPLASH,         PersistentDataType.BYTE,    isSplash ? (byte) 1 : (byte) 0);
        meta.getPersistentDataContainer().set(Keys.ALCHEMY_LEVEL_MODIFIED,    PersistentDataType.BYTE,    levelModified ? (byte) 1 : (byte) 0);
        meta.getPersistentDataContainer().set(Keys.ALCHEMY_DURATION_MODIFIED, PersistentDataType.BYTE,    durationModified ? (byte) 1 : (byte) 0);
        meta.getPersistentDataContainer().set(Keys.ITEM_ID_KEY,               PersistentDataType.STRING,  "alchemy:" + effect.getId());

        String rarityColor = getRarityColor(effect.getRarity());
        meta.displayName(Formatter.format(effect.getName() + " " + toRoman(level)));

        List<Component> lore = new ArrayList<>();
        if (effect.getLore() != null) {
            for (String line : effect.getLore()) lore.add(Formatter.format(line));
        }
        if (!effect.getStats().isEmpty()) {
            lore.add(Component.empty());
            var registry = org.nakii.valmora.api.ValmoraAPI.getInstance().getStatRegistry();
            for (String statId : effect.getStats().keySet()) {
                double val = effect.getStatValue(statId, level);
                String sign = val >= 0 && effect.getType() == AlchemyEffectType.BUFF ? "+" : "";
                String displayName = registry.get(statId)
                        .map(org.nakii.valmora.module.stat.StatDefinition::getDisplayName)
                        .orElse(statId);
                lore.add(Formatter.format("<gray>" + displayName + ": <" +
                        (effect.getType() == AlchemyEffectType.BUFF ? "green" : "red") + ">" + sign + (int) val));
            }
        }
        lore.add(Component.empty());
        lore.add(Formatter.format("<gray>Duration: <white>" + formatDuration(durationSeconds)));
        if (isSplash) lore.add(Formatter.format("<gray>Type: <light_purple>Splash"));
        lore.add(Component.empty());
        lore.add(Formatter.format(rarityColor + "<italic>" + effect.getRarity()));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String getItemKey(ItemStack item) {
        if (item.hasItemMeta()) {
            String id = item.getItemMeta().getPersistentDataContainer()
                    .get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
            // Valmora custom items: return the raw item ID (e.g. "enchanted_glowstone_dust")
            if (id != null && !id.startsWith("vanilla_") && !id.startsWith("alchemy:")) return id;
        }
        return "minecraft:" + item.getType().name().toLowerCase();
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() == 0;
    }

    private boolean isWaterBottle(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return false;
        if (item.hasItemMeta()) {
            String id = item.getItemMeta().getPersistentDataContainer()
                    .get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
            if (id != null) return false; // Any tagged Valmora potion is not a water bottle
            if (item.getItemMeta() instanceof PotionMeta pm) {
                return pm.getBasePotionType() == org.bukkit.potion.PotionType.WATER
                        || pm.getBasePotionType() == null;
            }
        }
        return item.getType() == Material.POTION;
    }

    private boolean isAwkwardPotion(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (AWKWARD_POTION_ID.equals(id)) return true;
        // Also accept vanilla awkward potions (e.g. from creative inventory)
        if (item.getItemMeta() instanceof PotionMeta pm) {
            return pm.getBasePotionType() == org.bukkit.potion.PotionType.AWKWARD;
        }
        return false;
    }

    private static void consume(Map<String, ItemStack> inputs, String key, int amount) {
        ItemStack item = inputs.get(key);
        if (item != null && !item.getType().isAir()) {
            item.setAmount(Math.max(0, item.getAmount() - amount));
        }
    }

    private int getAlchemyLevel(@Nullable Player player) {
        if (player == null) return 0;
        try {
            var session = plugin.getPlayerManager().getSession(player.getUniqueId());
            if (session == null) return 0;
            var profile = session.getActiveProfile();
            if (profile == null) return 0;
            double xp = profile.getSkillManager().getXp("alchemy");
            var data = plugin.getSkillModule().getSkillRegistry().getProgressData("default", xp);
            return data.currentLevel();
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatDuration(int seconds) {
        if (seconds >= 60) {
            int m = seconds / 60;
            int s = seconds % 60;
            return s == 0 ? m + "m" : m + "m " + s + "s";
        }
        return seconds + "s";
    }

    private String getRarityColor(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "UNCOMMON" -> "<green>";
            case "RARE"     -> "<blue>";
            case "EPIC"     -> "<dark_purple>";
            case "LEGENDARY"-> "<gold>";
            default         -> "<gray>";
        };
    }

    private String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            default -> String.valueOf(level);
        };
    }
}
