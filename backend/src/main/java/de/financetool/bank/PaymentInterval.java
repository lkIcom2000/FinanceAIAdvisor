package de.financetool.bank;

public enum PaymentInterval {
    MONTHLY(1), QUARTERLY(3), ANNUAL(12);

    private final int months;

    PaymentInterval(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }
}
