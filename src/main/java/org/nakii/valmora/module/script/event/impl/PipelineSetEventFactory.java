package org.nakii.valmora.module.script.event.impl;

import org.nakii.valmora.api.pipeline.flow.FlowDomain;
import org.nakii.valmora.api.pipeline.flow.FlowState;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.api.scripting.Expression;
import org.nakii.valmora.infrastructure.config.diag.Diagnostics;
import org.nakii.valmora.infrastructure.config.diag.Suggestions;
import org.nakii.valmora.module.script.ScriptModule;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;
import org.nakii.valmora.module.script.pipeline.FlowYamlLoader;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Writes a value into the running pipeline flow's state — e.g. a script step between
 * {@code calculate_damage} and {@code apply_damage} doubling the hit:
 * {@code pipeline_set dmg.final_damage "$dmg.final_damage$ * 2"}.
 *
 * <p>Which keys are writable is declared per flow ({@code FlowSpec#writable}). Inside a pipeline
 * file an unknown key is reported at load time; anywhere else (or a key that isn't writable in the
 * flow actually running) it's reported once at run time and ignored.
 *
 * <p>DSL: {@code pipeline_set <key> <value>} — everything after the key is one expression.
 */
public class PipelineSetEventFactory implements EventFactory {

    private final ScriptModule scriptModule;

    public PipelineSetEventFactory(ScriptModule scriptModule) {
        this.scriptModule = scriptModule;
    }

    @Override
    public String getName() {
        return "pipeline_set";
    }

    @Override
    public int minArgs() {
        return 2;
    }

    @Override
    public String usage() {
        return "pipeline_set <key> <value>";
    }

    @Override
    public CompiledEvent compile(String[] args, EventOptions options) {
        if (args.length < 2) return context -> {};
        String key = args[0].toLowerCase(Locale.ROOT);
        String rawValue = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        FlowDomain domain = FlowYamlLoader.compilingDomain();
        if (domain != null) {
            Set<String> writable = domain.writableKeys();
            if (!writable.contains(key)) {
                Diagnostics.error("pipeline_set: '" + key + "' can't be written in " + domain.fileName()
                        + (writable.isEmpty() ? " (nothing is writable there)" : " (writable: " + writable + ")"),
                        Suggestions.hint(key, writable));
                return context -> {};
            }
        }

        Expression value = scriptModule.getExpressionParser().parse(rawValue);
        return context -> {
            FlowState state = context.get(FlowState.STATE_KEY);
            if (state == null) {
                Diagnostics.warn("pipeline_set " + key + " used outside a pipeline flow — ignored");
                return;
            }
            if (state.write(key, value.evaluate(context))) {
                state.publish(context);
            } else {
                Diagnostics.warn("pipeline_set: '" + key + "' isn't writable in this flow — ignored");
            }
        };
    }
}
