package nl.hauntedmc.dungeons.dungeons.variables;

import java.util.HashMap;
import java.util.Map;

public class VariableHolder {
   private final Map<String, String> vars = new HashMap<>();

   public String getString(String var) {
      String val = this.vars.get(var);
      if (val == null) {
         return null;
      } else {
         try {
            double d = Double.parseDouble(val);
            if (d % 1.0 == 0.0) {
               val = String.valueOf((int)d);
            } else {
               val = String.valueOf(d);
            }
         } catch (NumberFormatException ignored) {
         }

         return val;
      }
   }

   public int getInt(String var) {
      try {
         return Integer.parseInt(this.vars.get(var));
      } catch (NumberFormatException var3) {
         return 0;
      }
   }

   public double getDouble(String var) {
      try {
         return Double.parseDouble(this.vars.get(var));
      } catch (NumberFormatException var3) {
         return 0.0;
      }
   }

   public void set(String var, String value) {
      this.vars.put(var, value);
   }

   public void set(String var, int value) {
      this.vars.put(var, String.valueOf(value));
   }

   public void set(String var, double value) {
      this.vars.put(var, String.valueOf(value));
   }

   public void add(String var, double value) {
      try {
         double current = Double.parseDouble(this.vars.getOrDefault(var, "0"));
         this.vars.put(var, String.valueOf(current + value));
      } catch (NumberFormatException ignored) {
      }
   }

   public void subtract(String var, double value) {
      try {
         double current = Double.parseDouble(this.vars.getOrDefault(var, "0"));
         this.vars.put(var, String.valueOf(current - value));
      } catch (NumberFormatException ignored) {
      }
   }
}
