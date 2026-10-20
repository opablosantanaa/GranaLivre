package com.opablosantanaa.granalivre.domain;

import java.util.ArrayList;
import java.util.List;

public final class InstallmentSplitter {
    private InstallmentSplitter() {}

    public static List<Long> split(long totalCents, int count) {
        if (count <= 0 || totalCents < count) {
            throw new IllegalArgumentException("count must be positive and not greater than totalCents");
        }
        long base = totalCents / count;
        long remainder = totalCents % count;
        List<Long> parcels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            parcels.add(i == 0 ? base + remainder : base);
        }
        return parcels;
    }
}
