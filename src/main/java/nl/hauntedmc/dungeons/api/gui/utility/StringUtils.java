package nl.hauntedmc.dungeons.api.gui.utility;

import java.util.List;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.api.gui.text.GuiTextUtils;

@Deprecated(forRemoval = false)
public class StringUtils {
   public static String colorize(String s) {
      return GuiTextUtils.colorize(s);
   }

   public static String fullColor(String s) {
      return GuiTextUtils.fullColor(s);
   }

   public static Component component(String s) {
      return GuiTextUtils.component(s);
   }

   public static List<Component> components(List<String> lines) {
      return GuiTextUtils.components(lines);
   }

   public static String serialize(Component component) {
      return GuiTextUtils.serialize(component);
   }
}
