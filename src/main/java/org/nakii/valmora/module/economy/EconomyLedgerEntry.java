package org.nakii.valmora.module.economy;

/**
 * One recorded purse↔bank transfer (deposit or withdraw). Backs the bank GUI's "Recent
 * Transactions" display — see {@link EconomyModule#getRecentTransactions(java.util.UUID)}.
 * Ordering (newest-first) is the caller's responsibility.
 */
public record EconomyLedgerEntry(String type, double amount, double purseAfter, double bankAfter, long createdAtMillis) {

    /** One MiniMessage-formatted display line, e.g. "Deposited 🪙 1.000". */
    public String formatLine() {
        String verb = "DEPOSIT".equals(type) ? "Deposited" : "Withdrew";
        return "<gray>" + verb + " <white>" + EconomyModule.formatCoinsDisplay(amount) + "</white>";
    }
}
