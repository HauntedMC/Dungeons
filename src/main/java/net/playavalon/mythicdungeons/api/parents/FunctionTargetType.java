package net.playavalon.mythicdungeons.api.parents;

import java.util.Collections;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

public enum FunctionTargetType implements ConfigurationSerializable {
   NONE(0, "None"),
   PLAYER(1, "Player"),
   PARTY(2, "Party"),
   ROOM(3, "Players in Room");

   private final int index;
   private final String display;

   private FunctionTargetType(int index, String display) {
      this.index = index;
      this.display = display;
   }

   public static FunctionTargetType intToTargetType(int index) {
      return switch (index) {
         default -> NONE;
         case 1 -> PLAYER;
         case 2 -> PARTY;
         case 3 -> ROOM;
      };
   }

   @NotNull
   public Map<String, Object> serialize() {
      return Collections.singletonMap("value", this.name());
   }

   public static FunctionTargetType deserialize(Map<String, Object> map) {
      return valueOf((String)map.get("value"));
   }

   public int getIndex() {
      return this.index;
   }

   public String getDisplay() {
      return this.display;
   }
}
