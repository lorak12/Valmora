package org.nakii.valmora.module.item;

import org.nakii.valmora.Valmora;

/**
 * HC-051: every {@code AbilityMechanic} falls back to a hardcoded literal when its YAML params
 * omit a field (e.g. {@code DamageMechanic}'s {@code damage: 1.0} default). Those literals used
 * to be the only source of truth — a server wanting to retune a mechanic's default globally had
 * to edit every ability YAML that used it. This reads {@code mechanics.<mechanic-id>.defaults.<key>}
 * from {@code config.yml} first, falling back to the mechanic's own hardcoded literal (passed in
 * as {@code fallback}) when unset — so the shipped behavior is unchanged unless a server opts in.
 */
public final class MechanicDefaults {

    private MechanicDefaults() {
    }

    private static String path(String mechanicId, String key) {
        return "mechanics." + mechanicId + ".defaults." + key;
    }

    public static double getDouble(String mechanicId, String key, double fallback) {
        Valmora plugin = Valmora.getInstance();
        return plugin != null && plugin.getConfig() != null
                ? plugin.getConfig().getDouble(path(mechanicId, key), fallback) : fallback;
    }

    public static int getInt(String mechanicId, String key, int fallback) {
        Valmora plugin = Valmora.getInstance();
        return plugin != null && plugin.getConfig() != null
                ? plugin.getConfig().getInt(path(mechanicId, key), fallback) : fallback;
    }

    public static long getLong(String mechanicId, String key, long fallback) {
        Valmora plugin = Valmora.getInstance();
        return plugin != null && plugin.getConfig() != null
                ? plugin.getConfig().getLong(path(mechanicId, key), fallback) : fallback;
    }

    public static boolean getBoolean(String mechanicId, String key, boolean fallback) {
        Valmora plugin = Valmora.getInstance();
        return plugin != null && plugin.getConfig() != null
                ? plugin.getConfig().getBoolean(path(mechanicId, key), fallback) : fallback;
    }

    public static String getString(String mechanicId, String key, String fallback) {
        Valmora plugin = Valmora.getInstance();
        return plugin != null && plugin.getConfig() != null
                ? plugin.getConfig().getString(path(mechanicId, key), fallback) : fallback;
    }
}
