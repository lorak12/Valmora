package org.nakii.valmora.infrastructure.config.diag;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.nakii.valmora.util.Formatter;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Shows diagnostics in chat — shared by {@code /valmora reload}, {@code validate} and {@code report}. */
public final class ReportRenderer {

    public static final int PAGE_SIZE = 10;

    private ReportRenderer() {}

    /** Errors first (up to {@code maxErrors}), then warnings (up to {@code maxWarnings}). */
    public static void sendSummary(CommandSender sender, List<ConfigDiagnostic> diagnostics, int maxErrors, int maxWarnings) {
        sendSome(sender, diagnostics, Severity.ERROR, maxErrors);
        sendSome(sender, diagnostics, Severity.WARN, maxWarnings);
    }

    private static void sendSome(CommandSender sender, List<ConfigDiagnostic> diagnostics, Severity severity, int max) {
        List<ConfigDiagnostic> matching = diagnostics.stream().filter(d -> d.severity() == severity).toList();
        matching.stream().limit(max).forEach(d -> sender.sendMessage(line(d)));
        if (matching.size() > max) {
            sender.sendMessage(Formatter.format("<gray>... and " + (matching.size() - max) + " more "
                    + (severity == Severity.ERROR ? "error(s)" : "warning(s)") + " — <white>/valmora report "
                    + (severity == Severity.ERROR ? "errors" : "warnings")));
        }
    }

    /**
     * One page of the diagnostics matching {@code severityFilter} ("errors", "warnings", "all") and
     * {@code text} (matched against file, entry, category and message; null = everything).
     */
    public static void sendPage(CommandSender sender, List<ConfigDiagnostic> diagnostics, String severityFilter,
                                String text, int page) {
        Predicate<ConfigDiagnostic> bySeverity = switch (severityFilter == null ? "all" : severityFilter.toLowerCase(Locale.ROOT)) {
            case "errors", "error" -> d -> d.severity() == Severity.ERROR;
            case "warnings", "warning", "warns" -> d -> d.severity() == Severity.WARN;
            default -> d -> d.severity() != Severity.INFO;
        };
        String needle = text == null ? null : text.toLowerCase(Locale.ROOT);
        List<ConfigDiagnostic> matching = diagnostics.stream().filter(bySeverity)
                .filter(d -> needle == null || matches(d, needle)).toList();
        if (matching.isEmpty()) {
            sender.sendMessage(Formatter.format("<green>Nothing to show."));
            return;
        }
        int pages = (matching.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int p = Math.max(1, Math.min(page, pages));
        sender.sendMessage(Formatter.format("<gold>" + matching.size() + " problem(s) — page " + p + "/" + pages));
        matching.stream().skip((long) (p - 1) * PAGE_SIZE).limit(PAGE_SIZE).forEach(d -> sender.sendMessage(line(d)));
    }

    private static boolean matches(ConfigDiagnostic d, String needle) {
        return contains(d.file(), needle) || contains(d.entryId(), needle) || contains(d.category(), needle)
                || contains(d.message(), needle);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    public static net.kyori.adventure.text.Component line(ConfigDiagnostic d) {
        String tag = switch (d.severity()) {
            case ERROR -> "<red>[ERROR]";
            case WARN -> "<gold>[WARN]";
            case INFO -> "<gray>[INFO]";
        };
        return Formatter.format(tag + " <white>" + MiniMessage.miniMessage().escapeTags(d.format()));
    }
}
