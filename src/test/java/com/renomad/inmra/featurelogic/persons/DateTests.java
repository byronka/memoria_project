package com.renomad.inmra.featurelogic.persons;

import org.junit.Test;

import java.util.HashMap;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class DateTests {

    /**
     * We should be able to convert a date object to a string
     * form suitable for saving in the database.
     */
    @Test
    public void testSerializeDateToString() {
        Date date = new Date(1978, Month.JANUARY, 4);
        String expectedString = "1978.JANUARY.4";

        String serialized = date.toString();

        assertEquals(expectedString, serialized);
    }

    /**
     * We should be able to convert a date object to a pretty string
     * form suitable for inclusion in a block of text.
     */
    @Test
    public void testSerializeDateToPrettyString() {
        Date date = new Date(1978, Month.JANUARY, 4);
        String expectedString = "January 4, 1978";

        String serialized = date.getPrettyString();

        assertEquals(expectedString, serialized);
    }

    /**
     * We should be able to convert a database-serialized string
     * form of this data back to a Date.
     */
    @Test
    public void testConvertStringToDate() {
        String serializedData = "1978.JANUARY.4";
        Date expectedDate = new Date(1978, Month.JANUARY, 4);

        Date date = Date.fromString(serializedData);

        assertEquals(expectedDate, date);
    }

    /**
     * We should be able to convert a date object to a string
     * form suitable for saving in the database.
     */
    @Test
    public void testSerializeDateToHTMLString() {
        Date date = new Date(1978, Month.JANUARY, 4);
        String expectedString = "1978-01-04";

        String serialized = date.toHtmlString();

        assertEquals(expectedString, serialized);
    }

    /**
     * We should be able to convert a database-serialized string
     * form of this data back to a Date.
     */
    @Test
    public void testConvertHTMLStringToDate() {
        String serializedData = "1978-1-4";
        Date expectedDate = new Date(1978, Month.JANUARY, 4);

        Date date = Date.extractDate(serializedData);

        assertEquals(expectedDate, date);
    }

    /**
     * Test that if we use a Date object in a map as a key, it
     * hashes the values of the object properly.  That is, two
     * different objects with the same year, month, and day
     * will be treated as the same.
     */
    @Test
    public void testUsableInHashmap_EdgeCase_DuplicateValues() {
        Date date1 = new Date(1978, Month.JANUARY, 4);
        Date date2 = new Date(1978, Month.JANUARY, 4);
        HashMap<Date, Integer> dateIntegerHashMap = new HashMap<>();
        dateIntegerHashMap.put(date1, 1);
        dateIntegerHashMap.put(date2, 2);

        // even though we are using the first key, we
        // get the data put in with the second key, because they hash the same.
        Integer value = dateIntegerHashMap.get(date1);

        assertEquals(2, value);
    }

    @Test
    public void testUsableInHashmap() {
        Date date1 = new Date(1978, Month.JANUARY, 4);
        Date date2 = new Date(2011, Month.NOVEMBER, 29);
        HashMap<Date, Integer> dateIntegerHashMap = new HashMap<>();
        dateIntegerHashMap.put(date1, 1);
        dateIntegerHashMap.put(date2, 2);

        Integer value = dateIntegerHashMap.get(date1);

        assertEquals(1, value);
    }

}
