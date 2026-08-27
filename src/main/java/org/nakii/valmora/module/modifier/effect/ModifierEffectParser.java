package org.nakii.valmora.module.modifier.effect;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.item.AbilityDefinition;
import org.nakii.valmora.module.item.AbilityTrigger;
import org.nakii.valmora.module.item.ConfiguredMechanic;
import org.nakii.valmora.module.item.MechanicParser;
import org.nakii.valmora.module.item.MechanicRegistry;
import org.nakii.valmora.module.modifier.value.ValueParser;
import org.nakii.valmora.module.script.condition.ConditionGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses a modifier definition/tier's {@code effects:} list (docs/
 * Valmora_Modifier_Framework_Design.docx §7/§8) into {@link ModifierEffect}s.
 */
public final class ModifierEffectParser {

    private ModifierEffectParser() {}

    public static class EffectParseException extends Exception {
        public EffectParseException(String message) { super(message); }
    }

    public static List<ModifierEffect> parse(List<Map<?, ?>> effectMaps, MechanicRegistry mechanicRegistry)
            throws EffectParseException {
        List<ModifierEffect> result = new ArrayList<>();
        if (effectMaps == null) return result;

        for (Map<?, ?> rawMap : effectMaps) {
            MemoryConfiguration section = new MemoryConfiguration();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                section.set(String.valueOf(entry.getKey()), entry.getValue());
            }

            String type = section.getString("type");
            if (type == null) continue;
            type = type.toUpperCase(Locale.ROOT);

            ConditionGroup conditions = section.contains("conditions")
                    ? ValmoraAPI.getInstance().getScriptModule().getConditionParser().parseList(section.getStringList("conditions"))
                    : new ConditionGroup(Collections.emptyList());

            switch (type) {
                case "STAT" -> result.add(parseStat(section, conditions));
                case "ABILITY" -> result.add(parseAbility(section, conditions, mechanicRegistry));
                case "EVENT", "TRIGGER" -> result.add(parseEvent(section, conditions));
                case "STATE" -> result.add(parseState(section, conditions));
                default -> {
                    var factory = ModifierEffectRegistry.get(type);
                    if (factory == null) {
                        throw new EffectParseException("Unknown modifier effect type '" + type + "'");
                    }
                    try {
                        result.add(factory.parse(section, conditions));
                    } catch (Exception e) {
                        throw new EffectParseException("Custom effect type '" + type + "' failed to parse: " + e.getMessage());
                    }
                }
            }
        }
        return result;
    }

    private static StatEffect parseStat(ConfigurationSection section, ConditionGroup conditions) throws EffectParseException {
        String stat = section.getString("stat");
        if (stat == null) throw new EffectParseException("STAT effect missing 'stat'");
        StatEffect.Operation operation;
        try {
            operation = StatEffect.Operation.valueOf(section.getString("operation", "ADD").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new EffectParseException("STAT effect has invalid operation '" + section.getString("operation") + "'");
        }
        return new StatEffect(stat.toLowerCase(Locale.ROOT), operation, ValueParser.parse(section.get("value")), conditions);
    }

    private static AbilityEffect parseAbility(ConfigurationSection section, ConditionGroup conditions, MechanicRegistry mechanicRegistry)
            throws EffectParseException {
        ConfigurationSection defSec = section.getConfigurationSection("definition");
        if (defSec == null) {
            throw new EffectParseException("ABILITY effect requires an inline 'definition:' — referencing an ability by id is not yet supported");
        }

        String id = defSec.getString("id", "modifier_ability");
        AbilityDefinition.Builder builder = new AbilityDefinition.Builder(id);
        if (defSec.contains("name")) builder.name(defSec.getString("name"));

        AbilityTrigger trigger = AbilityTrigger.PASSIVE;
        if (defSec.contains("trigger")) {
            try {
                trigger = AbilityTrigger.valueOf(defSec.getString("trigger").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new EffectParseException("ABILITY effect definition '" + id + "' has invalid trigger '" + defSec.getString("trigger") + "'");
            }
        }
        builder.trigger(trigger);
        builder.targetRange(defSec.getDouble("target-range", 0.0));
        builder.cooldown(defSec.getDouble("cooldown", 0.0));
        builder.manaCost(defSec.getDouble("mana-cost", 0.0));
        if (defSec.contains("description")) builder.description(defSec.getStringList("description"));

        if (defSec.contains("conditions")) {
            builder.conditions(ValmoraAPI.getInstance().getScriptModule().getConditionParser().parseList(defSec.getStringList("conditions")));
        }

        if (defSec.contains("mechanics")) {
            try {
                for (ConfiguredMechanic mechanic : MechanicParser.parse(defSec.getMapList("mechanics"), mechanicRegistry)) {
                    builder.addMechanic(mechanic);
                }
            } catch (MechanicParser.UnknownMechanicException e) {
                throw new EffectParseException("ABILITY effect definition '" + id + "' references unknown mechanic type '" + e.getMessage() + "'");
            }
        }

        return new AbilityEffect(builder.build(), conditions);
    }

    private static EventEffect parseEvent(ConfigurationSection section, ConditionGroup conditions) throws EffectParseException {
        String triggerRaw = section.contains("trigger") ? section.getString("trigger") : section.getString("event");
        if (triggerRaw == null) throw new EffectParseException("EVENT effect missing 'trigger'");
        AbilityTrigger trigger;
        try {
            trigger = AbilityTrigger.valueOf(triggerRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new EffectParseException("EVENT effect has invalid trigger '" + triggerRaw + "'");
        }

        List<CompiledEvent> actions = new ArrayList<>();
        var parser = ValmoraAPI.getInstance().getScriptModule().getEventParser();
        for (String raw : section.getStringList("actions")) {
            actions.add(parser.parse(raw));
        }
        return new EventEffect(trigger, actions, conditions);
    }

    private static StateEffect parseState(ConfigurationSection section, ConditionGroup conditions) throws EffectParseException {
        String key = section.getString("key");
        if (key == null) throw new EffectParseException("STATE effect missing 'key'");
        StateEffect.Operation operation;
        try {
            operation = StateEffect.Operation.valueOf(section.getString("operation", "ADD").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new EffectParseException("STATE effect has invalid operation '" + section.getString("operation") + "'");
        }
        return new StateEffect(operation, key, ValueParser.parse(section.get("value")), conditions);
    }
}
