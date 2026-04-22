package nl.hauntedmc.dungeons.util.math;

import java.util.ArrayList;
import java.util.Collection;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weighted random collection with cumulative weight selection.
 */
public class RandomCollection<E> {
    private final NavigableMap<Double, E> map = new TreeMap<>();
    private final Random random;
    private double total = 0.0;

    /** Creates a collection using thread-local random source. */
    public RandomCollection() {
        this(null);
    }

    /** Creates a collection using a custom random source when provided. */
    public RandomCollection(Random random) {
        this.random = random;
    }

    /** Adds an item with weight contribution. */
    public RandomCollection<E> add(double weight, E result) {
        if (weight > 0.0 && result != null) {
            this.total += weight;
            this.map.put(this.total, result);
        }
        return this;
    }

    /** Returns one weighted-random item, or null when the collection is empty. */
    public E next() {
        if (this.map.isEmpty() || this.total <= 0.0) {
            return null;
        }

        double value =
                (this.random != null ? this.random.nextDouble() : ThreadLocalRandom.current().nextDouble())
                     * this.total;
        var entry = this.map.higherEntry(value);
        if (entry != null) {
            return entry.getValue();
        }

        return this.map.lastEntry() == null ? null : this.map.lastEntry().getValue();
    }

    /** Removes one exact cumulative-weight entry/value pair. */
    public void remove(double weight, E value) {
        this.map.remove(weight, value);
    }

    /** Clears all entries and resets weight totals. */
    public void clear() {
        this.map.clear();
        this.total = 0.0;
    }

    /** Returns whether the collection contains the supplied value. */
    public boolean contains(E value) {
        return this.map.containsValue(value);
    }

    /** Returns whether no entries are present. */
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    /** Returns entry count. */
    public int size() {
        return this.map.size();
    }

    /** Returns snapshot values ordered by cumulative weight key. */
    public Collection<E> values() {
        return new ArrayList<>(this.map.values());
    }
}
