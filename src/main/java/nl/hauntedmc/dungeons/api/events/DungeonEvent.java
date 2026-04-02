package nl.hauntedmc.dungeons.api.events;

import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
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

   public static HandlerList getHandlerList() {
      return HANDLERS_LIST;
   }

   @NotNull
   public HandlerList getHandlers() {
      return HANDLERS_LIST;
   }

   public AbstractInstance getInstance() {
      return this.instance;
   }

   public AbstractDungeon getDungeon() {
      return this.dungeon;
   }
}
