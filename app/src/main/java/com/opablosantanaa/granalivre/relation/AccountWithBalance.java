package com.opablosantanaa.granalivre.relation;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;

import com.opablosantanaa.granalivre.entity.Account;

public class AccountWithBalance {
    @Embedded
    public Account account;

    @ColumnInfo (name = "balance_cents")
    public long balanceCents;
}
