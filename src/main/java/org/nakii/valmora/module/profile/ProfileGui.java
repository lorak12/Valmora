package org.nakii.valmora.module.profile;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.module.economy.EconomyModule;
import org.nakii.valmora.module.skill.SkillDefinition;
import org.nakii.valmora.module.stat.SystemStats;
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ProfileGui {

    // ── Main GUI layout (4 rows = 36 slots) ───────────────────────────────────
    // Row 0: border · border · border · border · INFO · border · border · border · border
    // Row 1: border · P1    · border · P2    · border · P3    · border · P4    · border
    // Row 2: border (all)
    // Row 3: border · CREATE · border · border · CLOSE · border · border · border · border
    private static final int SIZE_MAIN = 36;
    private static final int[] PROFILE_SLOTS = {10, 12, 14, 16};
    private static final int SLOT_INFO   = 4;   // top row center
    private static final int SLOT_CREATE = 28;  // bottom row left area
    private static final int SLOT_CLOSE  = 31;  // bottom row center
    private static final String TITLE_MAIN = "<dark_gray>» <gold><bold>Profiles <dark_gray>«";

    // ── Confirm dialog layout (3 rows = 27 slots) ─────────────────────────────
    // Row 0: border (all, center slot 4 = prompt)
    // Row 1: border · border · border · ACCEPT(12) · border · DENY(14) · border · border · border
    // Row 2: border (all)
    private static final int SIZE_CONFIRM = 27;
    private static final int SLOT_CONFIRM_ACCEPT = 12; // 4th from left
    private static final int SLOT_CONFIRM_DENY   = 14; // 4th from right
    private static final int SLOT_CONFIRM_PROMPT = 4;  // top row center
    private static final String TITLE_CONFIRM = "<dark_gray>» <red><bold>Delete Profile? <dark_gray>«";

    // ── State ─────────────────────────────────────────────────────────────────
    private static final Set<UUID> mainGuiPlayers    = new HashSet<>();
    private static final Set<UUID> confirmGuiPlayers = new HashSet<>();
    // player UUID → profile UUID pending deletion
    private static final Map<UUID, UUID> pendingDelete = new HashMap<>();
    private static Listener listener;
    private static Valmora plugin;

    private ProfileGui() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static void register(Valmora p) {
        plugin = p;
        listener = new GuiListener();
        p.getServer().getPluginManager().registerEvents(listener, p);
    }

    public static void unregister() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        mainGuiPlayers.clear();
        confirmGuiPlayers.clear();
        pendingDelete.clear();
    }

    // ── Main GUI ──────────────────────────────────────────────────────────────

    public static void open(Player player, PlayerManager pm) {
        Inventory inv = Bukkit.createInventory(null, SIZE_MAIN, Formatter.format(TITLE_MAIN));

        ItemStack border = borderPane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE_MAIN; i++) inv.setItem(i, border);

        // Info slot — top row center
        inv.setItem(SLOT_INFO, item(Material.PAPER,
                "<yellow><bold>Profile Manager",
                "<dark_gray>──────────────────────",
                "<gray>Click a profile to <white>switch<gray> to it.",
                "<gray>Shift-click a profile to <red>delete<gray> it.",
                "<dark_gray>──────────────────────",
                "<dark_gray>Cannot delete your active or only profile."));

        ValmoraPlayer vp = pm.getSession(player.getUniqueId());

        if (vp != null) {
            List<ValmoraProfile> profiles = new ArrayList<>(vp.getProfiles().values());
            UUID activeId = vp.getActiveProfile() != null ? vp.getActiveProfile().getId() : null;
            double totalCoins = getTotalCoins(player.getUniqueId());

            for (int i = 0; i < PROFILE_SLOTS.length; i++) {
                if (i < profiles.size()) {
                    ValmoraProfile profile = profiles.get(i);
                    boolean active = profile.getId().equals(activeId);
                    inv.setItem(PROFILE_SLOTS[i], profileCard(profile, active, totalCoins));
                } else {
                    inv.setItem(PROFILE_SLOTS[i], emptySlotItem());
                }
            }

            boolean atMax = profiles.size() >= pm.getMaxProfiles();
            if (atMax) {
                inv.setItem(SLOT_CREATE, item(Material.GRAY_DYE,
                        "<gray><bold>Create Profile",
                        "<dark_gray>Maximum of " + pm.getMaxProfiles() + " profiles reached."));
            } else {
                inv.setItem(SLOT_CREATE, item(Material.LIME_DYE,
                        "<green><bold>Create Profile",
                        "<gray>Click to create a new profile.",
                        "<dark_gray>(" + profiles.size() + " / " + pm.getMaxProfiles() + " used)"));
            }
        }

        inv.setItem(SLOT_CLOSE, item(Material.BARRIER,
                "<red><bold>Close",
                "<gray>Close this menu."));

        mainGuiPlayers.add(player.getUniqueId());
        player.openInventory(inv);
    }

    // ── Confirm dialog ────────────────────────────────────────────────────────

    private static void openConfirm(Player player, ValmoraProfile profile) {
        Inventory inv = Bukkit.createInventory(null, SIZE_CONFIRM, Formatter.format(TITLE_CONFIRM));

        ItemStack border = borderPane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE_CONFIRM; i++) inv.setItem(i, border);

        inv.setItem(SLOT_CONFIRM_PROMPT, item(Material.ORANGE_DYE,
                "<gold><bold>Are you sure?",
                "<dark_gray>──────────────────────",
                "<gray>You are about to permanently delete:",
                "<white>  " + profile.getName(),
                "<dark_red>This action cannot be undone.",
                "<dark_gray>──────────────────────"));

        inv.setItem(SLOT_CONFIRM_ACCEPT, item(Material.LIME_CONCRETE,
                "<green><bold>✔  Confirm Delete",
                "<dark_gray>──────────────────────",
                "<gray>Permanently removes <white>" + profile.getName() + "<gray>.",
                "<dark_red>Cannot be undone."));

        inv.setItem(SLOT_CONFIRM_DENY, item(Material.RED_CONCRETE,
                "<red><bold>✗  Cancel",
                "<dark_gray>──────────────────────",
                "<gray>Go back to your profiles."));

        confirmGuiPlayers.add(player.getUniqueId());
        player.openInventory(inv);
    }

    // ── Profile card builder ──────────────────────────────────────────────────

    private static ItemStack profileCard(ValmoraProfile profile, boolean active, double totalCoins) {
        Material mat = active ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE;
        String nameTag = active ? "<green><bold>★  " : "<white>";

        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>──────────────────────");

        // Health & Mana
        try {
            SystemStats sys = ValmoraAPI.getInstance().getSystemStats();
            if (sys != null) {
                double maxHp  = profile.getStatManager().getStat(sys.getHealth());
                double curHp  = profile.getPlayerState().getCurrentHealth();
                double maxMp  = profile.getStatManager().getStat(sys.getMana());
                double curMp  = profile.getPlayerState().getCurrentMana();
                lore.add(String.format("<gray>❤ Health  <red>%s <dark_gray>/ <white>%s",
                        formatStat(curHp), formatStat(maxHp)));
                lore.add(String.format("<gray>✦ Mana    <aqua>%s <dark_gray>/ <white>%s",
                        formatStat(curMp), formatStat(maxMp)));
            }
        } catch (Exception ignored) {}

        lore.add("<dark_gray>──────────────────────");

        // Total skill levels
        try {
            int totalLevel = 0;
            for (SkillDefinition skill : Valmora.getInstance().getSkillModule().getSkillRegistry().values()) {
                totalLevel += profile.getSkillManager().getLevel(skill.getId());
            }
            lore.add("<gray>⚔ Skill Level  <yellow>" + totalLevel);
        } catch (Exception ignored) {}

        // Coins (shared per player)
        lore.add("<gray>🪙 Coins  <gold>" + EconomyModule.formatCoins(totalCoins));

        lore.add("<dark_gray>──────────────────────");

        // Active indicator / last used
        if (active) {
            lore.add("<green>✔ Active Profile");
            lore.add("<gray>Last used: <white>Just now");
        } else {
            lore.add("<gray>Last used: <white>" + formatTimeSince(profile.getLastUsed()));
        }

        lore.add("<dark_gray>──────────────────────");
        lore.add("<dark_gray>Shift-click to delete");

        return item(mat, nameTag + profile.getName(), lore.toArray(new String[0]));
    }

    private static ItemStack emptySlotItem() {
        return item(Material.LIME_STAINED_GLASS_PANE,
                "<green>+ Empty Slot",
                "<gray>Click <white>Create Profile<gray> to fill.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static double getTotalCoins(UUID uuid) {
        try {
            return ValmoraAPI.getInstance().getEconomyModule().getTotal(uuid);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String formatStat(double value) {
        long v = Math.round(value);
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000.0);
        if (v >= 1_000)     return String.format("%.1fk", v / 1_000.0);
        return String.valueOf(v);
    }

    private static String formatTimeSince(long millis) {
        if (millis <= 0) return "Never";
        long diff = System.currentTimeMillis() - millis;
        if (diff < 60_000)          return "Just now";
        if (diff < 3_600_000)       return (diff / 60_000) + " min ago";
        if (diff < 86_400_000)      return (diff / 3_600_000) + " hr ago";
        if (diff < 604_800_000)     return (diff / 86_400_000) + " days ago";
        return (diff / 604_800_000) + " weeks ago";
    }

    private static ItemStack borderPane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack item(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Formatter.format(name));
        if (lore.length > 0) {
            List<Component> loreList = new ArrayList<>();
            for (String line : lore) loreList.add(Formatter.format(line));
            meta.lore(loreList);
        }
        item.setItemMeta(meta);
        return item;
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    private static final class GuiListener implements Listener {

        @EventHandler(priority = EventPriority.HIGH)
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            UUID uuid = player.getUniqueId();

            if (confirmGuiPlayers.contains(uuid)) {
                event.setCancelled(true);
                if (event.getClickedInventory() == null ||
                        event.getClickedInventory() != event.getView().getTopInventory()) return;
                handleConfirmClick(player, event.getSlot());
                return;
            }

            if (!mainGuiPlayers.contains(uuid)) return;
            event.setCancelled(true);
            if (event.getClickedInventory() == null ||
                    event.getClickedInventory() != event.getView().getTopInventory()) return;
            handleMainClick(player, event.getSlot(), event.isShiftClick());
        }

        private void handleMainClick(Player player, int slot, boolean shift) {
            PlayerManager pm = plugin.getPlayerManager();
            UUID uuid = player.getUniqueId();

            if (slot == SLOT_CLOSE) {
                player.closeInventory();
                return;
            }

            if (slot == SLOT_CREATE) {
                ValmoraPlayer vp = pm.getSession(uuid);
                if (vp == null) return;
                if (vp.getProfiles().size() >= pm.getMaxProfiles()) {
                    player.sendMessage(Formatter.format(
                            "<dark_gray>[<gold>Valmora<dark_gray>] <red>You have reached the profile limit of "
                            + pm.getMaxProfiles() + "."));
                    return;
                }
                pm.createNextProfile(uuid);
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, pm), 1L);
                return;
            }

            for (int i = 0; i < PROFILE_SLOTS.length; i++) {
                if (slot != PROFILE_SLOTS[i]) continue;
                ValmoraPlayer vp = pm.getSession(uuid);
                if (vp == null) return;
                List<ValmoraProfile> profiles = new ArrayList<>(vp.getProfiles().values());
                if (i >= profiles.size()) return;

                ValmoraProfile profile = profiles.get(i);

                if (shift) {
                    if (vp.getProfiles().size() <= 1) {
                        player.sendMessage(Formatter.format(
                                "<dark_gray>[<gold>Valmora<dark_gray>] <red>You cannot delete your only profile."));
                        return;
                    }
                    if (profile.getId().equals(vp.getActiveProfile().getId())) {
                        player.sendMessage(Formatter.format(
                                "<dark_gray>[<gold>Valmora<dark_gray>] <red>Switch to another profile before deleting this one."));
                        return;
                    }
                    pendingDelete.put(uuid, profile.getId());
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> openConfirm(player, profile), 1L);
                } else {
                    if (profile.getId().equals(vp.getActiveProfile().getId())) {
                        player.sendMessage(Formatter.format(
                                "<dark_gray>[<gold>Valmora<dark_gray>] <yellow>That profile is already active."));
                        return;
                    }
                    pm.switchProfile(player, profile.getId());
                    player.sendMessage(Formatter.format(
                            "<dark_gray>[<gold>Valmora<dark_gray>] <green>Switched to '<white>"
                            + profile.getName() + "<green>'."));
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, pm), 1L);
                }
                return;
            }
        }

        private void handleConfirmClick(Player player, int slot) {
            PlayerManager pm = plugin.getPlayerManager();
            UUID uuid = player.getUniqueId();

            if (slot == SLOT_CONFIRM_DENY) {
                pendingDelete.remove(uuid);
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, pm), 1L);
                return;
            }

            if (slot == SLOT_CONFIRM_ACCEPT) {
                UUID profileId = pendingDelete.remove(uuid);
                if (profileId == null) {
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, pm), 1L);
                    return;
                }
                ValmoraPlayer vp = pm.getSession(uuid);
                String name = vp != null && vp.getProfiles().containsKey(profileId)
                        ? vp.getProfiles().get(profileId).getName() : "Unknown";
                pm.deleteProfile(uuid, profileId);
                player.sendMessage(Formatter.format(
                        "<dark_gray>[<gold>Valmora<dark_gray>] <green>Profile '<white>"
                        + name + "<green>' deleted."));
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, pm), 1L);
            }
        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player player)) return;
            UUID uuid = player.getUniqueId();
            mainGuiPlayers.remove(uuid);
            confirmGuiPlayers.remove(uuid);
            // Clear pending delete after a short delay — the re-open scheduled via runTaskLater(1L)
            // adds the player back before this fires, so state is preserved during refreshes.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!mainGuiPlayers.contains(uuid) && !confirmGuiPlayers.contains(uuid)) {
                    pendingDelete.remove(uuid);
                }
            }, 3L);
        }
    }
}
