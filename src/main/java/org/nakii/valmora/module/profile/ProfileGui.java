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
import org.nakii.valmora.util.Formatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ProfileGui {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int SIZE = 36; // 4 rows
    // Row 1: border · P1 · border · P2 · border · P3 · border · P4 · border
    private static final int[] PROFILE_SLOTS = {10, 12, 14, 16};
    private static final int MAX_PROFILES = 4;
    // Row 3 action bar
    private static final int SLOT_CREATE  = 28; // also used as CANCEL in confirm mode
    private static final int SLOT_INFO    = 31; // hint / confirm prompt
    private static final int SLOT_CLOSE   = 34; // also used as CONFIRM DELETE in confirm mode
    private static final String TITLE = "<dark_gray>» <gold><bold>Profiles <dark_gray>«";

    // ── State ─────────────────────────────────────────────────────────────────
    private static final Set<UUID> openPlayers = new HashSet<>();
    // player UUID → profile UUID they want to delete (confirmation pending)
    private static final Map<UUID, UUID> pendingDelete = new HashMap<>();
    private static Listener listener;

    private ProfileGui() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static void register(Valmora plugin) {
        listener = new GuiListener(plugin);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    public static void unregister() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        openPlayers.clear();
        pendingDelete.clear();
    }

    // ── GUI builder ───────────────────────────────────────────────────────────

    public static void open(Player player, PlayerManager pm) {
        Inventory inv = Bukkit.createInventory(null, SIZE, Formatter.format(TITLE));

        // Fill with dark border
        ItemStack border = borderPane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, border);

        ValmoraPlayer vp = pm.getSession(player.getUniqueId());
        boolean inConfirm = pendingDelete.containsKey(player.getUniqueId());

        if (vp != null) {
            List<ValmoraProfile> profiles = new ArrayList<>(vp.getProfiles().values());
            UUID activeId = vp.getActiveProfile() != null ? vp.getActiveProfile().getId() : null;
            UUID deletingId = pendingDelete.get(player.getUniqueId());

            for (int i = 0; i < PROFILE_SLOTS.length; i++) {
                if (i < profiles.size()) {
                    ValmoraProfile profile = profiles.get(i);
                    boolean active = profile.getId().equals(activeId);
                    boolean markedForDelete = profile.getId().equals(deletingId);
                    inv.setItem(PROFILE_SLOTS[i], profileCard(profile, active, markedForDelete, inConfirm));
                } else if (!inConfirm) {
                    // Empty slot placeholder (only shown in normal mode)
                    inv.setItem(PROFILE_SLOTS[i], emptySlotItem());
                } else {
                    inv.setItem(PROFILE_SLOTS[i], borderPane(Material.BLACK_STAINED_GLASS_PANE));
                }
            }

            if (inConfirm) {
                // Confirmation mode: show cancel + confirm buttons
                ValmoraProfile toDelete = findProfile(vp, deletingId);
                String profileName = toDelete != null ? toDelete.getName() : "?";

                inv.setItem(SLOT_CREATE, item(Material.GRAY_CONCRETE,
                        "<gray><bold>← Go Back",
                        "<gray>Cancel deletion."));
                inv.setItem(SLOT_INFO, item(Material.ORANGE_DYE,
                        "<gold><bold>Delete Profile?",
                        "<gray>You are about to permanently delete:",
                        "<white>  " + profileName,
                        "<dark_red>This cannot be undone."));
                inv.setItem(SLOT_CLOSE, item(Material.RED_CONCRETE,
                        "<red><bold>Confirm Delete",
                        "<gray>Click to permanently delete",
                        "<gray><white>" + profileName + "<gray>."));
            } else {
                // Normal mode
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
                inv.setItem(SLOT_INFO, item(Material.PAPER,
                        "<yellow>Profile Manager",
                        "<gray>Click a profile to <white>switch<gray> to it.",
                        "<gray>Shift-click a profile to <red>delete<gray> it.",
                        "<dark_gray>Cannot delete your only or active profile."));
                inv.setItem(SLOT_CLOSE, item(Material.BARRIER,
                        "<red>Close",
                        "<gray>Close this menu."));
            }
        }

        openPlayers.add(player.getUniqueId());
        player.openInventory(inv);
    }

    private static ValmoraProfile findProfile(ValmoraPlayer vp, UUID id) {
        if (id == null) return null;
        return vp.getProfiles().get(id);
    }

    // ── Item helpers ──────────────────────────────────────────────────────────

    private static ItemStack profileCard(ValmoraProfile profile, boolean active, boolean markedForDelete, boolean inConfirm) {
        Material mat;
        String namePrefix;
        List<String> lore = new ArrayList<>();

        if (markedForDelete && inConfirm) {
            mat = Material.RED_CONCRETE;
            namePrefix = "<red><bold>";
            lore.add("<dark_gray>──────────────────");
            lore.add("<red>⚠ Pending deletion");
            lore.add("<dark_gray>──────────────────");
            lore.add("<gray>Confirm or cancel below.");
        } else if (active) {
            mat = Material.LIME_CONCRETE;
            namePrefix = "<green><bold>";
            lore.add("<dark_gray>──────────────────");
            lore.add("<green>✔ Active Profile");
            lore.add("<dark_gray>──────────────────");
            if (!inConfirm) lore.add("<gray>Shift-click to delete");
        } else {
            mat = Material.GRAY_CONCRETE;
            namePrefix = "<white>";
            lore.add("<dark_gray>──────────────────");
            if (inConfirm) {
                lore.add("<gray>Click to switch");
            } else {
                lore.add("<gray>Click to switch");
                lore.add("<dark_gray>──────────────────");
                lore.add("<gray>Shift-click to delete");
            }
        }

        return item(mat, namePrefix + profile.getName(), lore.toArray(new String[0]));
    }

    private static ItemStack emptySlotItem() {
        return item(Material.BLACK_STAINED_GLASS_PANE,
                "<dark_gray>Empty Slot",
                "<gray>Click <white>Create Profile<gray> to fill.");
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

        private final Valmora plugin;

        GuiListener(Valmora plugin) { this.plugin = plugin; }

        @EventHandler(priority = EventPriority.HIGH)
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (!openPlayers.contains(player.getUniqueId())) return;
            event.setCancelled(true);

            if (event.getClickedInventory() == null ||
                    event.getClickedInventory() != event.getView().getTopInventory()) return;

            int slot = event.getSlot();
            PlayerManager pm = plugin.getPlayerManager();
            boolean inConfirm = pendingDelete.containsKey(player.getUniqueId());

            if (inConfirm) {
                handleConfirmClick(player, pm, slot);
            } else {
                handleNormalClick(player, pm, slot, event.isShiftClick());
            }
        }

        private void handleNormalClick(Player player, PlayerManager pm, int slot, boolean shift) {
            if (slot == SLOT_CLOSE) {
                player.closeInventory();
                return;
            }

            if (slot == SLOT_CREATE) {
                ValmoraPlayer vp = pm.getSession(player.getUniqueId());
                if (vp == null) return;
                if (vp.getProfiles().size() >= pm.getMaxProfiles()) {
                    player.sendMessage(Formatter.format(
                            "<dark_gray>[<gold>Valmora<dark_gray>] <red>You already have the maximum of "
                            + pm.getMaxProfiles() + " profiles."));
                    return;
                }
                player.closeInventory();
                pm.createNextProfile(player.getUniqueId());
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, pm), 1L);
                return;
            }

            for (int i = 0; i < PROFILE_SLOTS.length; i++) {
                if (slot != PROFILE_SLOTS[i]) continue;

                ValmoraPlayer vp = pm.getSession(player.getUniqueId());
                if (vp == null) return;
                List<ValmoraProfile> profiles = new ArrayList<>(vp.getProfiles().values());
                if (i >= profiles.size()) return;

                ValmoraProfile profile = profiles.get(i);

                if (shift) {
                    // Start delete confirmation flow
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
                    pendingDelete.put(player.getUniqueId(), profile.getId());
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, pm), 1L);
                } else {
                    // Switch profile
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

        private void handleConfirmClick(Player player, PlayerManager pm, int slot) {
            if (slot == SLOT_CREATE) {
                // Cancel
                pendingDelete.remove(player.getUniqueId());
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, pm), 1L);
                return;
            }

            if (slot == SLOT_CLOSE) {
                // Confirm delete
                UUID profileId = pendingDelete.remove(player.getUniqueId());
                if (profileId == null) return;

                ValmoraPlayer vp = pm.getSession(player.getUniqueId());
                if (vp == null) return;
                ValmoraProfile profile = vp.getProfiles().get(profileId);
                String profileName = profile != null ? profile.getName() : "Unknown";

                pm.deleteProfile(player.getUniqueId(), profileId);
                player.sendMessage(Formatter.format(
                        "<dark_gray>[<gold>Valmora<dark_gray>] <green>Profile '<white>"
                        + profileName + "<green>' deleted."));
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, pm), 1L);
                return;
            }

            // Allow clicking profiles to switch even in confirm mode
            for (int i = 0; i < PROFILE_SLOTS.length; i++) {
                if (slot != PROFILE_SLOTS[i]) continue;
                ValmoraPlayer vp = pm.getSession(player.getUniqueId());
                if (vp == null) return;
                List<ValmoraProfile> profiles = new ArrayList<>(vp.getProfiles().values());
                if (i >= profiles.size()) return;

                ValmoraProfile profile = profiles.get(i);
                UUID deletingId = pendingDelete.get(player.getUniqueId());
                if (profile.getId().equals(deletingId)) return; // can't switch to the one being deleted

                if (!profile.getId().equals(vp.getActiveProfile().getId())) {
                    pendingDelete.remove(player.getUniqueId());
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

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player player)) return;
            UUID uuid = player.getUniqueId();
            openPlayers.remove(uuid);
            // Clear pending delete after a tick — if the GUI reopens within that tick
            // (e.g. via runTaskLater for refresh), open() will re-add the player before
            // this fires. If the player fully closed the menu, the state is discarded.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!openPlayers.contains(uuid)) {
                    pendingDelete.remove(uuid);
                }
            }, 2L);
        }
    }
}
