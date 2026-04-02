package nl.hauntedmc.dungeons.util.math;

public class RangedNumber {
   private double min;
   private double max;

    public RangedNumber(String raw) {
      if (raw == null || raw.isEmpty() || raw.equals("null")) {
         raw = "0+";
      }

      String[] split = null;
      if (raw.contains("to")) {
         split = raw.split("to");
      } else if (!raw.startsWith("-") && raw.contains("-")) {
         split = raw.split("-");
      } else {
          if (raw.endsWith("+")) {
            split = raw.split("\\+");
            this.min = Double.parseDouble(split[0]);
            this.max = -1.0;
            return;
         }

         if (raw.startsWith("<=")) {
            String sub = raw.substring(2);
            this.min = 0.0;
            this.max = Double.parseDouble(sub);
            return;
         }

         if (raw.startsWith(">=")) {
            String sub = raw.substring(2);
            this.min = Double.parseDouble(sub);
            this.max = -1.0;
            return;
         }

         if (raw.startsWith("<")) {
            String sub = raw.substring(1);
            this.min = 0.0;
            this.max = Double.parseDouble(sub) - 1.0;
            return;
         }

         if (raw.startsWith(">")) {
            String sub = raw.substring(1);
            this.min = Double.parseDouble(sub) + 1.0;
            this.max = -1.0;
            return;
         }
      }

      if (split == null) {
         this.min = Double.parseDouble(raw);
         this.max = this.min;
      } else {
         this.min = Double.parseDouble(split[0]);
         this.max = Double.parseDouble(split[1]);
         if (this.min > this.max) {
            double stored = this.min;
            this.min = this.max;
            this.max = stored;
         }
      }
   }

   public RangedNumber(double min, double max) {
      this.min = min;
      this.max = max;
      if (max != -1.0) {
         if (this.min > this.max) {
            double stored = this.min;
            this.min = this.max;
            this.max = stored;
         }
      }
   }

   public int randomizeAsInt() {
      return (int)this.min == (int)this.max
         ? (int)this.min
         : MathUtils.getRandomNumberInRange((int)this.min, (int)(this.max == -1.0 ? 2.147483647E9 : this.max));
   }

   @Override
   public String toString() {
      if (this.min == this.max) {
         return String.valueOf(this.min);
      } else {
         return this.max == -1.0 ? this.min + "+" : this.min + "-" + this.max;
      }
   }

   public String toIntString() {
      if (this.min == this.max) {
         return String.valueOf((int)this.min);
      } else {
         return this.max == -1.0 ? (int)this.min + "+" : (int)this.min + "-" + (int)this.max;
      }
   }

   public boolean isValueWithin(double value) {
      return this.max == -1.0 ? value >= this.min : value >= this.min && value <= this.max;
   }

   public double getMin() {
      return this.min;
   }

   public double getMax() {
      return this.max;
   }

   public void setMax(double max) {
      this.max = max;
   }

}
