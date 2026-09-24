package org.nakii.valmora.module.combat;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class DamageIndicatorManager {

    private final Valmora plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> lastIndicatorSpawned = new HashMap<>();
    private final List<TextDisplay> activeIndicators = new ArrayList<>();

    public DamageIndicatorManager(Valmora plugin) {
        this.plugin = plugin;
    }

    public void cleanup() {
        for (TextDisplay display : activeIndicators) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        activeIndicators.clear();
        lastIndicatorSpawned.clear();
    }

    /**
     * Spawns a floating damage indicator using a TextDisplay entity.
     * @param result The result of the combat calculation.
     */
    public void spawnIndicator(DamageResult result) {
        // The immune flag already suppresses damage application (see DamageApplier) — it should
        // suppress the visual indicator too, not just the damage.
        if (result.isImmune()) return;

        // Rate limiting for DoTs
        UUID victimId = result.getVictim().getUniqueId();
        long now = System.currentTimeMillis();
        long rateLimitMs = plugin.getConfig().getLong("combat.damage-indicator-rate-limit-ms", 400);

        // Spawn at most 1 indicator every `rateLimitMs` per entity
        if (lastIndicatorSpawned.containsKey(victimId) && (now - lastIndicatorSpawned.get(victimId)) < rateLimitMs) {
            return;
        }
        lastIndicatorSpawned.put(victimId, now);

        Location baseLoc = result.getVictim().getEyeLocation();

        // Random offset to prevent overlap — HC-028
        double offsetSpread = plugin.getConfig().getDouble("combat.damage-indicator.offset", 0.5);
        double offsetX = (random.nextDouble() - 0.5) * offsetSpread;
        double offsetY = (random.nextDouble() - 0.5) * offsetSpread;
        double offsetZ = (random.nextDouble() - 0.5) * offsetSpread;
        Location spawnLocation = baseLoc.clone().add(offsetX, offsetY, offsetZ);

        // Tagged + non-persistent inside the spawn consumer, so the display is never saved to disk
        // (it used to be: a crash or a chunk unload during its lifetime left it floating forever).
        TextDisplay display = spawnLocation.getWorld().spawn(spawnLocation, TextDisplay.class,
                entity -> org.nakii.valmora.util.TransientEntities.mark(entity, "combat"));
        display.text(getIndicatorComponent(result));
        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
        activeIndicators.add(display);
        long lifetimeTicks = plugin.getConfig().getLong("combat.damage-indicator-lifetime-ticks", 20);

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            display.remove();
            activeIndicators.remove(display);
        }, lifetimeTicks);
    }

    /** HC-028: crit-format/normal-format/show-as-int are configurable — placeholders {color}, {damage}. */
    private Component getIndicatorComponent(DamageResult result) {
        boolean showAsInt = plugin.getConfig().getBoolean("combat.damage-indicator.show-as-int", true);
        String damageStr = showAsInt ? String.valueOf((int) result.getFinalDamage()) : String.valueOf(result.getFinalDamage());
        String color = result.getDamageType().getColor();

        if (result.isCritical()) {
            String critFormat = plugin.getConfig().getString("combat.damage-indicator.crit-format",
                    "<gold>✧ {color}<b>{damage}<gold> ✧");
            return Formatter.format(critFormat.replace("{color}", color).replace("{damage}", damageStr));
        }

        String normalFormat = plugin.getConfig().getString("combat.damage-indicator.normal-format", "{color}{damage}");
        return Formatter.format(normalFormat.replace("{color}", color).replace("{damage}", damageStr));
    }
}
