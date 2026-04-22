package org.nakii.valmora.module.ui;

import org.bukkit.entity.Player;
import org.nakii.valmora.module.skill.Skill;
import org.nakii.valmora.util.Formatter;

public class ChatUI {
    
    // Standardized prefix for system messages
    private final String PREFIX = "<dark_gray>[<gold>Valmora<dark_gray>] <white>";

    public void sendReward(Player player, String rewardName, int amount) {
        String msg = PREFIX + "You received: <green>" + amount + "x " + rewardName;
        player.sendMessage(Formatter.format(msg));
    }

    public void sendLevelUp(Player player, Skill skill, int newLevel) {
        sendLevelUp(player, skill.getName(), newLevel);
    }

    public void sendLevelUp(Player player, String skillName, int newLevel) {
        // A big, multi-line level up sequence
        player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
        player.sendMessage(Formatter.format(" <gold><bold>SKILL LEVEL UP!"));
        player.sendMessage(Formatter.format(" <gray>Your <aqua>" + skillName + " <gray>is now level <yellow>" + newLevel + "<gray>!"));
        player.sendMessage(Formatter.format("<dark_gray><st>                                                </st>"));
    }

    public void sendError(Player player, String error) {
        player.sendMessage(Formatter.format(PREFIX + "<red>" + error));
    }
}
