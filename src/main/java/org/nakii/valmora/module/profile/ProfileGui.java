package org.nakii.valmora.module.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ProfileGui {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int SIZE = 54; // 6 rows
    private static final int[] PROFILE_SLOTS = {10, 12, 14, 28, 30};
    private static final int MAX_PROFILES = PROFILE_SLOTS.length;
    private static final int SLOT_CREATE = 47;
    private static final int SLOT_INFO   = 49; // "shift-click to delete" hint
    private static final int SLOT_CLOSE  = 53;
    private static final String TITLE = "<dark_gray>» <gold><bold>Profile Manager <dark_gray>«";

    // ── State ─────────────────────────────────────────────────────────────────
    private static final Set<UUID> openPlayers   = new HashSet<>();
    private static final Set<UUID> pendingCreate = new HashSet<>();
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
        pendingCreate.clear();
    }

    // ── GUI builder ───────────────────────────────────────────────────────────

    public static void open(Player player, PlayerManager playerManager) {
        Inventory inv = Bukkit.createInventory(null, SIZE, Formatter.format(TITLE));

        // Fill all slots with gray glass border
        ItemStack border = glass(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, border);

        // Action row (row 5)
        inv.setItem(SLOT_CREATE, item(Material.LIME_DYE,
                "<green><bold>Create Profile",
                "<gray>Click to create a new profile.",
                "<dark_gray>Max " + MAX_PROFILES + " profiles."));
        inv.setItem(SLOT_INFO, item(Material.GRAY_DYE,
                "<gray>Delete Profile",
                "<gray>Shift-click a profile card",
                "<gray>to permanently delete it.",
                "<red>Cannot delete your active profile."));
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER,
                "<red>Close",
                "<gray>Close this menu."));

        // Profile cards
        ValmoraPlayer vp = playerManager.getSession(player.getUniqueId());
        if (vp == null) {
            openPlayers.add(player.getUniqueId());
            player.openInventory(inv);
            return;
        }

        List<ValmoraProfile> profiles = new ArrayList<>(vp.getProfiles().values());
        UUID activeId = vp.getActiveProfile() != null ? vp.getActiveProfile().getId() : null;

        for (int i = 0; i < profiles.size() && i < MAX_PROFILES; i++) {
            ValmoraProfile profile = profiles.get(i);
            boolean active = profile.getId().equals(activeId);
            inv.setItem(PROFILE_SLOTS[i], profileCard(profile, active));
        }

        // Next empty slot: show "New Profile" placeholder if room remains
        int used = Math.min(profiles.size(), MAX_PROFILES);
        if (used < MAX_PROFILES) {
            inv.setItem(PROFILE_SLOTS[used], item(Material.LIME_STAINED_GLASS_PANE,
                    "<green>Empty Slot",
                    "<gray>Click 'Create Profile' to fill this slot."));
        }

        openPlayers.add(player.getUniqueId());
        player.openInventory(inv);
    }

    // ── Item helpers ──────────────────────────────────────────────────────────

    private static ItemStack profileCard(ValmoraProfile profile, boolean active) {
        Material mat = active ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE;
        String nameTag = active ? "<green><bold>" : "<white>";
        String activeLine = active ? "<aqua>✔ Currently active" : "<gray>Click to switch";
        return item(mat,
                nameTag + profile.getName(),
                activeLine,
                "<dark_gray>Shift-click to delete");
    }

    private static ItemStack glass(Material mat) {
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

            // Only react to clicks inside the GUI's own inventory
            if (event.getClickedInventory() == null ||
                    event.getClickedInventory() != event.getView().getTopInventory()) return;

            int slot = event.getSlot();
            PlayerManager pm = plugin.getPlayerManager();

            if (slot == SLOT_CLOSE) {
                player.closeInventory();
                return;
            }

            if (slot == SLOT_CREATE) {
                player.closeInventory();
                ValmoraPlayer vp = pm.getSession(player.getUniqueId());
                if (vp != null && vp.getProfiles().size() >= MAX_PROFILES) {
                    player.sendMessage(Formatter.format(
                            "<dark_gray>[<gold>Valmora<dark_gray>] <red>You already have the maximum of "
                            + MAX_PROFILES + " profiles."));
                    return;
                }
                pendingCreate.add(player.getUniqueId());
                player.sendMessage(Formatter.format(
                        "<dark_gray>[<gold>Valmora<dark_gray>] <yellow>Type a name for your new profile in chat."
                        + " Type <red>cancel <yellow>to abort."));
                return;
            }

            // Profile card slots
            for (int i = 0; i < PROFILE_SLOTS.length; i++) {
                if (slot != PROFILE_SLOTS[i]) continue;

                ValmoraPlayer vp = pm.getSession(player.getUniqueId());
                if (vp == null) return;
                List<ValmoraProfile> profiles = new ArrayList<>(vp.getProfiles().values());
                if (i >= profiles.size()) return;

                ValmoraProfile profile = profiles.get(i);

                if (event.isShiftClick()) {
                    // Delete
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
                    pm.deleteProfile(player.getUniqueId(), profile.getId());
                    player.sendMessage(Formatter.format(
                            "<dark_gray>[<gold>Valmora<dark_gray>] <green>Profile '<white>"
                            + profile.getName() + "<green>' deleted."));
                    // Refresh GUI
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, pm), 1L);
                } else {
                    // Switch
                    if (profile.getId().equals(vp.getActiveProfile().getId())) {
                        player.sendMessage(Formatter.format(
                                "<dark_gray>[<gold>Valmora<dark_gray>] <yellow>That profile is already active."));
                        return;
                    }
                    pm.switchProfile(player, profile.getId());
                    player.sendMessage(Formatter.format(
                            "<dark_gray>[<gold>Valmora<dark_gray>] <green>Switched to '<white>"
                            + profile.getName() + "<green>'."));
                    // Refresh GUI to reflect the new active profile
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> open(player, pm), 1L);
                }
                return;
            }
        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (event.getPlayer() instanceof Player player) {
                openPlayers.remove(player.getUniqueId());
            }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
            Player player = event.getPlayer();
            if (!pendingCreate.contains(player.getUniqueId())) return;

            event.setCancelled(true);
            String input = PlainTextComponentSerializer.plainText()
                    .serialize(event.message()).trim();

            Bukkit.getScheduler().runTask(plugin, () -> {
                pendingCreate.remove(player.getUniqueId());

                if (input.equalsIgnoreCase("cancel")) {
                    player.sendMessage(Formatter.format(
                            "<dark_gray>[<gold>Valmora<dark_gray>] <gray>Profile creation cancelled."));
                    return;
                }

                if (input.length() < 3 || input.length() > 16) {
                    player.sendMessage(Formatter.format(
                            "<dark_gray>[<gold>Valmora<dark_gray>] <red>Name must be 3–16 characters."));
                    return;
                }

                if (!input.matches("[a-zA-Z0-9_]+")) {
                    player.sendMessage(Formatter.format(
                            "<dark_gray>[<gold>Valmora<dark_gray>] <red>Name may only contain letters, numbers, and underscores."));
                    return;
                }

                PlayerManager pm = plugin.getPlayerManager();
                ValmoraPlayer vp = pm.getSession(player.getUniqueId());
                if (vp == null) return;

                for (ValmoraProfile p : vp.getProfiles().values()) {
                    if (p.getName().equalsIgnoreCase(input)) {
                        player.sendMessage(Formatter.format(
                                "<dark_gray>[<gold>Valmora<dark_gray>] <red>A profile named '<white>"
                                + input + "<red>' already exists."));
                        return;
                    }
                }

                pm.createProfile(player.getUniqueId(), input);
                player.sendMessage(Formatter.format(
                        "<dark_gray>[<gold>Valmora<dark_gray>] <green>Profile '<white>"
                        + input + "<green>' created!"));
                open(player, pm);
            });
        }
    }
}
