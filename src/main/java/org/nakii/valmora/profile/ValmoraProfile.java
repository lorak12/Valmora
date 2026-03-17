package org.nakii.valmora.profile;

import org.nakii.valmora.skill.PlayerSkillManager;
import org.nakii.valmora.stat.StatManager;

import java.util.UUID;

public class ValmoraProfile {
    private final UUID id;
    private final String name;
    private final StatManager statManager = new StatManager();
    private final PlayerSkillManager skillManager;

    public ValmoraProfile(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.skillManager = new PlayerSkillManager(this.id);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }

    public StatManager getStatManager() {
        return statManager;
    }

    public PlayerSkillManager getSkillManager() {
        return skillManager;
    }
}
