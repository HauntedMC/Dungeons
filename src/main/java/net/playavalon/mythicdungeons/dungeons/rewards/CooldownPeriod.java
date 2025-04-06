package net.playavalon.mythicdungeons.dungeons.rewards;

import java.util.Calendar;
import java.util.Date;

public enum CooldownPeriod {
   TIMER(12),
   HOURLY(10),
   DAILY(5),
   WEEKLY(5, 7),
   MONTHLY(2);

   public final int period;
   public final int amount;

   private CooldownPeriod(int period) {
      this.period = period;
      this.amount = 1;
   }

   private CooldownPeriod(int period, int amount) {
      this.period = period;
      this.amount = amount;
   }

   public Date fromNow() {
      return this.fromNow(this.amount);
   }

   public Date fromNow(int amount) {
      Calendar cal = Calendar.getInstance();
      cal.add(this.period, amount);
      return cal.getTime();
   }

   public Date fromDate(Date date) {
      return this.fromDate(date, this.amount);
   }

   public Date fromDate(Date date, int amount) {
      Calendar cal = Calendar.getInstance();
      cal.setTime(date);
      cal.add(this.period, amount);
      return cal.getTime();
   }

   public static CooldownPeriod intToPeriod(int index) {
      return switch (index) {
         default -> TIMER;
         case 1 -> HOURLY;
         case 2 -> DAILY;
         case 3 -> WEEKLY;
         case 4 -> MONTHLY;
      };
   }
}
