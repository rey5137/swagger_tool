package com.rey.swagger;

import java.util.List;

class Utils {

    static <T> T findNotContains(List<? extends T> expected, List<? extends T> list) {
        if (expected == null || expected.isEmpty())
            return null;

        if (list == null)
            return expected.get(0);

        return expected.stream()
                .filter(item -> !list.contains(item))
                .findFirst()
                .orElse(null);
    }
}
