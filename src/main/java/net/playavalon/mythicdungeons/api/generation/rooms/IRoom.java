package net.playavalon.mythicdungeons.api.generation.rooms;

import java.util.List;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.utility.SimpleLocation;

public interface IRoom {
   List<SimpleLocation> getConnectors();

   List<DungeonRoomContainer> getValidRooms();

   List<DungeonFunction> getFunctions();
}
