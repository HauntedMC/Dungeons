package net.playavalon.mythicdungeons.dungeons.variables;

import java.util.Collections;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

public enum VariableEditMode implements ConfigurationSerializable {
   SET,
   ADD,
   SUBTRACT;

   public static VariableEditMode intToModeType(int index) {
      return switch (index) {
         default -> SET;
         case 1 -> ADD;
         case 2 -> SUBTRACT;
      };
   }

   @NotNull
   public Map<String, Object> serialize() {
      return Collections.singletonMap("value", this.name());
   }

   public static VariableEditMode deserialize(Map<String, Object> map) {
      return valueOf((String)map.get("value"));
   }
}
