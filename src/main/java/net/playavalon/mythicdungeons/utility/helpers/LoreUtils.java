package net.playavalon.mythicdungeons.utility.helpers;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;

public final class LoreUtils {
   private static int defaultWrapLength = 35;

   public static List<String> wrapLore(String lore, boolean wordWrap) {
      return wrapLore(lore, defaultWrapLength, wordWrap);
   }

   public static List<String> wrapLore(String lore, int maxLength, boolean wordWrap) {
      List<String> wrapedLore = new ArrayList<>();
      String[] lines = lore.split("\\r?\\n");

      for (String line : lines) {
         String unwrapedLine = line.replaceFirst("\\s++$", "");

         while (unwrapedLine.length() > maxLength) {
            int splitIndex;
            if (wordWrap) {
               splitIndex = getWrapIndex(unwrapedLine, maxLength);
            } else {
               splitIndex = maxLength;
            }

            String newLine = unwrapedLine.substring(0, splitIndex);
            newLine = newLine.replaceFirst("\\s++$", "");
            String chatColor = ChatColor.getLastColors(newLine);
            unwrapedLine = chatColor + unwrapedLine.substring(splitIndex);
            wrapedLore.add(newLine);
         }

         wrapedLore.add(unwrapedLine);
      }

      return wrapedLore;
   }

   private static int getWrapIndex(String line, int maxLength) {
      int splitIndex = maxLength;
      if (maxLength > line.length()) {
         splitIndex = line.length();
      }

      for (int spaceIndex = splitIndex; spaceIndex >= 0; spaceIndex--) {
         if (line.charAt(spaceIndex) == ' ') {
            return spaceIndex + 1;
         }
      }

      return splitIndex;
   }

   public static void setDefauldWrapLength(int WrapLength) {
      defaultWrapLength = WrapLength;
   }
}
