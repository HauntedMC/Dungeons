package net.playavalon.mythicdungeons.api.events.dungeon;

import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public abstract class DungeonEvent extends Event {
   protected AbstractInstance instance;
   protected AbstractDungeon dungeon;
   private static final HandlerList HANDLERS_LIST = new HandlerList();

   public DungeonEvent(AbstractInstance instance) {
      this.instance = instance;
      this.dungeon = instance.getDungeon();
   }

   public DungeonEvent(AbstractDungeon dungeon) {
      this.dungeon = dungeon;
   }

   @NotNull
   public HandlerList getHandlers() {
      return HANDLERS_LIST;
   }

   public static HandlerList getHandlerList() {
      return HANDLERS_LIST;
   }

   public AbstractInstance getInstance() {
      return this.instance;
   }

   public AbstractDungeon getDungeon() {
      return this.dungeon;
   }
}
