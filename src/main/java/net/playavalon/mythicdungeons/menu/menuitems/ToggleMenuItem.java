package net.playavalon.mythicdungeons.menu.menuitems;

import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

public abstract class ToggleMenuItem extends MenuItem {
   @Override
   public void onSelect(PlayerEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      this.onSelect(player);
      aPlayer.setHotbar(this.menu);
   }

   public abstract void onSelect(Player var1);
}
