package nl.hauntedmc.dungeons.api.gui.utility;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.util.HelperUtils;

public class StringUtils {
   private static final Pattern BRACE_HEX_PATTERN = Pattern.compile("\\{(#[a-fA-F0-9]{6})}");

   public static String colorize(String s) {
      return HelperUtils.colorize(s);
   }

   public static String fullColor(String s) {
      return HelperUtils.fullColor(convertHexBraces(s));
   }

   public static Component component(String s) {
      return HelperUtils.component(convertHexBraces(s));
   }

   public static List<Component> components(List<String> lines) {
      return lines.stream().map(StringUtils::component).toList();
   }

   public static String serialize(Component component) {
      return HelperUtils.serialize(component);
   }

   private static String convertHexBraces(String input) {
      if (input == null) {
         return null;
      }

      Matcher matcher = BRACE_HEX_PATTERN.matcher(input);
      StringBuilder output = new StringBuilder();
      while (matcher.find()) {
         matcher.appendReplacement(output, Matcher.quoteReplacement("<" + matcher.group(1) + ">"));
      }

      matcher.appendTail(output);
      return output.toString();
   }
}
