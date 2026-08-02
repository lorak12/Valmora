package org.nakii.valmora.module.progression;

import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.scripting.CompiledEvent;
import org.nakii.valmora.module.script.event.EventFactory;
import org.nakii.valmora.module.script.event.EventOptions;

import java.util.List;

/**
 * DSL convenience wrappers around {@link ProgressionManager}, usable from GUI click actions
 * and NPC/quest reward scripts:
 * <pre>
 *   progression_levelup &lt;treeId&gt; &lt;nodeId&gt;
 *   progression_unlock_tier &lt;treeId&gt;
 *   progression_reset &lt;treeId&gt;
 * </pre>
 */
public class ProgressionEventFactory {

    public List<EventFactory> all() {
        return List.of(new LevelUp(), new UnlockTier(), new Reset());
    }

    private static class LevelUp implements EventFactory {
        @Override public String getName() { return "progression_levelup"; }

        @Override
        public CompiledEvent compile(String[] args, EventOptions options) {
            if (args.length < 2) return ctx -> {};
            String treeId = args[0];
            String nodeId = args[1];
            return ctx -> ctx.getPlayerCaster().ifPresent(player -> {
                ProgressionManager pm = ValmoraAPI.getInstance().getProgressionManager();
                if (pm != null) pm.levelUp(player, treeId, nodeId);
            });
        }
    }

    private static class UnlockTier implements EventFactory {
        @Override public String getName() { return "progression_unlock_tier"; }

        @Override
        public CompiledEvent compile(String[] args, EventOptions options) {
            if (args.length < 1) return ctx -> {};
            String treeId = args[0];
            return ctx -> ctx.getPlayerCaster().ifPresent(player -> {
                ProgressionManager pm = ValmoraAPI.getInstance().getProgressionManager();
                if (pm != null) pm.unlockTier(player, treeId);
            });
        }
    }

    private static class Reset implements EventFactory {
        @Override public String getName() { return "progression_reset"; }

        @Override
        public CompiledEvent compile(String[] args, EventOptions options) {
            if (args.length < 1) return ctx -> {};
            String treeId = args[0];
            return ctx -> ctx.getPlayerCaster().ifPresent(player -> {
                ProgressionManager pm = ValmoraAPI.getInstance().getProgressionManager();
                if (pm != null) pm.resetTree(player, treeId);
            });
        }
    }
}
