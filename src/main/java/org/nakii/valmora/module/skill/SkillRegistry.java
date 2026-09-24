package org.nakii.valmora.module.skill;

import org.nakii.valmora.api.registry.SimpleRegistry;
import java.util.Optional;

public class SkillRegistry extends SimpleRegistry<SkillDefinition> {

    // Phase 3.2 (docs/REFACTOR/PROGRESS.md): XP curves are now data-driven, loaded from
    // skills/xp_curves.yml via XpCurveRegistry.load() (called from SkillModule.onEnable()).
    // The "default" curve here always matches the exact original hardcoded threshold table.
    private final XpCurveRegistry xpCurveRegistry = new XpCurveRegistry();

    public SkillRegistry() {
        super(org.nakii.valmora.infrastructure.versioning.IdAliases.SKILLS);
    }

    public void registerSkill(SkillDefinition definition) {
        register(definition.getId(), definition);
    }

    public Optional<SkillDefinition> getSkill(String id) {
        return get(id);
    }

    public XpCurveRegistry getXpCurveRegistry() {
        return xpCurveRegistry;
    }

    public int getLevelFromXp(String curveId, double xp) {
        return xpCurveRegistry.get(curveId).getLevelFromXp(xp);
    }

    public double getXpForLevel(String curveId, int level) {
        return xpCurveRegistry.get(curveId).getXpForLevel(level);
    }

    public record ProgressData(int currentLevel, int nextLevel, int xpInLevel, int xpRequired, int percent) {}

    public ProgressData getProgressData(String curveId, double totalXp) {
        XpCurve curve = xpCurveRegistry.get(curveId);
        int level = curve.getLevelFromXp(totalXp);
        int maxLvl = curve.getMaxLevel();

        double currentLvlXp = curve.getXpForLevel(level);
        double nextLvlXp = curve.getXpForLevel(level + 1);

        int xpInLevel = (int) (totalXp - currentLvlXp);
        int xpRequired = (int) (nextLvlXp - currentLvlXp);
        int percent = xpRequired > 0 ? (int) ((double) xpInLevel / xpRequired * 100) : 100;

        return new ProgressData(level, Math.min(level + 1, maxLvl), xpInLevel, xpRequired, percent);
    }
}
