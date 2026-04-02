package nl.hauntedmc.dungeons.util.file;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StringUtils {
   public static Optional<Integer> readIntegerInput(CommandSender sender, String string) {
      try {
         int value = Integer.parseInt(string);
         return Optional.of(value);
      } catch (NumberFormatException var3) {
         sender.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cPlease enter a valid number! (1, 7, 42, etc.)"));
         return Optional.empty();
      }
   }

   public static Optional<Double> readDoubleInput(Player player, String string) {
      try {
         double value = Double.parseDouble(string);
         return Optional.of(value);
      } catch (NumberFormatException var4) {
         player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cPlease enter a valid number! (1.2, 7.5, 42, etc.)"));
         return Optional.empty();
      }
   }

   public static void sendClickableLink(Player player, String message, String url) {
      player.sendMessage(HelperUtils.component(message).clickEvent(ClickEvent.openUrl(url)));
   }

   public static void sendClickableCommand(Player player, String message, String command) {
      player.sendMessage(HelperUtils.component(message).clickEvent(ClickEvent.runCommand("/" + command)));
   }

   public static void sendReadyCheckMessage(Player player) {
      Component message = HelperUtils.component(LangUtils.getMessage("instance.queue.click-one"))
         .append(HelperUtils.component(LangUtils.getMessage("instance.queue.ready-button", false)).clickEvent(ClickEvent.runCommand("/ready")))
         .append(HelperUtils.component(LangUtils.getMessage("instance.queue.cancel-button", false)).clickEvent(ClickEvent.runCommand("/notready")));
      player.sendMessage(message);
   }

   public static String formatDate(Date date) {
      SimpleDateFormat format = new SimpleDateFormat("EEE, MMM d, hh:mm aaa z", LangUtils.getLocale());
      return format.format(date);
   }

   public static Date convertDurationString(String durString) {
      Pattern pat = Pattern.compile("((^[0-9]*)([smhdwySMHDWY]))");
      Matcher matcher = pat.matcher(durString);
      if (matcher.find()) {
         String length = matcher.group(2);
         int duration = Integer.parseInt(length);
         String timeFrame = matcher.group(3);
         int multiplier = 1;
         String var7 = timeFrame.toLowerCase();
          multiplier = switch (var7) {
              case "m" -> 60;
              case "h" -> 3600;
              case "d" -> 86400;
              case "w" -> 604800;
              case "y" -> 31557600;
              default -> multiplier;
          };

         return HelperUtils.getFutureTimeInSeconds(duration * multiplier);
      } else {
         return null;
      }
   }

   public static String formatDuration(long durationInMillis) {
      long second = durationInMillis / 1000L % 60L;
      long minute = durationInMillis / 60000L % 60L;
      long hour = durationInMillis / 3600000L % 24L;
      String secondStr = String.valueOf(second);
      if (second < 10L) {
         secondStr = "0" + secondStr;
      }

      String minuteStr = String.valueOf(minute);
      if (minute < 10L) {
         minuteStr = "0" + minuteStr;
      }

      String hourStr = String.valueOf(hour);
      if (hour < 10L) {
         hourStr = "0" + hourStr;
      }

      return hourStr + ":" + minuteStr + ":" + secondStr;
   }
}
