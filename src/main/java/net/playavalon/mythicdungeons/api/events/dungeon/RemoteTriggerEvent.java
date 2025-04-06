package net.playavalon.mythicdungeons.api.events.dungeon;

import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.player.MythicPlayer;
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
   private Location origin;
   @Nullable
   private MythicPlayer mythicPlayer;
   private boolean cancelled;

   public RemoteTriggerEvent(@NotNull String triggerName, DungeonTrigger trigger, @NotNull InstancePlayable instance) {
      this(triggerName, trigger, instance, 0.0, null);
   }

   public RemoteTriggerEvent(@NotNull String triggerName, DungeonTrigger trigger, @NotNull InstancePlayable instance, double range, @Nullable Location origin) {
      this(triggerName, trigger, instance, range, origin, null);
   }

   public RemoteTriggerEvent(
      @NotNull String triggerName,
      DungeonTrigger trigger,
      @NotNull InstancePlayable instance,
      double range,
      @Nullable Location origin,
      @Nullable MythicPlayer triggerPlayer
   ) {
      super(instance);
      this.triggerName = triggerName;
      this.trigger = trigger;
      this.range = range;
      this.origin = origin;
      this.mythicPlayer = triggerPlayer;
   }

   @Nullable
   public Player getPlayer() {
      return this.mythicPlayer == null ? null : this.mythicPlayer.getPlayer();
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
   public MythicPlayer getMythicPlayer() {
      return this.mythicPlayer;
   }
}
