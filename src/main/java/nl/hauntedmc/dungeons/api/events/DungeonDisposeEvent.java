package nl.hauntedmc.dungeons.api.events;

import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;

public class DungeonDisposeEvent extends DungeonEvent {
   public DungeonDisposeEvent(AbstractInstance instance) {
      super(instance);
   }
}
