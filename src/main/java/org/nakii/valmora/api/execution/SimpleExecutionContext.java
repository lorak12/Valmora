package org.nakii.valmora.api.execution;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.TagService;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.script.tag.TagServiceImpl;

import java.util.Optional;

public class SimpleExecutionContext implements ExecutionContext {

    private final LivingEntity caster;
    private final LivingEntity target;
    private final Location location;
    private final ConfigurationSection params;

    public SimpleExecutionContext(LivingEntity caster, LivingEntity target, Location location, ConfigurationSection params) {
        this.caster = caster;
        this.target = target;
        this.location = location;
        this.params = params;
    }

    public SimpleExecutionContext(LivingEntity caster, Location location, ConfigurationSection params) {
        this(caster, null, location, params);
    }

    @Override
    public LivingEntity getCaster() {
        return caster;
    }

    @Override
    public Optional<LivingEntity> getTarget() {
        return Optional.ofNullable(target);
    }

    @Override
    public Location getLocation() {
        return location;
    }

    @Override
    public ConfigurationSection getParams() {
        return params;
    }

    @Override
    public VariableResolver getVariableResolver() {
        return ValmoraAPI.getInstance().getScriptModule().getVariableResolver();
    }

    @Override
    public TagService getTagService() {
        return new TagServiceImpl(this);
    }
}
