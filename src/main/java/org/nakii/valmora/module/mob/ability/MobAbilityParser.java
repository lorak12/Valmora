package org.nakii.valmora.module.mob.ability;

import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.module.item.ConfiguredMechanic;
import org.nakii.valmora.module.item.MechanicParser;
import org.nakii.valmora.module.item.MechanicRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the {@code abilities:} section of a mob definition into {@link MobAbility} instances.
 * Mechanic parsing is delegated to the shared {@link MechanicParser} so boss abilities use the
 * exact same mechanics as item abilities.
 */
public final class MobAbilityParser {

    private MobAbilityParser() {}

    /** Thrown when an abilities section is invalid; carries a human-readable reason. */
    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    public static List<MobAbility> parse(ConfigurationSection abilitiesSection, MechanicRegistry registry)
            throws ParseException {
        List<MobAbility> abilities = new ArrayList<>();

        for (String key : abilitiesSection.getKeys(false)) {
            ConfigurationSection abSec = abilitiesSection.getConfigurationSection(key);
            if (abSec == null) continue;

            MobAbility.Builder builder = new MobAbility.Builder(key);

            if (abSec.contains("name")) builder.name(abSec.getString("name"));

            MobAbilityTrigger trigger;
            String triggerStr = abSec.getString("trigger", "ON_TIMER");
            try {
                trigger = MobAbilityTrigger.valueOf(triggerStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ParseException("Invalid trigger '" + triggerStr + "' in ability '" + key + "'.");
            }
            builder.trigger(trigger);

            builder.intervalTicks(abSec.getInt("interval", 100));
            builder.chance(abSec.getDouble("chance", 1.0));
            builder.healthPercent(abSec.getDouble("health-percent", 50.0));
            builder.targetRange(abSec.getDouble("target-range", 0.0));
            builder.cooldownSeconds(abSec.getDouble("cooldown", 0.0));
            if (abSec.contains("announce")) builder.announce(abSec.getString("announce"));

            if (abSec.contains("mechanics")) {
                try {
                    for (ConfiguredMechanic mechanic : MechanicParser.parse(abSec.getMapList("mechanics"), registry)) {
                        builder.addMechanic(mechanic);
                    }
                } catch (MechanicParser.UnknownMechanicException e) {
                    throw new ParseException("Unknown mechanic type '" + e.getMessage() + "' in ability '" + key + "'.");
                }
            }

            abilities.add(builder.build());
        }

        return abilities;
    }
}
