package com.opablosantanaa.granalivre.domain.ofx;

import java.time.LocalDate;

public final class OfxTransaction {
    public final String importId;
    public final String type;
    public final LocalDate date;
    public final long amountCents;
    public final String description;

    public OfxTransaction(String importId, String type, LocalDate date, long amountCents, String description) {
        this.importId = importId;
        this.type = type;
        this.date = date;
        this.amountCents = amountCents;
        this.description = description;
    }

    public boolean isIncome() {
        return amountCents > 0;
    }
}
