package org.nakii.valmora.module.skill;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.util.DebugManager;

import java.util.HashMap;
import java.util.Map;

public class SkillManager {

    // Maps Skill ID (String) to XP amount
    private final Map<String, Double> skillXp = new HashMap<>();
    /**
     * Skill id → highest level whose per-level/milestone rewards have been granted. Rewards are
     * granted exactly once per level, for levels in (rewarded, current], so changing a skill's XP
     * curve can neither re-grant rewards (curve made harder: the player drops and climbs back) nor
     * skip them (curve made easier: the player jumps several levels at once).
     */
    private final Map<String, Integer> rewardedLevel = new HashMap<>();

    /** Persisted shape of the {@code skills} column (profile data v2+). */
    public static class SaveData {
        public Map<String, Double> xp = new HashMap<>();
        public Map<String, Integer> rewarded = new HashMap<>();
    }
    // Injected registry for tests (bypasses the Valmora singleton)
    private final SkillRegistry injectedRegistry;

    public SkillManager() {
        this.injectedRegistry = null;
    }

    // Package-private constructor for unit tests — avoids Valmora.getInstance() dependency
    SkillManager(SkillRegistry registry) {
        this.injectedRegistry = registry;
    }

    public void loadData(Map<String, Double> savedData) {
        this.skillXp.clear();
        if (savedData != null) {
            for (Map.Entry<String, Double> entry : savedData.entrySet()) {
                this.skillXp.put(entry.getKey().toLowerCase(), entry.getValue());
            }
        }
    }

    public Map<String, Double> getSaveData() {
        return new HashMap<>(skillXp);
    }

    /**
     * Rewrites skill ids through {@code resolver} (e.g. following id aliases), merging with max
     * when two ids collapse into one. Returns how many ids changed.
     */
    public int remapIds(java.util.function.UnaryOperator<String> resolver) {
        int moved = 0;
        Map<String, Double> xp = new HashMap<>();
        for (Map.Entry<String, Double> e : skillXp.entrySet()) {
            String id = resolver.apply(e.getKey()).toLowerCase();
            if (!id.equals(e.getKey())) moved++;
            xp.merge(id, e.getValue(), Math::max);
        }
        Map<String, Integer> ledger = new HashMap<>();
        for (Map.Entry<String, Integer> e : rewardedLevel.entrySet()) {
            ledger.merge(resolver.apply(e.getKey()).toLowerCase(), e.getValue(), Math::max);
        }
        if (moved > 0) {
            skillXp.clear();
            skillXp.putAll(xp);
            rewardedLevel.clear();
            rewardedLevel.putAll(ledger);
        }
        return moved;
    }

    /** Drops a skill's XP and reward ledger entirely (orphan purge). */
    public void remove(String skillId) {
        skillXp.remove(skillId.toLowerCase());
        rewardedLevel.remove(skillId.toLowerCase());
    }

    /** XP plus the reward ledger, for persistence. */
    public SaveData getFullSaveData() {
        for (String skillId : skillXp.keySet()) ensureLedger(skillId);
        SaveData data = new SaveData();
        data.xp = new HashMap<>(skillXp);
        data.rewarded = new HashMap<>(rewardedLevel);
        return data;
    }

    public void loadFullData(SaveData data) {
        loadData(data != null ? data.xp : null);
        rewardedLevel.clear();
        if (data != null && data.rewarded != null) {
            data.rewarded.forEach((k, v) -> rewardedLevel.put(k.toLowerCase(), v));
        }
    }

    /**
     * Profiles saved before the ledger existed have no entry: treat everything up to the current
     * level as already rewarded (it was, under the curve at that time). Needs the skill registry,
     * so it's filled lazily on first use or save.
     */
    private void ensureLedger(String skillId) {
        String key = skillId.toLowerCase();
        if (rewardedLevel.containsKey(key)) return;
        SkillRegistry registry;
        try {
            registry = getSkillRegistry();
        } catch (RuntimeException moduleNotLoaded) { // not loaded (mid-reload) or no API (tests)
            return;
        }
        SkillDefinition skill = registry.getSkill(key).orElse(null);
        if (skill == null) return;
        rewardedLevel.put(key, Math.min(registry.getLevelFromXp(skill.getXpCurve(), getXp(key)), skill.getMaxLevel()));
    }

    public SkillRegistry getSkillRegistry() {
        if (injectedRegistry != null) return injectedRegistry;
        // Phase 5 (docs/REFACTOR/PROGRESS.md Task 17): routed through ModuleManager.getModule(id,
        // Class) — added in Phase 1 — rather than Valmora.getInstance(), since ValmoraAPI doesn't
        // expose the SkillModule itself (only the SkillManager, i.e. this class).
        return ValmoraAPI.getInstance().getModuleManager()
                .getModule("skills", SkillModule.class)
                .map(SkillModule::getSkillRegistry)
                .orElseThrow(() -> new IllegalStateException("Skill module is not available"));
    }

    public double getXp(String skillId) {
        return skillXp.getOrDefault(skillId.toLowerCase(), 0.0);
    }

    public double getXp(Skill skill) {
        return getXp(skill.name());
    }

    public int getLevel(String skillId) {
        SkillRegistry registry = getSkillRegistry();
        SkillDefinition skill = registry.getSkill(skillId).orElse(null);
        
        if (skill == null) return 0;

        // Capped: stored XP can exceed a max-level that was lowered in YAML.
        return Math.min(registry.getLevelFromXp(skill.getXpCurve(), getXp(skillId)), skill.getMaxLevel());
    }

    public int getLevel(Skill skill) {
        return getLevel(skill.name());
    }

    public int getLevelFromXp(String curveId, double xp) {
        return getSkillRegistry().getLevelFromXp(curveId, xp);
    }

    public void setXp(String skillId, double amount) {
        skillXp.put(skillId.toLowerCase(), amount);
        // An explicit (admin) XP change moves the reward ledger down with it, so lowering someone's
        // XP lets them earn those levels' rewards again. Curve changes don't go through here.
        Integer rewarded = rewardedLevel.get(skillId.toLowerCase());
        if (rewarded != null) {
            int level = getLevel(skillId);
            if (level < rewarded) rewardedLevel.put(skillId.toLowerCase(), level);
        }
    }

    public void setXp(Skill skill, double amount) {
        setXp(skill.name(), amount);
    }

    public void addXp(String skillId, double amount, Player player) {
        SkillRegistry registry = getSkillRegistry();
        SkillDefinition skill = registry.getSkill(skillId).orElse(null);
        
        if (skill == null) return;

        String key = skill.getId().toLowerCase();
        ensureLedger(key);

        double currentXp = getXp(key);
        int oldLevel = Math.min(registry.getLevelFromXp(skill.getXpCurve(), currentXp), skill.getMaxLevel());

        // Stop adding XP if they are already at the max level (but still settle any rewards owed,
        // e.g. after the curve was made easier).
        if (oldLevel >= skill.getMaxLevel()) {
            DebugManager.log("skills", "addXp REJECTED — " + player.getName() + " already at max level "
                    + skill.getMaxLevel() + " for skill=" + skillId);
            grantPendingRewards(skill, oldLevel, player);
            return;
        }

        double newXp = currentXp + amount;
        skillXp.put(key, newXp);

        // Call XP Gain Event
        SkillXpGainEvent event = new SkillXpGainEvent(player, skill, amount);
        event.callEvent();

        int newLevel = Math.min(registry.getLevelFromXp(skill.getXpCurve(), newXp), skill.getMaxLevel());

        DebugManager.log("skills", player.getName() + " skill=" + skillId + " +" + amount + "xp ("
                + currentXp + " -> " + newXp + ") level=" + oldLevel + (newLevel > oldLevel ? " -> " + newLevel : ""));

        if (newLevel > oldLevel) {
            // Call Level Up Event
            SkillLevelUpEvent levelUpEvent = new SkillLevelUpEvent(player, skill, oldLevel, newLevel);
            levelUpEvent.callEvent();
        }
        grantPendingRewards(skill, newLevel, player);
    }

    /**
     * Runs the per-level and milestone rewards for every level in (rewarded, currentLevel] and
     * advances the ledger. Levels at or below the ledger were already rewarded and are never
     * rewarded twice, even if the player dropped below them after a curve change and climbed back.
     */
    private void grantPendingRewards(SkillDefinition skill, int currentLevel, Player player) {
        String key = skill.getId().toLowerCase();
        int rewarded = rewardedLevel.getOrDefault(key, 0);
        if (currentLevel <= rewarded) return;

        // Prepare ExecutionContext for Script Engine rewards
        MemoryConfiguration params = new MemoryConfiguration();
        ExecutionContext context = new SimpleExecutionContext(player, player, player.getLocation(), params);

        // Execute rewards for EVERY level gained (in case they gained multiple levels at once)
        for (int lvl = rewarded + 1; lvl <= currentLevel; lvl++) {
            // Update the dynamic $param.level$ variable
            params.set("level", lvl);

            // Execute Per-Level Reward (if defined)
            if (skill.getPerLevelReward() != null) {
                skill.getPerLevelReward().execute(context);
            }

            // Execute Milestone Reward (if defined for this specific level)
            if (skill.getMilestoneRewards() != null && skill.getMilestoneRewards().containsKey(lvl)) {
                skill.getMilestoneRewards().get(lvl).execute(context);
            }
        }
        rewardedLevel.put(key, currentLevel);
    }

    public void addXp(Skill skill, double amount, Player player) {
        addXp(skill.name(), amount, player);
    }
}
