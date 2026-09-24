package org.nakii.valmora.module.script.event;

import org.nakii.valmora.infrastructure.config.diag.ConfigSource;

/**
 * Common execution options for events (delay, notifyPlayer, etc.).
 *
 * @param rawArgs the argument text exactly as written (quotes kept, options removed) — for events
 *                that embed another event ({@code foreach}, {@code run_script}) or free text
 * @param source  where the script was defined, when it was compiled while loading content
 */
public record EventOptions(int delay, boolean notifyPlayer, String rawArgs, ConfigSource source) {
    public static final EventOptions DEFAULT = new EventOptions(0, false);

    public EventOptions(int delay, boolean notifyPlayer) {
        this(delay, notifyPlayer, null, ConfigSource.UNKNOWN);
    }

    /**
     * The argument text after the first {@code skip} arguments, exactly as written (quotes kept) —
     * what an event embedding another event ({@code foreach @all give "x y"}) should re-parse.
     * Falls back to joining {@code args} when the raw text isn't known.
     */
    public String rawArgsAfter(int skip, String[] args) {
        if (rawArgs == null) {
            return skip >= args.length ? "" : String.join(" ", java.util.Arrays.copyOfRange(args, skip, args.length));
        }
        var tokens = EventParser.tokenize(rawArgs);
        StringBuilder sb = new StringBuilder();
        for (int i = skip; i < tokens.size(); i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(tokens.get(i).raw());
        }
        return sb.toString();
    }
}
