package net.phonex.util.guava;

import java.util.HashMap;

/**
 * Created by miroc on 7.12.14.
 */
public class MapUtils {

    public static <K, V> HashMap<K, V> mapOf(K k1, V v1) {
        return from(Tuple.of(k1, v1));
    }

    public static <K, V> HashMap<K, V> mapOf(K k1, V v1, K k2, V v2) {
        return from(Tuple.of(k1, v1), Tuple.of(k2, v2));
    }

    public static <K, V> HashMap<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        return from(Tuple.of(k1, v1), Tuple.of(k2, v2), Tuple.of(k3, v3));
    }

    public static <K, V> HashMap<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return from(Tuple.of(k1, v1), Tuple.of(k2, v2), Tuple.of(k3, v3), Tuple.of(k4, v4));
    }

    public static <K, V> HashMap<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        return from(Tuple.of(k1, v1), Tuple.of(k2, v2), Tuple.of(k3, v3), Tuple.of(k4, v4), Tuple.of(k5, v5));
    }

    public static <K, V> HashMap<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
        return from(Tuple.of(k1, v1), Tuple.of(k2, v2), Tuple.of(k3, v3), Tuple.of(k4, v4), Tuple.of(k5, v5), Tuple.of(k6, v6));
    }

    private static <K,V> HashMap<K, V> from(Tuple<K, V>... tuples){
        HashMap<K, V> map = new HashMap<K, V>(tuples.length);
        for (Tuple<K, V> tuple : tuples){
            map.put(tuple.getFirst(), tuple.getSecond());
        }
        return map;
    }
}
