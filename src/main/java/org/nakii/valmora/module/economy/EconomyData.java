package org.nakii.valmora.module.economy;

/**
 * A single player's purse/bank balances. All compound operations (add/remove/transfer) are
 * synchronized on the instance so concurrent mutations for the *same* player are serialized
 * and never lose an update, while different players never contend with each other (each has
 * their own instance/lock) — the standard shape for a per-key lock at scale.
 */
public final class EconomyData {
    private double purse;
    private double bank;

    public EconomyData(double purse, double bank) {
        this.purse = Math.max(0, purse);
        this.bank = Math.max(0, bank);
    }

    public synchronized double getPurse() { return purse; }
    public synchronized double getBank() { return bank; }
    public synchronized double getTotal() { return purse + bank; }

    public synchronized void setPurse(double v) { this.purse = Math.max(0, v); }
    public synchronized void setBank(double v) { this.bank = Math.max(0, v); }

    public synchronized void addPurse(double amount) { purse = Math.max(0, purse + amount); }
    public synchronized void removePurse(double amount) { purse = Math.max(0, purse - amount); }

    public synchronized void addBank(double amount) { bank = Math.max(0, bank + amount); }
    public synchronized void removeBank(double amount) { bank = Math.max(0, bank - amount); }

    public synchronized boolean hasPurse(double amount) { return purse >= amount; }
    public synchronized boolean hasBank(double amount) { return bank >= amount; }

    /** Atomically moves {@code amount} from purse to bank. Returns false (no-op) if purse is short. */
    public synchronized boolean deposit(double amount) {
        if (amount <= 0 || purse < amount) return false;
        purse -= amount;
        bank += amount;
        return true;
    }

    /** Atomically moves {@code amount} from bank to purse. Returns false (no-op) if bank is short. */
    public synchronized boolean withdraw(double amount) {
        if (amount <= 0 || bank < amount) return false;
        bank -= amount;
        purse += amount;
        return true;
    }

    /** Atomically moves the entire purse into bank. Returns the amount moved (0 if purse was empty). */
    public synchronized double depositAll() {
        double amount = purse;
        if (amount > 0) {
            purse = 0;
            bank += amount;
        }
        return amount;
    }

    /** Atomically moves the entire bank into purse. Returns the amount moved (0 if bank was empty). */
    public synchronized double withdrawAll() {
        double amount = bank;
        if (amount > 0) {
            bank = 0;
            purse += amount;
        }
        return amount;
    }

    /** Consistent point-in-time [purse, bank] snapshot, for persistence. */
    public synchronized double[] snapshot() {
        return new double[]{purse, bank};
    }
}
