package org.nakii.valmora.module.script.event.impl;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.item.TargetResolver;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.script.event.EventArgs;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.List;

/**
 * Generic entity-heal event, added alongside {@code damage}/{@code strike_lightning} for the
 * enchant overhaul's trigger actions (e.g. {@code life_steal}'s {@code ON_ATTACK_POST}), but kept
 * domain-agnostic (registered by {@link org.nakii.valmora.module.script.ScriptModule} itself, not
 * enchant-private) since it's just as usable from mob/item abilities or quest scripts.
 *
 * <p>DSL: {@code heal <selector> <amount>} — {@code selector} uses {@link TargetResolver}'s
 * {@code @self}/{@code @target}/etc. vocabulary; {@code amount} may be a literal number or a
 * {@code $}-containing formula (evaluated the same way {@code VariableEvent} evaluates its value
 * argument). A {@link Player} target heals through their Valmora profile (matching {@code
 * HealMechanic}'s ability-mechanic equivalent); any other {@link LivingEntity} heals via vanilla
 * {@code setHealth}, clamped to max health.
 */
public class HealEventFactory implements EventFactory {

    @Override
    public String getName() {
        return "heal";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2) return context -> {};
        String selector = args[0];
        String rawAmount = args[1];

        return context -> {
            double amount = EventArgs.resolveDouble(rawAmount, context);
            if (amount <= 0) return;

            for (LivingEntity target : TargetResolver.resolve(selector, context)) {
                if (target instanceof Player player) {
                    ValmoraPlayer vp = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
                    ValmoraProfile profile = vp != null ? vp.getActiveProfile() : null;
                    if (profile == null) continue;
                    profile.getPlayerState().heal(amount, profile.getStatManager());
                    ValmoraAPI.getInstance().getPlayerManager()
                            .syncVisualHealth(player, profile.getPlayerState(), profile.getStatManager());
                } else {
                    var attr = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    double max = attr != null ? attr.getValue() : target.getHealth();
                    target.setHealth(Math.min(max, target.getHealth() + amount));
                }
            }
        };
    }
}
