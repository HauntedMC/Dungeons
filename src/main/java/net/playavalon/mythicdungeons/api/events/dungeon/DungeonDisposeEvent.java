package net.playavalon.mythicdungeons.api.events.dungeon;

import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;

public class DungeonDisposeEvent extends DungeonEvent {
   public DungeonDisposeEvent(AbstractInstance instance) {
      super(instance);
   }
}
