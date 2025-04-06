package net.playavalon.mythicdungeons.api.events.dungeon;

import javax.annotation.Nullable;
import net.playavalon.mythicdungeons.player.Hotbar;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class HotbarSetEvent extends Event implements Cancellable {
   @Nullable
   private final Hotbar oldHotbar;
   private Hotbar newHotbar;
   private final MythicPlayer mythicPlayer;
   private boolean cancelled;
   private static final HandlerList HANDLERS_LIST = new HandlerList();

   public HotbarSetEvent(Hotbar hotbar, MythicPlayer aPlayer) {
      this.oldHotbar = aPlayer.getPreviousHotbar();
      this.newHotbar = hotbar;
      this.mythicPlayer = aPlayer;
   }

   public Player getPlayer() {
      return this.mythicPlayer.getPlayer();
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancel) {
      this.cancelled = cancel;
   }

   @NotNull
   public HandlerList getHandlers() {
      return HANDLERS_LIST;
   }

   public static HandlerList getHandlerList() {
      return HANDLERS_LIST;
   }

   @Nullable
   public Hotbar getOldHotbar() {
      return this.oldHotbar;
   }

   public Hotbar getNewHotbar() {
      return this.newHotbar;
   }

   public void setNewHotbar(Hotbar newHotbar) {
      this.newHotbar = newHotbar;
   }

   public MythicPlayer getMythicPlayer() {
      return this.mythicPlayer;
   }
}
