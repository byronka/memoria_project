package com.renomad.inmra.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomnessUtils {

    static Random rand = new Random();

    /**
     * Grabs a number of items, randomly, from a list.
     * <br>
     * Edge conditions:
     * a) If we are passed a null for the list, or an empty list, we'll return an empty unmodifiable list
     * b) If we are given a list with one item, we'll just return it
     * c) if we are given a count larger than the list, we'll only grab up to the list size
     * @param list the list from where we grab items
     * @param count the number of items to grab
     * @param <T> the type of item in the list
     */
    public static <T> List<T> randomSample(List<T> list, int count) {
        if (list == null || list.isEmpty()) {
            return List.of();
        } else if (list.size() == 1) {
            return list;
        }
        List<T> sample = new ArrayList<>();

        int modifiedCount = Math.min(count, list.size());

        for (int sortedSampleIndices[] = new int[modifiedCount], i = 0; i < modifiedCount; i++) {
            int index = rand.nextInt(list.size() - i);

            int j = 0;
            for (; j < i && index >= sortedSampleIndices[j]; j++)
                index++;
            sample.add(list.get(index));

            for (; j <= i; j++) {
                int temp = sortedSampleIndices[j];
                sortedSampleIndices[j] = index;
                index = temp;
            }
        }

        return sample;
    }

}
