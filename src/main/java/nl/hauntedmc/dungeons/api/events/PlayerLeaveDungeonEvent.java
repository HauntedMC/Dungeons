package nl.hauntedmc.dungeons.api.events;

import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.api.parents.instances.InstanceEditable;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class PlayerLeaveDungeonEvent extends DungeonEvent implements Cancellable {
   private final DungeonPlayer mPlayer;
   private final Player player;
   boolean editMode;
   private boolean cancelled;

   public PlayerLeaveDungeonEvent(AbstractInstance instance, DungeonPlayer mPlayer) {
      super(instance);
      this.mPlayer = mPlayer;
      this.player = mPlayer.getPlayer();
      this.editMode = instance instanceof InstanceEditable;
   }

   public DungeonPlayer getMPlayer() {
      return this.mPlayer;
   }

   public Player getPlayer() {
      return this.player;
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancelled) {
      this.cancelled = cancelled;
   }
}
