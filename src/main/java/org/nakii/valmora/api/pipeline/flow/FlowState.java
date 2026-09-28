package org.nakii.valmora.api.pipeline.flow;

import org.nakii.valmora.api.execution.ExecutionContext;

import java.util.List;

/**
 * The mutable per-run state of one flow (a hit, a catch, a block break, ...): the values built-in
 * steps pass to each other, replacing what used to be local variables in one long Java method.
 *
 * <p>Script-visible values ({@code $fishing.item$}, {@code $dmg.final_damage$}, ...) are exposed
 * <b>lazily</b>: {@link #publish} copies them onto the context as attachments, and is only called
 * (via {@link #expose}) right before something script-side reads them — a step's
 * {@code conditions:}, a script step, an anchor with registered stages, an expression param. A
 * default flow with no script content therefore never touches the attachment store.
 */
public interface FlowState {

    /** Context attachment holding the running flow's state — read by {@code pipeline_set}. */
    String STATE_KEY = "flow:state";

    /** Copies this state's script-visible values onto {@code ctx} (e.g. {@code "fishing:item"}). */
    void publish(ExecutionContext ctx);

    /**
     * Applies a {@code pipeline_set <key> <value>} write (key as a script would name it, e.g.
     * {@code "fishing.amount"}).
     *
     * @return false if {@code key} isn't writable in this state (the caller reports it)
     */
    default boolean write(String key, Object value) {
        return false;
    }

    /** Makes {@code state} readable by scripts on {@code ctx} — see the class doc. */
    static void expose(ExecutionContext ctx, FlowState state) {
        if (state == null) return;
        ctx.set(STATE_KEY, state);
        state.publish(ctx);
    }

    /** Helper for {@link #write} implementations: a script value as a number. */
    static double asDouble(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Helper for {@link #write} implementations: a script value as a boolean. */
    static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        return value != null && Boolean.parseBoolean(value.toString());
    }

    /** Helper for {@link #write} implementations: a script value as a string ({@code null}/"null" → null). */
    static String asString(Object value) {
        if (value == null) return null;
        String s = value.toString();
        return s.equalsIgnoreCase("null") || s.isEmpty() ? null : s;
    }

    /** Convenience for states with nothing writable. */
    static List<String> none() {
        return List.of();
    }
}
