package org.nakii.valmora.database;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/**
 * Versioned upgrades for the JSON blobs stored in a {@code valmora_profiles} row.
 *
 * <p>The row's {@code data_version} column records which shape its blobs are in. On load,
 * {@link #migrate} walks the row forward one step at a time — the same pattern as the table-level
 * ladder in {@link SQLDataStore#applyMigrations} — so hydration code only ever sees the newest
 * shape and never has to guess from the JSON itself (as it used to for {@code player_state} and
 * {@code collections}). Saves always write {@link #LATEST_VERSION}.
 *
 * <p>To change a blob's shape: bump {@link #LATEST_VERSION}, add a {@code toVN} step that rewrites
 * the affected columns, and update the hydration code for the new shape only.
 *
 * <p>Steps must be pure JSON rewrites. Migrations that need live content definitions (e.g. moving
 * quest progress from index keys to objective ids) belong in the reference-resolution pass that
 * runs after hydration, since definitions can change on any reload, not just on upgrade.
 */
public final class ProfileMigrator {

    /** The profile data version this build writes. */
    public static final int LATEST_VERSION = 3;

    /**
     * From this version on, the {@code stats} blob holds allocations (offsets from each stat's
     * current default) instead of absolute base values. That's a change in meaning, not shape,
     * and converting needs the stat registry, so there's no JSON step. The loader picks the
     * interpretation from the row's version (see SQLDataStore.loadPlayer).
     */
    public static final int STAT_ALLOCATIONS_VERSION = 3;

    private ProfileMigrator() {}

    /**
     * Upgrades {@code columns} (column name → raw JSON, values may be {@code null}) in place from
     * {@code fromVersion} to {@link #LATEST_VERSION}.
     *
     * @throws IllegalStateException if the row was written by a newer plugin version
     */
    public static void migrate(Map<String, String> columns, int fromVersion) {
        if (fromVersion > LATEST_VERSION) {
            throw new IllegalStateException("Profile data version " + fromVersion
                    + " is newer than this plugin supports (" + LATEST_VERSION + ")");
        }
        if (fromVersion < 1) toV1(columns);
        if (fromVersion < 2) toV2(columns);
    }

    /**
     * v2 — {@code skills}: the bare {@code skillId → xp} map becomes
     * {@code {xp: {...}, rewarded: {}}}, adding the per-skill reward ledger. The empty ledger means
     * "rewarded up to the current level", filled in by SkillManager once the skill registry is
     * available — existing players are never re-granted old rewards.
     */
    private static void toV2(Map<String, String> columns) {
        String skills = columns.get("skills");
        if (skills == null) return;
        JsonElement parsed = JsonParser.parseString(skills);
        if (parsed.isJsonObject() && !parsed.getAsJsonObject().has("xp")) {
            JsonObject obj = new JsonObject();
            obj.add("xp", parsed);
            obj.add("rewarded", new JsonObject());
            columns.put("skills", obj.toString());
        }
    }

    /**
     * v1 — normalizes the two blobs whose shape was previously guessed at load time:
     * <ul>
     *   <li>{@code player_state}: a bare {@code [health, mana]} array becomes the object shape
     *       ({@code health}, {@code mana}, {@code lastCombatTime}, {@code zoneId}).</li>
     *   <li>{@code collections}: a bare {@code id → count} map becomes
     *       {@code {counts: {...}, grantedStages: {}}}. The empty ledger keeps the existing
     *       one-time catch-up re-grant behavior for pre-ledger saves.</li>
     * </ul>
     */
    private static void toV1(Map<String, String> columns) {
        String state = columns.get("player_state");
        if (state != null) {
            JsonElement parsed = JsonParser.parseString(state);
            if (parsed.isJsonArray()) {
                JsonArray arr = parsed.getAsJsonArray();
                JsonObject obj = new JsonObject();
                obj.addProperty("health", arr.size() > 0 ? arr.get(0).getAsDouble() : 0.0);
                obj.addProperty("mana", arr.size() > 1 ? arr.get(1).getAsDouble() : 0.0);
                obj.addProperty("lastCombatTime", 0L);
                obj.add("zoneId", JsonNull.INSTANCE);
                columns.put("player_state", obj.toString());
            }
        }

        String collections = columns.get("collections");
        if (collections != null) {
            JsonElement parsed = JsonParser.parseString(collections);
            if (parsed.isJsonObject() && !parsed.getAsJsonObject().has("counts")) {
                JsonObject obj = new JsonObject();
                obj.add("counts", parsed);
                obj.add("grantedStages", new JsonObject());
                columns.put("collections", obj.toString());
            }
        }
    }
}
