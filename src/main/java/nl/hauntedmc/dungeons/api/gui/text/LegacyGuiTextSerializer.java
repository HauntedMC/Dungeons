package nl.hauntedmc.dungeons.api.gui.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.util.HelperUtils;

final class LegacyGuiTextSerializer {
   private static final Pattern BRACE_HEX_PATTERN = Pattern.compile("\\{(#[a-fA-F0-9]{6})}");

   private LegacyGuiTextSerializer() {
   }

   static String fullColor(String input) {
      return HelperUtils.fullColor(convertHexBraces(input));
   }

   static Component component(String input) {
      return HelperUtils.component(convertHexBraces(input));
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
