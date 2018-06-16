package ru.spbau.farutin.arch_test.util;

import org.jetbrains.annotations.NotNull;

public class BubbleSort {
    public static void sort(@NotNull int[] array) {
        int size = array.length;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size - 1; j++) {
                if (array[j] > array[j + 1]) {
                    int t = array[j];
                    array[j] = array[j + 1];
                    array[j + 1] = t;
                }
            }
        }
    }
}
