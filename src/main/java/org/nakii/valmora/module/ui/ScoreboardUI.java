package org.nakii.valmora.module.ui;

import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreboardUI {
    // Stores the current dynamic section for each player
    private final Map<UUID, DynamicSection> dynamicSections = new HashMap<>();

    public ScoreboardUI(Valmora plugin) {
    }

    private static class DynamicSection {
        boolean locked;

        DynamicSection(List<String> lines, boolean locked) {
            this.locked = locked;
        }
    }

    /**
     * Sets the flexible part of the scoreboard.
     * @param locked If true, subsequent calls to this method will be ignored until unlocked.
     */
    public void setDynamicSection(Player player, List<String> lines, boolean locked) {
        UUID uuid = player.getUniqueId();
        
        // If it's currently locked, and we are trying to overwrite it with a new locked/unlocked state, deny it
        // UNLESS we are explicitly passing an empty list to clear the lock.
        if (dynamicSections.containsKey(uuid) && dynamicSections.get(uuid).locked) {
            if (!lines.isEmpty()) {
                return; // Prevent overwrite
            }
        }

        if (lines.isEmpty()) {
            dynamicSections.remove(uuid);
        } else {
            dynamicSections.put(uuid, new DynamicSection(lines, locked));
        }
    }

    // Called automatically by the UIManager clock
    public void tick(Player player) {
        // In a real implementation, you would update your FastBoard or Bukkit Objective here.
        // Pseudo-code layout assembly:

        /*
        List<String> board = new ArrayList<>();
        board.add("<gold><bold>VALMORA RPG");
        board.add(" ");
        board.add("<gray>Purse: <gold>0 Coins"); // Fetch from profile economy
        board.add("<gray>Zone: <green>Safezone"); 
        board.add(" ");

        DynamicSection dynamic = dynamicSections.get(player.getUniqueId());
        if (dynamic != null) {
            board.addAll(dynamic.lines);
            board.add(" ");
        }

        board.add("<yellow>play.valmora.net");

        // Send 'board' via Formatter.formatList() to the player's scoreboard
        */
    }
}
