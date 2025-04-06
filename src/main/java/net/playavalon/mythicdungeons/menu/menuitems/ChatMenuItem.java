package net.playavalon.mythicdungeons.menu.menuitems;

import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerEvent;

public abstract class ChatMenuItem extends MenuItem {
   private boolean cancelled;

   @Override
   public void onSelect(PlayerEvent event) {
      this.cancelled = false;
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      this.onSelect(player);
      if (!this.cancelled) {
         aPlayer.setChatListening(true);
      }
   }

   public abstract void onSelect(Player var1);

   @Override
   public void onChat(AsyncPlayerChatEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      String message = event.getMessage();
      Bukkit.getScheduler().runTask(MythicDungeons.inst(), () -> {
         this.onInput(player, Util.fullColor(message));
         aPlayer.setHotbar(this.menu);
      });
   }

   public abstract void onInput(Player var1, String var2);

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancelled) {
      this.cancelled = cancelled;
   }
}
