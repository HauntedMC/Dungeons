package net.playavalon.mythicdungeons.utility.helpers;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.md_5.bungee.api.ChatColor;

public final class ColorUtils {
   private static Map<ChatColor, ColorUtils.ColorSet<Integer, Integer, Integer>> colorMap = new HashMap<>();

   public static ChatColor fromRGB(int r, int g, int b) {
      TreeMap<Integer, ChatColor> closest = new TreeMap<>();
      colorMap.forEach((color, set) -> {
         int red = Math.abs(r - set.getRed());
         int green = Math.abs(g - set.getGreen());
         int blue = Math.abs(b - set.getBlue());
         closest.put(red + green + blue, color);
      });
      return closest.firstEntry().getValue();
   }

   static {
      colorMap.put(ChatColor.BLACK, new ColorUtils.ColorSet<>(0, 0, 0));
      colorMap.put(ChatColor.DARK_BLUE, new ColorUtils.ColorSet<>(0, 0, 170));
      colorMap.put(ChatColor.DARK_GREEN, new ColorUtils.ColorSet<>(0, 170, 0));
      colorMap.put(ChatColor.DARK_AQUA, new ColorUtils.ColorSet<>(0, 170, 170));
      colorMap.put(ChatColor.DARK_RED, new ColorUtils.ColorSet<>(170, 0, 0));
      colorMap.put(ChatColor.DARK_PURPLE, new ColorUtils.ColorSet<>(170, 0, 170));
      colorMap.put(ChatColor.GOLD, new ColorUtils.ColorSet<>(255, 170, 0));
      colorMap.put(ChatColor.GRAY, new ColorUtils.ColorSet<>(170, 170, 170));
      colorMap.put(ChatColor.DARK_GRAY, new ColorUtils.ColorSet<>(85, 85, 85));
      colorMap.put(ChatColor.BLUE, new ColorUtils.ColorSet<>(85, 85, 255));
      colorMap.put(ChatColor.GREEN, new ColorUtils.ColorSet<>(85, 255, 85));
      colorMap.put(ChatColor.AQUA, new ColorUtils.ColorSet<>(85, 255, 255));
      colorMap.put(ChatColor.RED, new ColorUtils.ColorSet<>(255, 85, 85));
      colorMap.put(ChatColor.LIGHT_PURPLE, new ColorUtils.ColorSet<>(255, 85, 255));
      colorMap.put(ChatColor.YELLOW, new ColorUtils.ColorSet<>(255, 255, 85));
      colorMap.put(ChatColor.WHITE, new ColorUtils.ColorSet<>(255, 255, 255));
   }

   private static class ColorSet<R, G, B> {
      R red = (R)null;
      G green = (G)null;
      B blue = (B)null;

      ColorSet(R red, G green, B blue) {
         this.red = red;
         this.green = green;
         this.blue = blue;
      }

      public R getRed() {
         return this.red;
      }

      public G getGreen() {
         return this.green;
      }

      public B getBlue() {
         return this.blue;
      }
   }
}
