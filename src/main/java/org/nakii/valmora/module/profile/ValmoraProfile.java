package org.nakii.valmora.module.profile;

import org.nakii.valmora.module.item.CooldownManager;
import org.nakii.valmora.module.skill.SkillManager;
import org.nakii.valmora.module.stat.StatManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ValmoraProfile {
    private final UUID id;
    private final String name;
    private final StatManager statManager = new StatManager();
    private final SkillManager skillManager = new SkillManager();
    private final PlayerState playerState = new PlayerState();
    private final CooldownManager cooldownManager = new CooldownManager();
    private final Set<String> tags = new HashSet<>();
    private final Map<String, Object> variables = new HashMap<>();

    public ValmoraProfile(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public ValmoraProfile(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }

    public StatManager getStatManager() {
        return statManager;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public PlayerState getPlayerState() {
        return playerState;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }
}
