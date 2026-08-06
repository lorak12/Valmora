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
    private ExecutionContext parent;

    public SimpleExecutionContext(LivingEntity caster, LivingEntity target, Location location, ConfigurationSection params) {
        this.caster = caster;
        this.target = target;
        this.location = location;
        this.params = params;
    }

    public SimpleExecutionContext(LivingEntity caster, Location location, ConfigurationSection params) {
        this(caster, null, location, params);
    }

    /**
     * Builds a child context that inherits key-value attachments (see
     * {@link ExecutionContext#get(String)}) from {@code parent} on a local miss. Useful for nested
     * mechanic invocations that need to pass runtime data down without polluting the parent scope.
     */
    public SimpleExecutionContext(LivingEntity caster, LivingEntity target, Location location,
                                   ConfigurationSection params, ExecutionContext parent) {
        this(caster, target, location, params);
        this.parent = parent;
    }

    @Override
    public ExecutionContext getParent() {
        return parent;
    }

    @Override
    public void setParent(ExecutionContext parent) {
        this.parent = parent;
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
