package nl.hauntedmc.dungeons.util.math;

import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

public class RandomCollection<E> {
   private final NavigableMap<Double, E> map = new TreeMap<>();
   private final Random random;
   private double total = 0.0;

   public RandomCollection() {
      this(new Random());
   }

   public RandomCollection(Random random) {
      this.random = random;
   }

   public RandomCollection<E> add(double weight, E result) {
       if (!(weight <= 0.0)) {
           this.total += weight;
           this.map.put(this.total, result);
       }
       return this;
   }

   public E next() {
      double value = this.random.nextDouble() * this.total;
      return this.map.higherEntry(value).getValue();
   }

   public void remove(double weight, E value) {
      this.map.remove(weight, value);
   }

   public void clear() {
      this.map.clear();
      this.total = 0.0;
   }

   public boolean contains(E value) {
      return this.map.containsValue(value);
   }

   public int size() {
      return this.map.size();
   }
}
