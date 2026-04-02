package nl.hauntedmc.dungeons.gui.hotbar.menuitems;

import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

public abstract class ChatMenuItem extends MenuItem {
   private boolean cancelled;

   @Override
   public void onSelect(PlayerEvent event) {
      this.cancelled = false;
      Player player = event.getPlayer();
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      this.onSelect(player);
      if (!this.cancelled) {
         aPlayer.setChatListening(true);
      }
   }

   public abstract void onSelect(Player var1);

   @Override
   public void onChat(Player player, String message) {
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      this.onInput(player, HelperUtils.fullColor(message));
      aPlayer.setHotbar(this.menu);
   }

   public abstract void onInput(Player var1, String var2);

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancelled) {
      this.cancelled = cancelled;
   }
}
