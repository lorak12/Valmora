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
    }

    /**
     * Spawns a floating damage indicator using a TextDisplay entity.
     * @param result The result of the combat calculation.
     */
    public void spawnIndicator(DamageResult result) {
        // Rate limiting for DoTs
        UUID victimId = result.getVictim().getUniqueId();
        long now = System.currentTimeMillis();
        
        // Spawn at most 1 indicator every 400ms per entity
        if (lastIndicatorSpawned.containsKey(victimId) && (now - lastIndicatorSpawned.get(victimId)) < 400) {
            return;
        }
        lastIndicatorSpawned.put(victimId, now);

        Location baseLoc = result.getVictim().getEyeLocation();
        
        // Random offset to prevent overlap
        double offsetX = (random.nextDouble() - 0.5) * 0.5;
        double offsetY = (random.nextDouble() - 0.5) * 0.5;
        double offsetZ = (random.nextDouble() - 0.5) * 0.5;
        Location spawnLocation = baseLoc.clone().add(offsetX, offsetY, offsetZ);

        TextDisplay display = spawnLocation.getWorld().spawn(spawnLocation, TextDisplay.class);
        display.text(getIndicatorComponent(result));
        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
        activeIndicators.add(display);
        
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            display.remove();
            activeIndicators.remove(display);
        }, 20L);
    }

    private Component getIndicatorComponent(DamageResult result) {
        String damageStr = String.valueOf((int) result.getFinalDamage());
        String color = result.getDamageType().getColor();
        
        if (result.isCritical()) {
            // Shiny critical hit symbols
            return Formatter.format("<gold>✧ " + color + "<b>" + damageStr + "<gold> ✧");
        }
        
        return Formatter.format(color + damageStr);
    }
}
