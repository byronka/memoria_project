package com.renomad.inmra.featurelogic.persons;

import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class MonthTests {

    @Test
    public void testGettingByOrdinal() {
        assertEquals(Month.getByOrdinal(1), Month.JANUARY);
        assertEquals(Month.getByOrdinal(2), Month.FEBRUARY);
        assertEquals(Month.getByOrdinal(3), Month.MARCH);
        assertEquals(Month.getByOrdinal(4), Month.APRIL);
        assertEquals(Month.getByOrdinal(5), Month.MAY);
        assertEquals(Month.getByOrdinal(6), Month.JUNE);
        assertEquals(Month.getByOrdinal(7), Month.JULY);
        assertEquals(Month.getByOrdinal(8), Month.AUGUST);
        assertEquals(Month.getByOrdinal(9), Month.SEPTEMBER);
        assertEquals(Month.getByOrdinal(10), Month.OCTOBER);
        assertEquals(Month.getByOrdinal(11), Month.NOVEMBER);
        assertEquals(Month.getByOrdinal(12), Month.DECEMBER);
    }

}
