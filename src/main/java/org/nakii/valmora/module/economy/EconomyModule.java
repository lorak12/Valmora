package org.nakii.valmora.module.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.api.economy.EconomyService;
import org.nakii.valmora.database.DataStore;
import org.nakii.valmora.module.economy.event.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyModule implements ReloadableModule, EconomyService {

    private final Valmora plugin;
    private final DataStore dataStore;
    private final Map<UUID, EconomyData> cache = new HashMap<>();
    private EconomyListener listener;

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
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        for (Map.Entry<UUID, EconomyData> entry : cache.entrySet()) {
            dataStore.saveEconomy(entry.getKey(), entry.getValue().getPurse(), entry.getValue().getBank()).join();
        }
        cache.clear();
    }

    @Override
    public String getId() { return "economy"; }

    @Override
    public String getName() { return "Economy"; }

    // --- Join / Quit ---

    public void handleJoin(UUID uuid) {
        dataStore.loadEconomy(uuid).thenAcceptAsync(row -> {
            EconomyData data = row != null ? new EconomyData(row[0], row[1]) : new EconomyData(0, 0);
            Bukkit.getScheduler().runTask(plugin, () -> cache.put(uuid, data));
        });
    }

    public void handleQuit(UUID uuid) {
        EconomyData data = cache.remove(uuid);
        if (data != null) {
            dataStore.saveEconomy(uuid, data.getPurse(), data.getBank());
        }
    }

    // --- Read ---

    public double getPurse(UUID uuid) { return cache.getOrDefault(uuid, EMPTY).getPurse(); }
    public double getBank(UUID uuid)  { return cache.getOrDefault(uuid, EMPTY).getBank(); }
    public double getTotal(UUID uuid) { return cache.getOrDefault(uuid, EMPTY).getTotal(); }

    // --- Mutate purse ---

    public void addPurse(UUID uuid, double amount) {
        getOrCreate(uuid).setPurse(getPurse(uuid) + amount);
    }

    public void removePurse(UUID uuid, double amount) {
        getOrCreate(uuid).setPurse(Math.max(0, getPurse(uuid) - amount));
    }

    public boolean hasPurse(UUID uuid, double amount) { return getPurse(uuid) >= amount; }

    // --- Mutate bank ---

    public void addBank(UUID uuid, double amount) {
        getOrCreate(uuid).setBank(getBank(uuid) + amount);
    }

    public void removeBank(UUID uuid, double amount) {
        getOrCreate(uuid).setBank(Math.max(0, getBank(uuid) - amount));
    }

    // --- Transfers ---

    public boolean deposit(UUID uuid, double amount) {
        if (amount <= 0 || !hasPurse(uuid, amount)) return false;
        removePurse(uuid, amount);
        addBank(uuid, amount);
        return true;
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (amount <= 0 || getBank(uuid) < amount) return false;
        removeBank(uuid, amount);
        addPurse(uuid, amount);
        return true;
    }

    public void depositAll(UUID uuid) {
        double purse = getPurse(uuid);
        if (purse > 0) {
            removePurse(uuid, purse);
            addBank(uuid, purse);
        }
    }

    public void withdrawAll(UUID uuid) {
        double bank = getBank(uuid);
        if (bank > 0) {
            removeBank(uuid, bank);
            addPurse(uuid, bank);
        }
    }

    // --- Formatting ---

    public static String formatCoins(double amount) {
        long r = Math.round(amount);
        if (r >= 1_000_000_000) return String.format("%.2fb", amount / 1_000_000_000.0);
        if (r >= 1_000_000)     return String.format("%.2fm", amount / 1_000_000.0);
        if (r >= 1_000)         return String.format("%.1fk", amount / 1_000.0);
        return String.valueOf(r);
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
