package io.github.maliciousfiles.serversideProxyChat.util;

import java.util.Map;


/**
 * A simple implementation of {@link Map.Entry} that is equal to another entry even if the values are swapped.
 * @param <K> Key type
 * @param <V> Value type
 */
public class BidiEntry<K, V> implements Map.Entry<K, V> {

    private final K key;
    private V value;

    public BidiEntry(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public K getKey() {
        return this.key;
    }

    @Override
    public V getValue() {
        return this.value;
    }

    @Override
    public V setValue(V value) {
        V old = this.value;

        this.value = value;
        return old;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BidiEntry<?,?> entry &&
                (entry.value.equals(value) && entry.key.equals(key) ||
                 entry.value.equals(key) && entry.key.equals(value));
    }
}
