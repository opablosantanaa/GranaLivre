package com.opablosantanaa.granalivre.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class InstallmentSplitterTest {
    @Test
    public void remainderGoesToFirstParcel() {
        assertEquals(Arrays.asList(3334L, 3333L, 3333L), InstallmentSplitter.split(10000, 3));
    }

    @Test
    public void parcelsSumToTotal() {
        List<Long> parcels = InstallmentSplitter.split(99999, 7);
        long sum = 0;
        for (long parcel : parcels) {
            sum += parcel;
        }
        assertEquals(99999, sum);
        assertEquals(7, parcels.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void totalSmallerThanCountIsRejected() {
        InstallmentSplitter.split(2, 3);
    }
}
