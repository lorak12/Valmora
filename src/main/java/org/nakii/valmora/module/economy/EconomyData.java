package org.nakii.valmora.module.economy;

public class EconomyData {
    private double purse;
    private double bank;

    public EconomyData(double purse, double bank) {
        this.purse = Math.max(0, purse);
        this.bank = Math.max(0, bank);
    }

    public double getPurse() { return purse; }
    public double getBank() { return bank; }
    public double getTotal() { return purse + bank; }

    public void setPurse(double v) { this.purse = Math.max(0, v); }
    public void setBank(double v) { this.bank = Math.max(0, v); }
}
