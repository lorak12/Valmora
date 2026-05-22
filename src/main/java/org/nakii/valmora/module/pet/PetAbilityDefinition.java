package org.nakii.valmora.module.pet;

import org.nakii.valmora.api.scripting.CompiledEvent;

public class PetAbilityDefinition {

    private final PetAbilityTrigger trigger;
    private final CompiledEvent events;

    public PetAbilityDefinition(PetAbilityTrigger trigger, CompiledEvent events) {
        this.trigger = trigger;
        this.events = events;
    }

    public PetAbilityTrigger getTrigger() { return trigger; }
    public CompiledEvent getEvents() { return events; }
}
