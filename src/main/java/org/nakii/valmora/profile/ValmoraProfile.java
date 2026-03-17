package org.nakii.valmora.profile;

import org.nakii.valmora.skill.SkillManager;
import org.nakii.valmora.stat.StatManager;

import java.util.UUID;

public class ValmoraProfile {
    private final UUID id;
    private final String name;
    private final StatManager statManager = new StatManager();
    private final SkillManager skillManager = new SkillManager();

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

    
}
