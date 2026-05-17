package org.nakii.valmora.module.quest.journal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.quest.QuestDefinition;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.module.quest.QuestObjective;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.List;

public class JournalManager implements Listener {

    private static final String TITLE = "Quest Journal";

    public void openJournal(Player player) {
        QuestManager qm = ValmoraAPI.getInstance().getQuestManager();
        if (qm == null) return;
        ValmoraPlayer vp = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
        if (vp == null) return;
        ValmoraProfile profile = vp.getActiveProfile();
        if (profile == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, Formatter.format("<dark_green><bold>" + TITLE));
        int slot = 0;

        for (QuestDefinition quest : qm.getRegistry().values()) {
            if (slot >= 54) break;
            String status = qm.getStatus(profile, quest.getId());
            ItemStack icon = buildQuestIcon(quest, status, profile, qm);
            inv.setItem(slot++, icon);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Component title = event.getView().title();
        if (!title.equals(Formatter.format("<dark_green><bold>" + TITLE))) return;
        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------

    private ItemStack buildQuestIcon(QuestDefinition quest, String status,
                                     ValmoraProfile profile, QuestManager qm) {
        Material mat = switch (status) {
            case QuestManager.STATUS_IN_PROGRESS -> Material.WRITABLE_BOOK;
            case QuestManager.STATUS_COMPLETED   -> Material.WRITTEN_BOOK;
            case QuestManager.STATUS_FAILED      -> Material.BARRIER;
            default                              -> Material.BOOK;
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(quest.getName())
                .color(statusColor(status))
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Status: " + formatStatus(status))
                .color(statusColor(status))
                .decoration(TextDecoration.ITALIC, false));

        if (QuestManager.STATUS_IN_PROGRESS.equals(status)) {
            lore.add(Component.empty());
            lore.add(Component.text("Objectives:").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            List<QuestObjective> objectives = quest.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective obj = objectives.get(i);
                String key = obj.getId() != null ? obj.getId() : String.valueOf(i);
                int progress = getProgressByKey(profile, quest.getId(), key, qm);
                int required = obj.getRequired();
                boolean done = progress >= required;
                String bar = progressBar(progress, required);
                Component line = Component.text("  " + obj.getTarget() + " " + bar
                        + " " + progress + "/" + required)
                        .color(done ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false);
                lore.add(line);
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private int getProgressByKey(ValmoraProfile profile, String questId, String key, QuestManager qm) {
        Object p = profile.getVariables().get("quest." + questId + ".obj." + key);
        return p instanceof Number n ? n.intValue() : 0;
    }

    private String progressBar(int current, int required) {
        int total = 10;
        int filled = required > 0 ? (int) ((current / (double) required) * total) : total;
        return "[" + "█".repeat(filled) + "░".repeat(Math.max(0, total - filled)) + "]";
    }

    private NamedTextColor statusColor(String status) {
        return switch (status) {
            case QuestManager.STATUS_IN_PROGRESS -> NamedTextColor.YELLOW;
            case QuestManager.STATUS_COMPLETED   -> NamedTextColor.GREEN;
            case QuestManager.STATUS_FAILED      -> NamedTextColor.RED;
            default                              -> NamedTextColor.GRAY;
        };
    }

    private String formatStatus(String status) {
        return switch (status) {
            case QuestManager.STATUS_IN_PROGRESS -> "In Progress";
            case QuestManager.STATUS_COMPLETED   -> "Completed";
            case QuestManager.STATUS_FAILED      -> "Failed";
            default                              -> "Not Started";
        };
    }
}
