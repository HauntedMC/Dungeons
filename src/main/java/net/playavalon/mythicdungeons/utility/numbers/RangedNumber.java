package net.playavalon.mythicdungeons.utility.numbers;

import net.playavalon.mythicdungeons.utility.helpers.MathUtils;

public class RangedNumber {
   private double min = 0.0;
   private double max = 0.0;
   private RangedNumber.Operation op;

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
            this.op = RangedNumber.Operation.GREATER_THAN;
            return;
         }

         if (raw.startsWith("<=")) {
            String sub = raw.substring(2);
            this.min = 0.0;
            this.max = Double.parseDouble(sub);
            this.op = RangedNumber.Operation.LESS_THAN;
            return;
         }

         if (raw.startsWith(">=")) {
            String sub = raw.substring(2);
            this.min = Double.parseDouble(sub);
            this.max = -1.0;
            this.op = RangedNumber.Operation.GREATER_THAN;
            return;
         }

         if (raw.startsWith("<")) {
            String sub = raw.substring(1);
            this.min = 0.0;
            this.max = Double.parseDouble(sub) - 1.0;
            this.op = RangedNumber.Operation.LESS_THAN;
            return;
         }

         if (raw.startsWith(">")) {
            String sub = raw.substring(1);
            this.min = Double.parseDouble(sub) + 1.0;
            this.max = -1.0;
            this.op = RangedNumber.Operation.GREATER_THAN;
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

   public double randomize() {
      return this.min == this.max ? this.min : MathUtils.getRandomDoubleInRange(this.min, this.max == -1.0 ? 2.147483647E9 : this.max);
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

   public void setMin(double min) {
      this.min = min;
   }

   public double getMax() {
      return this.max;
   }

   public void setMax(double max) {
      this.max = max;
   }

   public RangedNumber.Operation getOp() {
      return this.op;
   }

   public void setOp(RangedNumber.Operation op) {
      this.op = op;
   }

   public static enum Operation {
      EQUALS,
      GREATER_THAN,
      LESS_THAN,
      RANGE;
   }
}
