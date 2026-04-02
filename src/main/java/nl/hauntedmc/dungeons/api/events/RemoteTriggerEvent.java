package nl.hauntedmc.dungeons.api.events;

import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoteTriggerEvent extends DungeonEvent implements Cancellable {
   private final String triggerName;
   private final DungeonTrigger trigger;
   private double range;
   @Nullable
   private final Location origin;
   @Nullable
   private final DungeonPlayer dungeonPlayer;
   private boolean cancelled;

   public RemoteTriggerEvent(
      @NotNull String triggerName,
      DungeonTrigger trigger,
      @NotNull InstancePlayable instance,
      double range,
      @Nullable Location origin,
      @Nullable DungeonPlayer triggerPlayer
   ) {
      super(instance);
      this.triggerName = triggerName;
      this.trigger = trigger;
      this.range = range;
      this.origin = origin;
      this.dungeonPlayer = triggerPlayer;
   }

   @Nullable
   public Player getPlayer() {
      return this.dungeonPlayer == null ? null : this.dungeonPlayer.getPlayer();
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancel) {
      this.cancelled = cancel;
   }

   public String getTriggerName() {
      return this.triggerName;
   }

   public DungeonTrigger getTrigger() {
      return this.trigger;
   }

   public double getRange() {
      return this.range;
   }

   public void setRange(double range) {
      this.range = range;
   }

   @Nullable
   public Location getOrigin() {
      return this.origin;
   }

   @Nullable
   public DungeonPlayer getDungeonPlayer() {
      return this.dungeonPlayer;
   }
}
