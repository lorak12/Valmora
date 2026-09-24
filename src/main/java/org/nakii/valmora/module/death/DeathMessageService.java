package org.nakii.valmora.module.death;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.module.combat.DeathContextCache;
import org.nakii.valmora.util.Formatter;

/**
 * Builds custom death messages purely from Valmora's own {@code DamageType}/attacker/weapon
 * resolution (VANILLA_CONTROL_AUDIT.md §9/§14) — deliberately NOT from Paper's
 * {@code DamageSource}/{@code DamageType} builder API, which is unused anywhere else in this
 * codebase and would fight the virtual-health architecture (see
 * docs/VANILLA_CONTROL_AUDIT_PROGRESS.md and this decision recorded in docs/modules/design/death.md).
 */
public final class DeathMessageService {

    private DeathMessageService() {}

    /** Consumes the cached death context for {@code victim} (see {@link DeathContextCache}) and renders the message. */
    public static Component build(Player victim) {
        DeathContextCache.Context ctx = DeathContextCache.consume(victim.getUniqueId());
        Valmora plugin = Valmora.getInstance();

        String typeId = ctx != null ? ctx.type().getId() : "default";
        String defaultTemplate = plugin.getConfig().getString("death.messages.default", "<gray>%victim% died.");
        String template = plugin.getConfig().getString("death.messages." + typeId, defaultTemplate);

        String rendered = template
                .replace("%victim%", victim.getName())
                .replace("%attacker%", attackerName(ctx))
                .replace("%weapon%", weaponName(ctx))
                .replace("%cause%", typeId);

        return Formatter.format(rendered);
    }

    private static String attackerName(DeathContextCache.Context ctx) {
        if (ctx == null || ctx.attacker() == null) return "";
        LivingEntity attacker = ctx.attacker();
        return attacker instanceof Player p ? p.getName() : Formatter.capitalize(attacker.getType().name().replace('_', ' ').toLowerCase());
    }

    private static String weaponName(DeathContextCache.Context ctx) {
        if (ctx == null || ctx.weapon() == null) return "";
        ItemStack weapon = ctx.weapon();
        if (weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(weapon.getItemMeta().displayName());
        }
        return Formatter.capitalize(weapon.getType().name().replace('_', ' ').toLowerCase());
    }

    public static boolean broadcastEnabled() {
        return Valmora.getInstance().getConfig().getBoolean("death.broadcast-enabled", true);
    }
}
