package com.renomad.inmra.utils;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static com.renomad.inmra.utils.RandomnessUtils.randomSample;
import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertTrue;

public class RandomnessUtilsTests {

    @Test
    public void testRandomSampleHappyPath() {
        List<Integer> integers = randomSample(List.of(1, 2, 3), 1);
        assertEquals(integers.size(), 1);
        assertTrue(Set.of(1,2,3).contains(integers.getFirst()));
    }

    /**
     * If the user asks for more values than exists in the list,
     * we'll adjust so that they are asking for the size of the list.
     */
    @Test
    public void testRandomSample_AskingForMoreThanPossible() {
        List<Integer> integers = randomSample(List.of(1, 2, 3), 4);
        assertEquals(integers.size(), 3);
        assertEquals(integers.stream().mapToInt(Integer::intValue).sum(), 6);
    }

    /**
     * If the user asks for same number of values as in the list, it
     * should just work
     */
    @Test
    public void testRandomSample_AskingForSize() {
        List<Integer> integers = randomSample(List.of(1, 2, 3), 3);
        assertEquals(integers.size(), 3);
        assertEquals(integers.stream().mapToInt(Integer::intValue).sum(), 6);
    }

    /**
     * If the user asks for no items, return an empty list
     */
    @Test
    public void testRandomSample_AskingForZero() {
        List<Integer> integers = randomSample(List.of(1, 2, 3), 0);
        assertEquals(integers.size(), 0);
    }

    /**
     * If the user provides a null list, return an empty list
     */
    @Test
    public void testRandomSample_AskingForItemsFromNullList() {
        List<Integer> integers = randomSample(null, 5);
        assertEquals(integers.size(), 0);
    }

    /**
     * If the user provides an empty list, return an empty list
     */
    @Test
    public void testRandomSample_AskingForItemsFromEmptyList() {
        List<Integer> integers = randomSample(List.of(), 5);
        assertEquals(integers.size(), 0);
    }
}
