package net.playavalon.mythicdungeons.api.events.dungeon;

import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.entity.Player;

public class PlayerFinishDungeonEvent extends DungeonEvent {
   private final MythicPlayer mPlayer;
   private final Player player;

   public PlayerFinishDungeonEvent(InstancePlayable instance, MythicPlayer mPlayer) {
      super(instance);
      this.mPlayer = mPlayer;
      this.player = mPlayer.getPlayer();
   }

   public PlayerFinishDungeonEvent(AbstractDungeon dungeon, MythicPlayer mPlayer) {
      super(dungeon);
      this.mPlayer = mPlayer;
      this.player = mPlayer.getPlayer();
   }

   public MythicPlayer getMPlayer() {
      return this.mPlayer;
   }

   public Player getPlayer() {
      return this.player;
   }
}
