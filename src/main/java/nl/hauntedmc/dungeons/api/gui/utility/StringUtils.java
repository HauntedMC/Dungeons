package nl.hauntedmc.dungeons.api.gui.utility;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

public class StringUtils {
   private static final Pattern BRACE_HEX_PATTERN = Pattern.compile("\\{(#[a-fA-F0-9]{6})}");
   private static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER = LegacyComponentSerializer.builder()
      .character(ChatColor.COLOR_CHAR)
      .hexColors()
      .useUnusualXRepeatedCharacterHexFormat()
      .build();

   public static String colorize(String s) {
      return s == null ? null : ChatColor.translateAlternateColorCodes('&', s);
   }

   public static String fullColor(String s) {
      return colorize(replaceHexColors(s));
   }

   public static Component component(String s) {
      return s == null ? Component.empty() : LEGACY_COMPONENT_SERIALIZER.deserialize(fullColor(s));
   }

   public static List<Component> components(List<String> lines) {
      return lines.stream().map(StringUtils::component).toList();
   }

   public static String serialize(Component component) {
      return component == null ? "" : LEGACY_COMPONENT_SERIALIZER.serialize(component);
   }

   private static String replaceHexColors(String input) {
      if (input == null) {
         return null;
      }

      Matcher matcher = BRACE_HEX_PATTERN.matcher(input);
      StringBuilder output = new StringBuilder();
      while (matcher.find()) {
         matcher.appendReplacement(output, Matcher.quoteReplacement(toLegacyHex(matcher.group(1))));
      }

      matcher.appendTail(output);
      return output.toString();
   }

   private static String toLegacyHex(String hex) {
      StringBuilder output = new StringBuilder().append(ChatColor.COLOR_CHAR).append('x');
      for (char character : hex.substring(1).toLowerCase(Locale.ROOT).toCharArray()) {
         output.append(ChatColor.COLOR_CHAR).append(character);
      }

      return output.toString();
   }
}
