package net.playavalon.mythicdungeons.api.events.dungeon;

import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.jetbrains.annotations.Nullable;

public class TriggerFireEvent extends DungeonEvent implements Cancellable {
   private MythicPlayer dPlayer;
   private final DungeonTrigger trigger;
   private boolean cancelled;

   public TriggerFireEvent(InstancePlayable instance, DungeonTrigger trigger) {
      super(instance);
      this.trigger = trigger;
      this.instance = instance;
   }

   public TriggerFireEvent(MythicPlayer dPlayer, DungeonTrigger trigger) {
      super(dPlayer.getInstance());
      this.dPlayer = dPlayer;
      this.trigger = trigger;
      this.cancelled = false;
   }

   @Nullable
   public Player getPlayer() {
      return this.dPlayer == null ? null : this.dPlayer.getPlayer();
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancel) {
      this.cancelled = cancel;
   }

   public MythicPlayer getDPlayer() {
      return this.dPlayer;
   }

   public DungeonTrigger getTrigger() {
      return this.trigger;
   }
}
