package net.playavalon.mythicdungeons.utility.helpers;

import java.util.UUID;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class SkullUtil {

   public static ItemStack getNextButton() {
      String displayName = net.playavalon.mythicdungeons.avngui.Utility.StringUtils.colorize("&a&lNext Page");
      String textures = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjgyYWQxYjljYjRkZDIxMjU5YzBkNzVhYTMxNWZmMzg5YzNjZWY3NTJiZTM5NDkzMzgxNjRiYWM4NGE5NmUifX19";
      return createHeadByTextures(displayName, textures);
   }

   public static ItemStack getPreviousButton() {
      String displayName = net.playavalon.mythicdungeons.avngui.Utility.StringUtils.colorize("&a&lPrevious Page");
      String textures = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyYTdkNzU1YzYzMzg4ZmYwMTc1MmQ1ZjQ0MTlmYzY0NSJ9fX0=";
      return createHeadByTextures(displayName, textures);
   }

   public static ItemStack createHeadByTextures(String name, String textures) {
      ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1, (short) 3);
      if (textures == null || textures.isEmpty()) {
         return head;
      }
      SkullMeta meta = (SkullMeta) head.getItemMeta();
      PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
      profile.setProperty(new ProfileProperty("textures", textures));
      meta.setPlayerProfile(profile);
      meta.setDisplayName(name);
      head.setItemMeta(meta);
      return head;
   }
}
