package com.opablosantanaa.granalivre.relation;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.opablosantanaa.granalivre.entity.FinancialTransaction;
import com.opablosantanaa.granalivre.entity.Invoice;

import java.util.List;

public class InvoiceWithTransactions {
    @Embedded
    public Invoice invoice;

    @Relation(parentColumn = "invoice_id", entityColumn = "invoice_id")
    public List<FinancialTransaction> transactions;
}
