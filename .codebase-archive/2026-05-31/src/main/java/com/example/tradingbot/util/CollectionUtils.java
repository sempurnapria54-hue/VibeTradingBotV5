package com.example.tradingbot.util;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.util.CollectionUtils.isEmpty;

@UtilityClass
public class CollectionUtils {

    public static void checkContains(String value, String fieldName, Set<String> allowedValues) {
        if (isBlank(value) || isFalse(allowedValues.contains(value))) {
            throw new IllegalArgumentException(
                    fieldName + " has unsupported value: " + value + ". Allowed values: " + allowedValues
            );
        }
    }

    public static boolean doNotContains(Collection<?> collection, Object value) {
        return isEmpty(collection) || isNull(value) || isFalse(collection.contains(value));
    }

    public static <T> Collection<T> emptyIfNull(Collection<T> collection) {
        return isNull(collection) ? Collections.emptyList() : collection;
    }
}
