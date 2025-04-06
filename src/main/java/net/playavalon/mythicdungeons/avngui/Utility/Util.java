package net.playavalon.mythicdungeons.avngui.Utility;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class Util {
   public static ItemStack getNextButton() {
      return createHeadByTextures(
         StringUtils.colorize("&a&lNext Page"),
         "79f13daf-4884-40ab-8e35-95e472463321",
         "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjgyYWQxYjljYjRkZDIxMjU5YzBkNzVhYTMxNWZmMzg5YzNjZWY3NTJiZTM5NDkzMzgxNjRiYWM4NGE5NmUifX19"
      );
   }

   public static ItemStack getPreviousButton() {
      return createHeadByTextures(
         StringUtils.colorize("&a&lPrevious Page"),
         "5fecc571-bcbb-4aaa-b53c-b5d8715dbe37",
         "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyYTdkNzU1YzYzMzg4ZmYwMTc1MmQ1ZjQ0MTlmYzY0NSJ9fX0="
      );
   }

   public static ItemStack createHeadByTextures(String name, String id, String textures) {
      ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1, (short)3);
      SkullMeta itemMeta = (SkullMeta)item.getItemMeta();
      GameProfile gProfile = new GameProfile(UUID.fromString(id), "");
      gProfile.getProperties().put("texture", new Property("textures", textures));

      try {
         Method mtd = itemMeta.getClass().getDeclaredMethod("setProfile", GameProfile.class);
         mtd.setAccessible(true);
         mtd.invoke(itemMeta, gProfile);
      } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException var7) {
         var7.printStackTrace();
      }

      item.setItemMeta(itemMeta);
      return item;
   }
}
