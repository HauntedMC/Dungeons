package net.playavalon.mythicdungeons.api.events.dungeon;

import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.api.parents.instances.InstanceEditable;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class PlayerLeaveDungeonEvent extends DungeonEvent implements Cancellable {
   private final MythicPlayer mPlayer;
   private final Player player;
   boolean editMode;
   private boolean cancelled;

   public PlayerLeaveDungeonEvent(AbstractInstance instance, MythicPlayer mPlayer) {
      super(instance);
      this.mPlayer = mPlayer;
      this.player = mPlayer.getPlayer();
      this.editMode = instance instanceof InstanceEditable;
   }

   public MythicPlayer getMPlayer() {
      return this.mPlayer;
   }

   public Player getPlayer() {
      return this.player;
   }

   public boolean isEditMode() {
      return this.editMode;
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancelled) {
      this.cancelled = cancelled;
   }
}
