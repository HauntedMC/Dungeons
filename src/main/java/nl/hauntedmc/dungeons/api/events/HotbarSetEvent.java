package nl.hauntedmc.dungeons.api.events;

import nl.hauntedmc.dungeons.player.DungeonPlayerHotbar;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class HotbarSetEvent extends Event implements Cancellable {
    private final DungeonPlayerHotbar newDungeonPlayerHotbar;
   private final DungeonPlayer dungeonPlayer;
   private boolean cancelled;
   private static final HandlerList HANDLERS_LIST = new HandlerList();

   public HotbarSetEvent(DungeonPlayerHotbar dungeonPlayerHotbar, DungeonPlayer aPlayer) {
      this.newDungeonPlayerHotbar = dungeonPlayerHotbar;
      this.dungeonPlayer = aPlayer;
   }

   public Player getPlayer() {
      return this.dungeonPlayer.getPlayer();
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancel) {
      this.cancelled = cancel;
   }

   public static HandlerList getHandlerList() {
      return HANDLERS_LIST;
   }

   @NotNull
   public HandlerList getHandlers() {
      return HANDLERS_LIST;
   }

   public DungeonPlayerHotbar getNewHotbar() {
      return this.newDungeonPlayerHotbar;
   }

   public DungeonPlayer getDungeonPlayer() {
      return this.dungeonPlayer;
   }
}
