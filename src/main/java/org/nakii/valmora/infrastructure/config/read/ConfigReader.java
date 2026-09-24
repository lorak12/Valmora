package org.nakii.valmora.infrastructure.config.read;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.api.config.LoadResult;
import org.nakii.valmora.infrastructure.config.diag.ConfigSource;
import org.nakii.valmora.infrastructure.config.diag.DiagnosticSink;
import org.nakii.valmora.infrastructure.config.diag.Diagnostics;
import org.nakii.valmora.infrastructure.config.diag.LoadScope;
import org.nakii.valmora.infrastructure.config.diag.Severity;
import org.nakii.valmora.infrastructure.config.diag.Suggestions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Typed, self-reporting access to one YAML entry. Every accessor reports what went wrong to the
 * current {@link LoadScope} (so it lands in the reload report with the file, entry and key path)
 * instead of each parser hand-writing {@code try { Enum.valueOf } catch}:
 * <ul>
 *   <li>{@code require*} — a missing/invalid value is an ERROR; the entry should fail
 *       ({@link #result(Supplier)} does that for you).</li>
 *   <li>everything else — a bad optional value is a WARN and the default is used.</li>
 * </ul>
 * <pre>{@code
 * ConfigReader r = ConfigReader.of(section).knownKeys("material", "amount", "display-name");
 * Material material = r.requireMaterial("material");
 * int amount = r.intRange("amount", 1, 1, 64);
 * return r.result(() -> new Thing(id, material, amount));
 * }</pre>
 */
public final class ConfigReader {

    /** Keys any entry may carry, handled generically by the loaders. */
    private static final Set<String> ALWAYS_ALLOWED = Set.of("previous-ids");

    private final ConfigurationSection section;
    private final LoadScope scope;

    private ConfigReader(ConfigurationSection section, LoadScope scope) {
        this.section = section;
        this.scope = scope;
    }

    /** Reader over {@code section} reporting into the current {@link LoadScope} (or the console, if none). */
    public static ConfigReader of(ConfigurationSection section) {
        return new ConfigReader(section, LoadScope.current().orElse(null));
    }

    /** Reader reporting into an explicit scope (e.g. a {@link LoadScope#sub} location). */
    public static ConfigReader of(ConfigurationSection section, LoadScope scope) {
        return new ConfigReader(section, scope);
    }

    public ConfigurationSection section() {
        return section;
    }

    public LoadScope scope() {
        return scope;
    }

    public boolean has(String key) {
        return section != null && section.contains(key);
    }

    // ------------------------------------------------------------------ reporting

    public void error(String key, String message) {
        report(Severity.ERROR, key, message, null);
    }

    public void error(String key, String message, String hint) {
        report(Severity.ERROR, key, message, hint);
    }

    public void warn(String key, String message) {
        report(Severity.WARN, key, message, null);
    }

    public void warn(String key, String message, String hint) {
        report(Severity.WARN, key, message, hint);
    }

    private void report(Severity severity, String key, String message, String hint) {
        if (scope != null) {
            (key == null ? scope : scope.sub(key)).report(severity, message, hint);
        } else {
            Diagnostics.report(severity, (key == null ? "" : key + ": ") + message, hint);
        }
    }

    /** Whether any ERROR was reported for this entry (by this reader or anything else in its scope). */
    public boolean hasErrors() {
        return scope != null && scope.errorCount() > 0;
    }

    /**
     * {@code success(build.get())} unless an ERROR was reported for this entry, in which case a
     * failure (whose message summarises the count — the details are already in the report).
     */
    public <T> LoadResult<T, String> result(Supplier<T> build) {
        if (hasErrors()) {
            int n = scope.errorCount();
            return LoadResult.failure("not loaded — " + n + (n == 1 ? " error" : " errors") + " above");
        }
        return LoadResult.success(build.get());
    }

    /** Records a reference to other content, checked once everything has loaded. */
    public void ref(String kind, String id) {
        if (scope != null) scope.ref(kind, id);
    }

    /** {@link #ref} at a key's location (e.g. {@code drops[2].item}). */
    public void ref(String key, String kind, String id) {
        if (scope != null) (key == null ? scope : scope.sub(key)).ref(kind, id);
    }

    public ConfigSource source() {
        return scope == null ? ConfigSource.UNKNOWN : scope.source();
    }

    // ------------------------------------------------------------------ unknown keys

    /**
     * Warns about every key of this section that isn't one of {@code allowed} (with a "did you
     * mean" suggestion) — catching typos like {@code materail:} that would otherwise be silently
     * ignored. Controlled by {@code diagnostics.unknown-keys}.
     */
    public ConfigReader knownKeys(String... allowed) {
        return knownKeys(Arrays.asList(allowed));
    }

    public ConfigReader knownKeys(Collection<String> allowed) {
        if (section == null || !unknownKeyWarningsEnabled()) return this;
        Set<String> lower = new HashSet<>();
        for (String a : allowed) lower.add(a.toLowerCase(Locale.ROOT));
        lower.addAll(ALWAYS_ALLOWED);
        for (String key : section.getKeys(false)) {
            if (lower.contains(key.toLowerCase(Locale.ROOT))) continue;
            warn(key, "unknown key '" + key + "' — ignored", Suggestions.hint(key, allowed));
        }
        return this;
    }

    static boolean unknownKeyWarningsEnabled() {
        try {
            var plugin = org.nakii.valmora.Valmora.getInstance();
            if (plugin != null && plugin.getConfig() != null) {
                return !"off".equalsIgnoreCase(plugin.getConfig().getString("diagnostics.unknown-keys", "warn"));
            }
        } catch (RuntimeException ignored) {
            // no server (tests)
        }
        return true;
    }

    // ------------------------------------------------------------------ strings

    public String string(String key, String def) {
        if (section == null) return def;
        Object raw = section.get(key);
        if (raw == null) return def;
        if (raw instanceof ConfigurationSection || raw instanceof List<?>) {
            warn(key, "expected a single value, got " + describe(raw) + " — using the default");
            return def;
        }
        return String.valueOf(raw);
    }

    /** Required non-blank value; ERROR (and null) when missing. */
    public String requireString(String key) {
        String value = string(key, null);
        if (value == null || value.isBlank()) {
            error(key, "missing required '" + key + "'");
            return null;
        }
        return value;
    }

    /** A value that may be written as a single string or a list of strings. */
    public List<String> stringList(String key) {
        if (section == null || !section.contains(key)) return List.of();
        Object raw = section.get(key);
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o == null) continue;
                if (o instanceof Map<?, ?>) {
                    warn(key, "expected a list of text lines, found a map entry — skipped");
                    continue;
                }
                out.add(String.valueOf(o));
            }
            return out;
        }
        if (raw instanceof ConfigurationSection) {
            warn(key, "expected a list, got a section — ignored");
            return List.of();
        }
        return raw == null ? List.of() : List.of(String.valueOf(raw));
    }

    public boolean bool(String key, boolean def) {
        if (section == null || !section.contains(key)) return def;
        Object raw = section.get(key);
        if (raw instanceof Boolean b) return b;
        String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "true", "yes", "on" -> { return true; }
            case "false", "no", "off" -> { return false; }
            default -> {
                warn(key, "expected true/false, got '" + raw + "' — using " + def);
                return def;
            }
        }
    }

    // ------------------------------------------------------------------ numbers

    public int intValue(String key, int def) {
        return intRange(key, def, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /** Integer in {@code [min, max]}; unparsable → WARN + default, out of range → WARN + clamped. */
    public int intRange(String key, int def, int min, int max) {
        Double d = number(key);
        if (d == null) return def;
        if (d != Math.rint(d)) {
            warn(key, "expected a whole number, got " + format(d) + " — rounded");
        }
        long v = Math.round(d);
        if (v < min || v > max) {
            long clamped = Math.max(min, Math.min(max, v));
            warn(key, "value " + v + " is outside " + min + ".." + max + " — using " + clamped);
            return (int) clamped;
        }
        return (int) v;
    }

    public double doubleValue(String key, double def) {
        return doubleRange(key, def, -Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public double doubleRange(String key, double def, double min, double max) {
        Double d = number(key);
        if (d == null) return def;
        if (d < min || d > max) {
            double clamped = Math.max(min, Math.min(max, d));
            warn(key, "value " + format(d) + " is outside " + format(min) + ".." + format(max) + " — using " + format(clamped));
            return clamped;
        }
        return d;
    }

    /** The number at {@code key}, or null (with a WARN if something unparsable was there). */
    private Double number(String key) {
        if (section == null || !section.contains(key)) return null;
        Object raw = section.get(key);
        if (raw instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            warn(key, "expected a number, got '" + raw + "' — using the default");
            return null;
        }
    }

    // ------------------------------------------------------------------ enums & materials

    /** Case- and separator-insensitive ({@code fire-aspect} = {@code FIRE_ASPECT}) enum lookup; WARN + default when invalid. */
    public <E extends Enum<E>> E enumOf(String key, Class<E> type, E def) {
        String raw = string(key, null);
        if (raw == null) return def;
        E value = parseEnum(raw, type);
        if (value == null) {
            warn(key, "unknown " + humanName(type) + " '" + raw + "' — using " + (def == null ? "none" : def.name()),
                    Suggestions.hint(raw, enumNames(type)));
            return def;
        }
        return value;
    }

    /** Like {@link #enumOf} but missing/invalid is an ERROR. */
    public <E extends Enum<E>> E requireEnum(String key, Class<E> type) {
        String raw = string(key, null);
        if (raw == null || raw.isBlank()) {
            error(key, "missing required '" + key + "' (one of " + preview(enumNames(type)) + ")");
            return null;
        }
        E value = parseEnum(raw, type);
        if (value == null) {
            error(key, "unknown " + humanName(type) + " '" + raw + "'", Suggestions.hint(raw, enumNames(type)));
        }
        return value;
    }

    /**
     * Lookup in a registry-backed "open enum" (mob categories, damage types, ...): WARN + default
     * when the value is unknown.
     * @param what human name for messages, e.g. {@code "damage type"}
     * @param find id → value (case-insensitive)
     * @param names every known id, for suggestions
     */
    public <T> T oneOf(String key, String what, Function<String, Optional<T>> find,
                       Supplier<? extends Collection<String>> names, T def) {
        String raw = string(key, null);
        if (raw == null) return def;
        Optional<T> value = find.apply(raw.trim());
        if (value.isEmpty()) {
            warn(key, "unknown " + what + " '" + raw + "' — using " + (def == null ? "none" : "the default"),
                    Suggestions.hint(raw, names.get()));
            return def;
        }
        return value.get();
    }

    /** Like {@link #oneOf} but missing/unknown is an ERROR. */
    public <T> T requireOneOf(String key, String what, Function<String, Optional<T>> find,
                              Supplier<? extends Collection<String>> names) {
        String raw = string(key, null);
        if (raw == null || raw.isBlank()) {
            error(key, "missing required '" + key + "' (one of " + preview(new ArrayList<>(names.get())) + ")");
            return null;
        }
        Optional<T> value = find.apply(raw.trim());
        if (value.isEmpty()) error(key, "unknown " + what + " '" + raw + "'", Suggestions.hint(raw, names.get()));
        return value.orElse(null);
    }

    /** Enum parse of a free-standing value (e.g. a list element); null when invalid, nothing reported. */
    public static <E extends Enum<E>> E parseEnum(String raw, Class<E> type) {
        if (raw == null) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static <E extends Enum<E>> List<String> enumNames(Class<E> type) {
        List<String> names = new ArrayList<>();
        for (E e : type.getEnumConstants()) names.add(e.name());
        return names;
    }

    public Material material(String key, Material def) {
        String raw = string(key, null);
        if (raw == null) return def;
        Material m = parseMaterial(raw);
        if (m == null) {
            warn(key, "unknown material '" + raw + "' — using " + (def == null ? "none" : def.name()), materialHint(raw));
            return def;
        }
        return m;
    }

    public Material requireMaterial(String key) {
        String raw = string(key, null);
        if (raw == null || raw.isBlank()) {
            error(key, "missing required '" + key + "'");
            return null;
        }
        Material m = parseMaterial(raw);
        if (m == null) error(key, "unknown material '" + raw + "'", materialHint(raw));
        return m;
    }

    /** {@code Material.matchMaterial} (accepts {@code minecraft:stone}, any case); null when unknown. */
    public static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Material.matchMaterial(raw.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String materialHint(String raw) {
        try {
            return Suggestions.hint(raw, enumNames(Material.class));
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ sections

    /** Child reader for a sub-section (null when absent — nothing reported). */
    public ConfigReader section(String key) {
        ConfigurationSection child = section == null ? null : section.getConfigurationSection(key);
        if (child == null) {
            if (section != null && section.contains(key)) warn(key, "expected a section, got " + describe(section.get(key)) + " — ignored");
            return null;
        }
        return new ConfigReader(child, scope == null ? null : scope.sub(key));
    }

    /** Child reader for a required sub-section; ERROR (and null) when missing. */
    public ConfigReader requireSection(String key) {
        ConfigReader child = section(key);
        if (child == null && (section == null || !section.contains(key))) error(key, "missing required section '" + key + "'");
        return child;
    }

    /**
     * Readers over a list of maps ({@code drops: [ {item: ..}, {item: ..} ]}), each reporting at
     * {@code key[i]}. Non-map elements are reported and skipped.
     */
    public List<ConfigReader> sectionList(String key) {
        if (section == null || !section.contains(key)) return List.of();
        List<?> list = section.getList(key);
        if (list == null) {
            ConfigurationSection asSection = section.getConfigurationSection(key);
            if (asSection != null) {
                // Keyed form (drops: {a: {...}, b: {...}}) — accept it too.
                List<ConfigReader> out = new ArrayList<>();
                for (String k : asSection.getKeys(false)) {
                    ConfigurationSection child = asSection.getConfigurationSection(k);
                    if (child != null) out.add(new ConfigReader(child, scope == null ? null : scope.sub(key).sub(k)));
                }
                return out;
            }
            warn(key, "expected a list, got " + describe(section.get(key)) + " — ignored");
            return List.of();
        }
        List<ConfigReader> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object element = list.get(i);
            LoadScope at = scope == null ? null : scope.sub(key).sub(String.valueOf(i));
            if (element instanceof Map<?, ?> map) {
                org.bukkit.configuration.MemoryConfiguration mem = new org.bukkit.configuration.MemoryConfiguration();
                for (Map.Entry<?, ?> e : map.entrySet()) mem.set(String.valueOf(e.getKey()), e.getValue());
                out.add(new ConfigReader(mem, at));
            } else if (element instanceof ConfigurationSection cs) {
                out.add(new ConfigReader(cs, at));
            } else if (at != null) {
                at.warn("expected a map, got " + describe(element) + " — skipped");
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static String describe(Object raw) {
        if (raw == null) return "nothing";
        if (raw instanceof ConfigurationSection || raw instanceof Map<?, ?>) return "a section";
        if (raw instanceof List<?>) return "a list";
        return "'" + raw + "'";
    }

    private static String humanName(Class<?> type) {
        return type.getSimpleName().replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
    }

    private static String preview(List<String> names) {
        return names.size() <= 8 ? String.join(", ", names) : String.join(", ", names.subList(0, 8)) + ", ...";
    }

    private static String format(double d) {
        return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** For callers that only need a sink (e.g. to forward diagnostics). */
    public DiagnosticSink sink() {
        return d -> {
            if (scope != null) scope.report(d.severity(), d.message(), d.hint());
            else Diagnostics.report(d.severity(), d.message(), d.hint());
        };
    }
}
