package org.nakii.valmora.module.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.database.DataStore;
import org.nakii.valmora.infrastructure.config.YamlLoader;
import org.nakii.valmora.module.gui.components.InputComponent;
import org.nakii.valmora.module.gui.components.StorageComponent;
import org.nakii.valmora.module.gui.event.OpenDialogInputEventFactory;
import org.nakii.valmora.module.gui.parser.GuiDefinitionParser;
import org.nakii.valmora.module.gui.renderer.GuiRenderer;
import org.nakii.valmora.module.gui.storage.GuiSlotItemHandle;
import org.nakii.valmora.module.gui.storage.ItemBindingHandle;
import org.nakii.valmora.module.gui.storage.ItemStorageCodec;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.util.Keys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class GuiModule implements ReloadableModule {

    /** HookBus point prefix for GUI lifecycle event blocks — see {@link GuiEventBlockStage}. */
    private static final String GUI_POINT_PREFIX = "gui:";

    /** Point name for a GUI's {@code on-open} block. Failing it aborts the GUI open (see {@link GuiEventBlockStage}). */
    public static String onOpenPoint(String guiId) { return GUI_POINT_PREFIX + guiId + ":on_open"; }
    /** Point name for a GUI's {@code on-close} block. */
    public static String onClosePoint(String guiId) { return GUI_POINT_PREFIX + guiId + ":on_close"; }
    /** Point name for a GUI's {@code on-slot-update} block. */
    public static String onSlotUpdatePoint(String guiId) { return GUI_POINT_PREFIX + guiId + ":on_slot_update"; }
    /** Point name for a GUI's {@code on-update} (repeating timer) block. */
    public static String onUpdatePoint(String guiId) { return GUI_POINT_PREFIX + guiId + ":on_update"; }

    private final Valmora plugin;
    private final DataStore dataStore;
    private final Map<String, GuiDefinition> guiRegistry = new HashMap<>();
    private final Map<UUID, GuiSession> openSessions = new HashMap<>();
    private GuiListener listener;
    private final List<String> registeredCommandNames = new ArrayList<>();
    private org.nakii.valmora.module.gui.sign.SignInputManager signInputManager;
    private org.nakii.valmora.module.gui.sign.SignInputListener signInputListener;

    public GuiModule(Valmora plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    @Override
    public void onEnable() {
        this.listener = new GuiListener(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        plugin.getScriptModule().registerProvider(new GuiVariableProvider(plugin));
        plugin.getScriptModule().registerProvider(new CandidateVariableProvider());
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.SoundEventFactory());
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.OpenGuiEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.GuiBackEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.CloseEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.GiveXpEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.EnchantApplyEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.EnchantSelectEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.EnchantRemoveEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.EnchantBackEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.GuiForceCraftEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.AlchemyBrewStartEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.AlchemyBrewEventFactory(plugin));
        plugin.getScriptModule().registerEvent(new org.nakii.valmora.module.gui.event.RecalculateStatsEventFactory(plugin));

        plugin.getScriptModule().registerEvent(new OpenDialogInputEventFactory(plugin, this));

        // Virtual sign input (added 2026-08-07 — the classes existed but were never wired up
        // anywhere, so `open_sign_input` was a silently-dead DSL event).
        this.signInputManager = new org.nakii.valmora.module.gui.sign.SignInputManager(plugin);
        signInputManager.init();
        this.signInputListener = new org.nakii.valmora.module.gui.sign.SignInputListener(plugin, signInputManager, this);
        plugin.getServer().getPluginManager().registerEvents(signInputListener, plugin);
        plugin.getScriptModule().registerEvent(
                new org.nakii.valmora.module.gui.event.OpenSignInputEventFactory(plugin, signInputManager, this));


        loadGuis();
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            org.bukkit.event.HandlerList.unregisterAll(listener);
            listener = null;
        }
        for (UUID uuid : new HashSet<>(openSessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) continue;
            GuiSession session = openSessions.get(uuid);
            closeGuiSession(player);
            // The listener is already gone, so an inventory left open here would behave as a
            // plain chest after a reload (display items and already-persisted STORAGE contents
            // could be taken out — a dupe). Close it for real.
            if (session != null && player.getOpenInventory().getTopInventory() == session.getInventory()) {
                player.closeInventory();
            }
        }
        unregisterGuiCommands();
        if (plugin.getScriptModule() != null) {
            plugin.getScriptModule().getHookBus().clearYamlStages(GUI_POINT_PREFIX);
        }
        if (signInputListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(signInputListener);
            signInputListener = null;
        }
        if (signInputManager != null) {
            signInputManager.cleanup();
            signInputManager = null;
        }
    }

    /** Registers {@code def}'s lifecycle event blocks onto the shared HookBus — see {@link GuiEventBlockStage}. */
    private void registerEventBlockStages(GuiDefinition def) {
        var bus = plugin.getScriptModule().getHookBus();
        if (def.getOnOpen() != null) {
            bus.registerYamlStage(onOpenPoint(def.getId()), new GuiEventBlockStage(def.getId() + ":on-open", def.getOnOpen()));
        }
        if (def.getOnClose() != null) {
            bus.registerYamlStage(onClosePoint(def.getId()), new GuiEventBlockStage(def.getId() + ":on-close", def.getOnClose()));
        }
        if (def.getOnSlotUpdate() != null) {
            bus.registerYamlStage(onSlotUpdatePoint(def.getId()), new GuiEventBlockStage(def.getId() + ":on-slot-update", def.getOnSlotUpdate()));
        }
        if (def.getOnUpdate() != null) {
            bus.registerYamlStage(onUpdatePoint(def.getId()), new GuiEventBlockStage(def.getId() + ":on-update", def.getOnUpdate()));
        }
    }

    // ------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------

    public void openGui(Player player, String id, Map<String, Object> props) {
        openGuiInternal(player, id, props, null, null);
    }

    public void openGui(Player player, String id) {
        openGui(player, id, new HashMap<>());
    }

    /**
     * Opens a top-level GUI remembering {@code parentSession} as where to return via the generic
     * {@code gui_back} script event (added 2026-08-07 — {@code GuiSession.parent} previously was
     * only ever set by the nested item-bound-storage flow, so general GUI-to-GUI "back" navigation
     * had no way to work despite being documented). Distinct from {@link #openNestedGui} — this
     * path does not bind an item storage handle, it's purely for chaining ordinary screens.
     */
    public void openGui(Player player, String id, Map<String, Object> props, GuiSession parentSession) {
        openGuiInternal(player, id, props, parentSession, null);
    }

    /** Opens a GUI bound to a physical item's own storage (e.g. a backpack right-clicked in hand). */
    public void openItemBoundGui(Player player, String id, ItemBindingHandle handle) {
        openGuiInternal(player, id, new HashMap<>(), null, handle);
    }

    /** Opens a GUI bound to an item sitting inside another GUI's storage slot (nested container). */
    public void openNestedGui(Player player, String id, Map<String, Object> props, GuiSession parentSession, int parentSlot) {
        ItemBindingHandle handle = new GuiSlotItemHandle(parentSession, parentSlot);
        openGuiInternal(player, id, props, parentSession, handle);
    }

    private void openGuiInternal(Player player, String id, Map<String, Object> props,
                                  @Nullable GuiSession parentSession, @Nullable ItemBindingHandle boundHandle) {
        GuiDefinition def = id == null ? null : guiRegistry.get(id.toLowerCase(java.util.Locale.ROOT));
        if (def == null) return;

        GuiRenderer renderer = new GuiRenderer(plugin);
        // Create a prop-bearing temp session BEFORE running on-open so $prop.*$ resolves correctly.
        GuiSession tempSession = new GuiSession(player, def, null, props);
        String resolvedTitle = renderer.resolveVariables(def.getTitle(), tempSession, null, null);

        // Fire onOpen — use tempSession context so PropVariableProvider can read incoming props.
        // Routed through the shared HookBus (see GuiEventBlockStage): a failed condition or an
        // aborted action list both report INTERRUPT, which aborts the open exactly like the old
        // inline dispatch's early `return` did. Also lets addon Java hooks veto a GUI open.
        GuiExecutionContext openCtx = new GuiExecutionContext(player, tempSession);
        if (!plugin.getScriptModule().getHookBus().runPoint(onOpenPoint(def.getId()), openCtx)) {
            return;
        }

        Set<String> playerStorageIds = new HashSet<>();
        for (GuiComponent component : def.getComponents().values()) {
            if (component instanceof StorageComponent storage && storage.getOwner() == StorageComponent.Owner.PLAYER) {
                playerStorageIds.add(storage.getStorageId());
            }
        }

        if (playerStorageIds.isEmpty()) {
            Map<String, ItemStack[]> preloaded = loadItemOwnedStorage(def, boundHandle);
            finishOpenGui(player, def, props, parentSession, boundHandle, resolvedTitle, preloaded);
            return;
        }

        ValmoraProfile profile = getActiveProfile(player);
        if (profile == null) {
            // No active profile — proceed with empty PLAYER-owned storage rather than blocking open.
            Map<String, ItemStack[]> preloaded = loadItemOwnedStorage(def, boundHandle);
            finishOpenGui(player, def, props, parentSession, boundHandle, resolvedTitle, preloaded);
            return;
        }

        Map<String, ItemStack[]> preloaded = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String storageId : playerStorageIds) {
            int size = countStorageSlots(def, storageId);
            futures.add(dataStore.loadStorage(profile.getId(), storageId, size)
                    .thenAccept(items -> preloaded.put(storageId, items)));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        // Never open with empty storage after a failed read: the close would
                        // persist the empty contents over the real ones.
                        player.sendMessage(org.nakii.valmora.util.Formatter.format(
                                "<red>That storage could not be loaded right now. Please try again."));
                        return;
                    }
                    preloaded.forEach(profile::putStorage);
                    Map<String, ItemStack[]> merged = new HashMap<>(preloaded);
                    merged.putAll(loadItemOwnedStorage(def, boundHandle));
                    finishOpenGui(player, def, props, parentSession, boundHandle, resolvedTitle, merged);
                })
        );
    }

    private @Nullable ValmoraProfile getActiveProfile(Player player) {
        ValmoraPlayer session = plugin.getPlayerManager().getSession(player.getUniqueId());
        return session != null ? session.getActiveProfile() : null;
    }

    private Map<String, ItemStack[]> loadItemOwnedStorage(GuiDefinition def, @Nullable ItemBindingHandle boundHandle) {
        Map<String, ItemStack[]> result = new HashMap<>();
        if (boundHandle == null) return result;
        ItemStack boundItem = boundHandle.getItem();
        for (GuiComponent component : def.getComponents().values()) {
            if (!(component instanceof StorageComponent storage) || storage.getOwner() != StorageComponent.Owner.ITEM
                    || result.containsKey(storage.getStorageId())) {
                continue;
            }
            int size = countStorageSlots(def, storage);
            byte[] bytes = null;
            if (boundItem != null && boundItem.hasItemMeta()) {
                bytes = boundItem.getItemMeta().getPersistentDataContainer()
                        .get(Keys.STORAGE_CONTENTS_KEY, PersistentDataType.BYTE_ARRAY);
            }
            result.put(storage.getStorageId(), ItemStorageCodec.deserialize(bytes, size, plugin.getLogger()));
        }
        return result;
    }

    private void finishOpenGui(Player player, GuiDefinition def, Map<String, Object> props,
                                @Nullable GuiSession parentSession, @Nullable ItemBindingHandle boundHandle,
                                String resolvedTitle, Map<String, ItemStack[]> preloadedStorage) {
        GuiRenderer renderer = new GuiRenderer(plugin);
        Inventory inv = Bukkit.createInventory(null, def.getRows() * 9, org.nakii.valmora.util.Formatter.format(resolvedTitle));
        GuiSession session = new GuiSession(player, def, inv, props);
        session.setParent(parentSession);
        session.setBoundItemHandle(boundHandle);
        session.setInitialStorageContents(preloadedStorage);

        // Fixed 2026-08-07: navigating from a GUI with an update-interval straight into another
        // GUI (via open_gui) previously just overwrote the player's session entry, leaving the
        // old session's repeating on-update task running forever — a real per-navigation leak,
        // continuing to fire on-update scripts against a session whose inventory was no longer
        // even open.
        GuiSession previous = openSessions.get(player.getUniqueId());
        if (previous != null && previous.getUpdateTask() != null) {
            previous.getUpdateTask().cancel();
        }

        openSessions.put(player.getUniqueId(), session);

        renderer.render(session);

        player.openInventory(inv);

        if (def.getUpdateIntervalTicks() > 0) {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                // 1. Run the on-update script if present — always re-renders after, regardless of
                // outcome, so the runPoint() result is intentionally ignored here (unlike on-open).
                GuiExecutionContext updateContext = new GuiExecutionContext(player, session);
                plugin.getScriptModule().getHookBus().runPoint(onUpdatePoint(def.getId()), updateContext);

                // 2. Re-render the GUI
                renderer.render(session);
            },
                    def.getUpdateIntervalTicks(), def.getUpdateIntervalTicks());
            session.setUpdateTask(task);
        }
    }

    // ------------------------------------------------------------------
    // Closing
    // ------------------------------------------------------------------

    public void closeGuiSession(Player player) {
        GuiSession session = openSessions.remove(player.getUniqueId());
        if (session != null) {
            finalizeSessionClose(player, session);
        }
    }

    /** Closes the (nested) child session and reopens its parent, e.g. leaving a backpack GUI back into the bag it was opened from. */
    public void resumeParentSession(Player player, GuiSession childSession, GuiSession parentSession) {
        openSessions.remove(player.getUniqueId());
        finalizeSessionClose(player, childSession);

        openSessions.put(player.getUniqueId(), parentSession);
        new GuiRenderer(plugin).render(parentSession);
        player.openInventory(parentSession.getInventory());
    }

    private void finalizeSessionClose(Player player, GuiSession session) {
        if (session.getUpdateTask() != null) session.getUpdateTask().cancel();

        GuiExecutionContext context = new GuiExecutionContext(player, session);
        plugin.getScriptModule().getHookBus().runPoint(onClosePoint(session.getDefinition().getId()), context);

        persistAllStorage(session);

        // --- BEST PRACTICE ITEM REFUND LOGIC (INPUT slots only — STORAGE slots are durably persisted, never refunded) ---
        Inventory inv = session.getInventory();
        List<List<Character>> layout = session.getDefinition().getLayout();

        for (int r = 0; r < layout.size(); r++) {
            List<Character> row = layout.get(r);
            for (int c = 0; c < row.size(); c++) {
                char ch = row.get(c);
                if (session.getDefinition().getComponents().get(ch) instanceof InputComponent) {
                    int slot = r * 9 + c;
                    ItemStack item = inv.getItem(slot);

                    if (item != null && item.getType() != Material.AIR) {
                        // 1. Clear slot FIRST to prevent race-condition dupes
                        inv.setItem(slot, null);

                        // 2. Add to player inventory
                        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);

                        // 3. If inventory is full, drop leftovers at player's location
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Storage persistence (called from here on close, and from GuiListener
    // on every accepted click/drag mutation for crash-durable write-through)
    // ------------------------------------------------------------------

    public void persistStorageComponent(GuiSession session, StorageComponent storage) {
        List<Integer> slots = findStorageSlots(session.getDefinition(), storage);
        Inventory inv = session.getInventory();
        ItemStack[] contents = new ItemStack[slots.size()];
        for (int i = 0; i < slots.size(); i++) contents[i] = inv.getItem(slots.get(i));

        if (storage.getOwner() == StorageComponent.Owner.PLAYER) {
            ValmoraProfile profile = getActiveProfile(session.getPlayer());
            if (profile != null) {
                profile.putStorage(storage.getStorageId(), contents);
                dataStore.saveStorage(profile.getId(), storage.getStorageId(), contents);
            }
        } else if (session.getBoundItemHandle() != null) {
            writeItemStorage(session.getBoundItemHandle(), contents);
        }
    }

    /**
     * Writes a PLAYER-owned storage's contents to both the profile's in-memory mirror and the
     * database. For code that edits a storage outside of an open GUI (e.g. quiver auto-refill) —
     * updating only the in-memory mirror would let the next join reload the stale DB copy.
     */
    public void persistPlayerStorage(ValmoraProfile profile, String storageId, ItemStack[] contents) {
        profile.putStorage(storageId, contents);
        dataStore.saveStorage(profile.getId(), storageId, contents);
    }

    private void persistAllStorage(GuiSession session) {
        Set<StorageComponent> seen = new HashSet<>();
        for (GuiComponent component : session.getDefinition().getComponents().values()) {
            if (component instanceof StorageComponent storage && seen.add(storage)) {
                persistStorageComponent(session, storage);
            }
        }
    }

    private void writeItemStorage(ItemBindingHandle handle, ItemStack[] contents) {
        ItemStack item = handle.getItem();
        if (item == null) return;
        ItemStack updated = item.clone();
        ItemMeta meta = updated.getItemMeta();
        if (meta == null) return;
        byte[] bytes = ItemStorageCodec.serialize(contents, plugin.getLogger());
        if (bytes != null) {
            meta.getPersistentDataContainer().set(Keys.STORAGE_CONTENTS_KEY, PersistentDataType.BYTE_ARRAY, bytes);
        }
        updated.setItemMeta(meta);
        handle.writeBack(updated);
    }

    private List<Integer> findStorageSlots(GuiDefinition def, StorageComponent target) {
        List<Integer> slots = new ArrayList<>();
        List<List<Character>> layout = def.getLayout();
        for (int r = 0; r < layout.size(); r++) {
            List<Character> row = layout.get(r);
            for (int c = 0; c < row.size(); c++) {
                if (def.getComponents().get(row.get(c)) == target) slots.add(r * 9 + c);
            }
        }
        return slots;
    }

    private int countStorageSlots(GuiDefinition def, String storageId) {
        int count = 0;
        for (List<Character> row : def.getLayout()) {
            for (char c : row) {
                GuiComponent comp = def.getComponents().get(c);
                if (comp instanceof StorageComponent storage && storage.getStorageId().equals(storageId)) count++;
            }
        }
        return count;
    }

    private int countStorageSlots(GuiDefinition def, StorageComponent target) {
        int count = 0;
        for (List<Character> row : def.getLayout()) {
            for (char c : row) {
                if (def.getComponents().get(c) == target) count++;
            }
        }
        return count;
    }

    public GuiSession getSession(UUID uuid) {
        return openSessions.get(uuid);
    }

    @Override
    public String getId() {
        return "gui";
    }

    @Override
    public String getName() {
        return "GUI System";
    }

    private void loadGuis() {
        unregisterGuiCommands();
        guiRegistry.clear();
        plugin.getScriptModule().getHookBus().clearYamlStages(GUI_POINT_PREFIX);
        GuiDefinitionParser parser = new GuiDefinitionParser(plugin);
        YamlLoader<GuiDefinition> loader = new YamlLoader<>(plugin, "guis", "GUIs");
        loader.load(parser::parse, def -> {
            guiRegistry.put(def.getId().toLowerCase(java.util.Locale.ROOT), def);
            if (def.getCommand() != null) {
                registerGuiCommand(def);
            }
            registerEventBlockStages(def);
        });
        if (!registeredCommandNames.isEmpty()) {
            syncCommandsWithClients();
        }
    }

    private void registerGuiCommand(GuiDefinition def) {
        org.bukkit.command.CommandMap commandMap = Bukkit.getServer().getCommandMap();
        String name = def.getCommand().toLowerCase();
        GuiOpenCommand cmd = new GuiOpenCommand(name, def.getId(), def.getCommandPermission(), this);
        if (commandMap.register("valmora", cmd)) {
            registeredCommandNames.add(name);
            plugin.getLogger().info("[GUI] Registered command /" + name + " → opens GUI '" + def.getId() + "'");
        } else {
            plugin.getLogger().warning("[GUI] Could not register command /" + name + " for GUI '" + def.getId() + "' — name already taken.");
        }
    }

    private void unregisterGuiCommands() {
        if (registeredCommandNames.isEmpty()) return;
        Map<String, org.bukkit.command.Command> knownCommands = Bukkit.getServer().getCommandMap().getKnownCommands();
        for (String name : registeredCommandNames) {
            knownCommands.remove(name);
            knownCommands.remove("valmora:" + name);
        }
        registeredCommandNames.clear();
        syncCommandsWithClients();
    }

    private void syncCommandsWithClients() {
        try {
            plugin.getServer().getClass().getMethod("syncCommands").invoke(plugin.getServer());
        } catch (Exception e) {
            plugin.getLogger().warning("[GUI] Could not sync commands with clients: " + e.getMessage());
        }
    }

    public Map<String, GuiDefinition> getGuiRegistry() {
        return guiRegistry;
    }
}
