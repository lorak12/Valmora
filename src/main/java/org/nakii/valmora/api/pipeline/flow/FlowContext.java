package org.nakii.valmora.api.pipeline.flow;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

import java.util.Optional;

/**
 * An execution context whose target a step can set mid-flow — for flows where a step resolves the
 * target (e.g. an ability's {@code resolve_target}) and later steps, conditions and anchor stages
 * must see it as {@code $target.*$} / {@link #getTarget()}.
 */
public final class FlowContext extends SimpleExecutionContext {

    private LivingEntity target;

    /** Params are an empty section (never null), as ability contexts always had — {@code $param.*$} reads them. */
    public FlowContext(LivingEntity caster, LivingEntity target, Location location) {
        super(caster, null, location, new org.bukkit.configuration.MemoryConfiguration());
        this.target = target;
    }

    @Override
    public Optional<LivingEntity> getTarget() {
        return Optional.ofNullable(target);
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }
}
