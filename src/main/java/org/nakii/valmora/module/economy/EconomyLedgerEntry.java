package org.nakii.valmora.module.economy;

/**
 * One recorded purse↔bank transfer (deposit or withdraw). Backs the bank GUI's "Recent
 * Transactions" display — see {@link EconomyModule#getRecentTransactions(java.util.UUID)}.
 * Ordering (newest-first) is the caller's responsibility.
 */
public record EconomyLedgerEntry(String type, double amount, double purseAfter, double bankAfter, long createdAtMillis) {

    /** One MiniMessage-formatted display line, e.g. "Deposited 🪙 1.000". HC-017: verb text is
     *  configurable via {@code economy.messages.deposit-verb}/{@code withdraw-verb}. */
    public String formatLine() {
        var plugin = org.nakii.valmora.Valmora.getInstance();
        String depositVerb = plugin != null ? plugin.getConfig().getString("economy.messages.deposit-verb", "Deposited") : "Deposited";
        String withdrawVerb = plugin != null ? plugin.getConfig().getString("economy.messages.withdraw-verb", "Withdrew") : "Withdrew";
        String verb = "DEPOSIT".equals(type) ? depositVerb : withdrawVerb;
        return "<gray>" + verb + " <white>" + EconomyModule.formatCoinsDisplay(amount) + "</white>";
    }
}
