package nl.hauntedmc.dungeons.api.events;

import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import org.bukkit.entity.Player;

public class PlayerFinishDungeonEvent extends DungeonEvent {
   private final DungeonPlayer mPlayer;
   private final Player player;

   public PlayerFinishDungeonEvent(InstancePlayable instance, DungeonPlayer mPlayer) {
      super(instance);
      this.mPlayer = mPlayer;
      this.player = mPlayer.getPlayer();
   }

   public DungeonPlayer getMPlayer() {
      return this.mPlayer;
   }

   public Player getPlayer() {
      return this.player;
   }
}
