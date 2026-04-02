package nl.hauntedmc.dungeons.api.gui.utility;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;

public class StringUtils {
   public static String colorize(String s) {
      return s == null ? null : ChatColor.translateAlternateColorCodes('&', s);
   }

   public static String fullColor(String s) {
      StringBuilder sb = new StringBuilder();
      String[] strs = s.split("(?=(\\{#[a-fA-F0-9]*}))");
      Pattern pat = Pattern.compile("(\\{(#[a-fA-F0-9]*)})(.*)");

      for (String str : strs) {
         Matcher matcher = pat.matcher(str);
         if (matcher.find()) {
            sb.append(ChatColor.of(matcher.group(2)));
            sb.append(matcher.group(3));
         } else {
            sb.append(str);
         }
      }

      return colorize(sb.toString());
   }
}
