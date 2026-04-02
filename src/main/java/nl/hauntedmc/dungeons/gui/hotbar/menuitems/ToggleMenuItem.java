package nl.hauntedmc.dungeons.gui.hotbar.menuitems;

import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

public abstract class ToggleMenuItem extends MenuItem {
   @Override
   public void onSelect(PlayerEvent event) {
      Player player = event.getPlayer();
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      this.onSelect(player);
      aPlayer.setHotbar(this.menu);
   }

   public abstract void onSelect(Player var1);
}
