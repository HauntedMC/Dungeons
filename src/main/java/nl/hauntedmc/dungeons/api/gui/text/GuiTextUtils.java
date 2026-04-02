package nl.hauntedmc.dungeons.api.gui.text;

import java.util.List;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.util.HelperUtils;

public final class GuiTextUtils {
   private GuiTextUtils() {
   }

   public static String colorize(String s) {
      return HelperUtils.colorize(s);
   }

   public static String fullColor(String s) {
      return LegacyGuiTextSerializer.fullColor(s);
   }

   public static Component component(String s) {
      return LegacyGuiTextSerializer.component(s);
   }

   public static List<Component> components(List<String> lines) {
      return lines.stream().map(GuiTextUtils::component).toList();
   }

   public static String serialize(Component component) {
      return HelperUtils.serialize(component);
   }
}
