package nl.hauntedmc.dungeons.util.math;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class MathUtils {
   public static double round(double value, int places) {
      double scale = Math.pow(10.0, places);
      return Math.round(value * scale) / scale;
   }

   public static int getRandomNumber(Integer... values) {
      return values.length == 0 ? -1 : values[getRandomNumberInRange(0, values.length - 1)];
   }

   public static int getRandomNumberInRange(int min, int max) {
      if (min >= max) {
         return min;
      } else {
         Random r = new Random();
         return r.nextInt(max - min + 1) + min;
      }
   }

   public static double getRandomDoubleInRange(double min, double max) {
      return min >= max ? min : ThreadLocalRandom.current().nextDouble(min, max);
   }

   public static boolean getRandomBoolean(double chance) {
      return Math.random() < chance;
   }

   public static boolean isNegative(double d) {
      return Double.doubleToRawLongBits(d) < 0L;
   }
}
