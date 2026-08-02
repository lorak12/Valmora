package org.nakii.valmora.module.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.economy.EconomyService;
import org.nakii.valmora.database.DataStore;
import org.nakii.valmora.module.economy.event.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory-authoritative economy backend.
 *
 * <p>Every balance read/mutation is an O(1) operation against a {@link ConcurrentHashMap} of
 * per-player {@link EconomyData} (each of which is internally lock-protected, so different
 * players never contend with each other and same-player concurrent transactions serialize
 * correctly instead of losing updates). No transaction ever touches the database — this is
 * what lets thousands of transactions per second across up to ~10k cached players stay cheap:
 * throughput is bounded by map/lock operations in memory, not by disk or network I/O.
 *
 * <p>Persistence is write-behind: mutations mark the player dirty, and a background task
 * flushes every dirty player in a single batched transaction (one connection, one round-trip)
 * on an interval, again on quit, and once more on shutdown/reload — never one DB round-trip
 * per transaction, and never (with 10k cached players) one blocking round-trip per player.
 */
public class EconomyModule implements ReloadableModule, EconomyService {

    private final Valmora plugin;
    private final DataStore dataStore;
    private final Map<UUID, EconomyData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private EconomyListener listener;
    private BukkitTask flushTask;

    private static final EconomyData EMPTY = new EconomyData(0, 0);

    public EconomyModule(Valmora plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    @Override
    public void onEnable() {
        this.listener = new EconomyListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        plugin.getScriptModule().registerProvider(new EconomyVariableProvider(this));
        plugin.getScriptModule().registerEvent(new EconomyDepositEventFactory(this));
        plugin.getScriptModule().registerEvent(new EconomyWithdrawEventFactory(this));
        plugin.getScriptModule().registerEvent(new EconomyDepositAllEventFactory(this));
        plugin.getScriptModule().registerEvent(new EconomyAddEventFactory(this));
        plugin.getScriptModule().registerEvent(new EconomyRemoveEventFactory(this));

        // Load economy data for players already online (handles hot-reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            double[] row = dataStore.loadEconomy(player.getUniqueId()).join();
            cache.put(player.getUniqueId(), row != null ? new EconomyData(row[0], row[1]) : new EconomyData(0, 0));
        }

        long intervalTicks = plugin.getConfig().getLong("economy.autosave-interval-seconds", 60) * 20L;
        flushTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::flushDirty, intervalTicks, intervalTicks);
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }

        // Final flush: one batched transaction covering every cached player instead of one
        // blocking round-trip each — the difference between an instant reload/shutdown and a
        // multi-second (or worse) stall once thousands of players have been cached this session.
        Map<UUID, double[]> snapshot = new HashMap<>();
        for (Map.Entry<UUID, EconomyData> entry : cache.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().snapshot());
        }
        dirty.clear();
        dataStore.saveEconomyBatch(snapshot).join();
        cache.clear();
    }

    @Override
    public String getId() { return "economy"; }

    @Override
    public String getName() { return "Economy"; }

    // --- Join / Quit ---

    public void handleJoin(UUID uuid) {
        // Already cached (e.g. a quick rejoin within the same server session) — skip the DB
        // round-trip and keep serving the in-memory value, which is authoritative until flushed.
        if (cache.containsKey(uuid)) return;
        dataStore.loadEconomy(uuid).thenAcceptAsync(row -> {
            EconomyData data = row != null ? new EconomyData(row[0], row[1]) : new EconomyData(0, 0);
            Bukkit.getScheduler().runTask(plugin, () -> cache.putIfAbsent(uuid, data));
        });
    }

    public void handleQuit(UUID uuid) {
        // Deliberately NOT removed from cache: the memory cost of keeping a couple of doubles
        // per player who has ever joined this session is negligible even at 10k players, and
        // keeping the entry avoids a cache-miss race (and a spurious 0-balance read) if the
        // same player reconnects before an async reload would have completed. Still persisted
        // immediately here for durability in case the server never reaches a clean shutdown.
        EconomyData data = cache.get(uuid);
        if (data != null) {
            dirty.remove(uuid);
            dataStore.saveEconomy(uuid, data.getPurse(), data.getBank());
        }
    }

    // --- Periodic write-behind flush ---

    private void flushDirty() {
        if (dirty.isEmpty()) return;

        // Snapshot-and-clear-first: any mutation that races with this flush will simply
        // re-mark itself dirty (picked up next flush) rather than being silently dropped.
        Set<UUID> keys = new HashSet<>(dirty);
        dirty.removeAll(keys);

        Map<UUID, double[]> snapshot = new HashMap<>();
        for (UUID id : keys) {
            EconomyData data = cache.get(id);
            if (data != null) snapshot.put(id, data.snapshot());
        }
        if (!snapshot.isEmpty()) dataStore.saveEconomyBatch(snapshot);
    }

    // --- Read ---

    public double getPurse(UUID uuid) { return cache.getOrDefault(uuid, EMPTY).getPurse(); }
    public double getBank(UUID uuid)  { return cache.getOrDefault(uuid, EMPTY).getBank(); }
    public double getTotal(UUID uuid) { return cache.getOrDefault(uuid, EMPTY).getTotal(); }

    // --- Set (admin commands) ---

    public void setPurse(UUID uuid, double amount) {
        getOrCreate(uuid).setPurse(amount);
        dirty.add(uuid);
    }

    public void setBank(UUID uuid, double amount) {
        getOrCreate(uuid).setBank(amount);
        dirty.add(uuid);
    }

    // --- Mutate purse ---

    public void addPurse(UUID uuid, double amount) {
        getOrCreate(uuid).addPurse(amount);
        dirty.add(uuid);
    }

    public void removePurse(UUID uuid, double amount) {
        getOrCreate(uuid).removePurse(amount);
        dirty.add(uuid);
    }

    public boolean hasPurse(UUID uuid, double amount) { return getPurse(uuid) >= amount; }

    // --- Mutate bank ---

    public void addBank(UUID uuid, double amount) {
        getOrCreate(uuid).addBank(amount);
        dirty.add(uuid);
    }

    public void removeBank(UUID uuid, double amount) {
        getOrCreate(uuid).removeBank(amount);
        dirty.add(uuid);
    }

    // --- Transfers ---

    public boolean deposit(UUID uuid, double amount) {
        boolean ok = getOrCreate(uuid).deposit(amount);
        if (ok) dirty.add(uuid);
        return ok;
    }

    public boolean withdraw(UUID uuid, double amount) {
        boolean ok = getOrCreate(uuid).withdraw(amount);
        if (ok) dirty.add(uuid);
        return ok;
    }

    public void depositAll(UUID uuid) {
        double moved = getOrCreate(uuid).depositAll();
        if (moved > 0) dirty.add(uuid);
    }

    public void withdrawAll(UUID uuid) {
        double moved = getOrCreate(uuid).withdrawAll();
        if (moved > 0) dirty.add(uuid);
    }

    // --- Formatting ---

    public static String formatCoins(double amount) {
        long r = Math.round(amount);
        if (r >= 1_000_000_000) return String.format("%.2fb", amount / 1_000_000_000.0);
        if (r >= 1_000_000)     return String.format("%.2fm", amount / 1_000_000.0);
        if (r >= 1_000)         return String.format("%.1fk", amount / 1_000.0);
        return String.valueOf(r);
    }

    /** Formats a coin amount with dot-separated thousands and a coin emoji, e.g. "🪙 1.000.000". */
    public static String formatCoinsDisplay(double amount) {
        long r = Math.round(amount);
        String num = String.format(java.util.Locale.US, "%,d", r).replace(",", ".");
        return "🪙 " + num;
    }

    // --- EconomyService compat (operates on purse) ---

    @Override public void addCoins(Player player, double amount) { addPurse(player.getUniqueId(), amount); }
    @Override public void removeCoins(Player player, double amount) { removePurse(player.getUniqueId(), amount); }
    @Override public double getCoins(Player player) { return getPurse(player.getUniqueId()); }
    @Override public boolean hasCoins(Player player, double amount) { return hasPurse(player.getUniqueId(), amount); }

    // --- Private ---

    public EconomyData getOrCreateData(UUID uuid) {
        return cache.computeIfAbsent(uuid, k -> new EconomyData(0, 0));
    }

    private EconomyData getOrCreate(UUID uuid) {
        return getOrCreateData(uuid);
    }
}
