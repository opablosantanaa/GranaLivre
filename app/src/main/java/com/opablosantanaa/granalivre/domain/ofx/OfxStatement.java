package com.opablosantanaa.granalivre.domain.ofx;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public final class OfxStatement {
    public final String bankId;
    public final String accountId;
    public final String currency;
    public final boolean creditCard;
    public final LocalDate start;
    public final LocalDate end;
    public final Long ledgerBalanceCents;
    public final LocalDate ledgerBalanceDate;
    public final List<OfxTransaction> transactions;

    public OfxStatement(String bankId, String accountId, String currency, boolean creditCard, LocalDate start,
                        LocalDate end, Long ledgerBalanceCents, LocalDate ledgerBalanceDate,
                        List<OfxTransaction> transactions) {
        this.bankId = bankId;
        this.accountId = accountId;
        this.currency = currency;
        this.creditCard = creditCard;
        this.start = start;
        this.end = end;
        this.ledgerBalanceCents = ledgerBalanceCents;
        this.ledgerBalanceDate = ledgerBalanceDate;
        this.transactions = Collections.unmodifiableList(transactions);
    }
}
