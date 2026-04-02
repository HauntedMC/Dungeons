package nl.hauntedmc.dungeons.util.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LangUtils {
   private static FileConfiguration langYaml;
   private static FileConfiguration jarLang;

   public static void init() {
      langYaml = new YamlConfiguration();
      File langFile = new File(Dungeons.inst().getDataFolder(), "lang.yml");
      InputStream jarLangStream = Dungeons.inst().getResource("lang.yml");
      if (jarLangStream != null) {
         InputStreamReader reader = new InputStreamReader(jarLangStream);
         jarLang = YamlConfiguration.loadConfiguration(reader);
      }

      try {
         if (!langFile.exists()) {
            Dungeons.inst().saveResource("lang.yml", false);
         }

         langYaml.load(langFile);
      } catch (InvalidConfigurationException | IOException var3) {
         Dungeons.inst().getLogger().info(HelperUtils.colorize("&cERROR :: Could not load lang file!!"));
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var3.getMessage());
      }
   }

   public static void sendMessage(CommandSender target, String langNamespace) {
      String message = langYaml.getString(langNamespace);
      if (message != null && !message.isEmpty()) {
         String prefix = langYaml.getString("general.prefix", "<#9753f5>[Dungeons]");
         String msg;
         if (prefix.isEmpty()) {
            msg = HelperUtils.fullColor(message);
         } else {
            msg = HelperUtils.fullColor(prefix + " " + message);
         }

         target.sendMessage(msg);
      }
   }

   public static void sendMessage(CommandSender target, String langNamespace, String... args) {
      String message = langYaml.getString(langNamespace);
      if (message != null && !message.isEmpty()) {
         String prefix = langYaml.getString("general.prefix", "<#9753f5>[Dungeons]");
         int i = 1;

         for (String arg : args) {
            message = message.replace("$" + i, arg);
            i++;
         }

         if (prefix.isEmpty()) {
            message = HelperUtils.fullColor(message);
         } else {
            message = HelperUtils.fullColor(prefix + " " + message);
         }

         target.sendMessage(HelperUtils.fullColor(langYaml.getString("general.prefix", "<#9753f5>[Dungeons]") + " " + message));
      }
   }

   public static String getMessage(String langNamespace) {
      return getMessage(langNamespace, true);
   }

   public static String getMessage(String langNamespace, boolean prefixed) {
      String message = langYaml.getString(langNamespace);
      if (message == null) {
         return "";
      } else {
         if (prefixed) {
            message = langYaml.getString("general.prefix", "<#9753f5>[Dungeons]") + " " + message;
         }

         return HelperUtils.fullColor(message);
      }
   }

   public static String getMessage(String langNamespace, String... args) {
      return getMessage(langNamespace, true, args);
   }

   public static String getMessage(String langNamespace, boolean prefixed, String... args) {
      String message = langYaml.getString(langNamespace);
      if (message == null) {
         return "";
      } else {
         int i = 1;

         for (String arg : args) {
            message = message.replace("$" + i, arg);
            i++;
         }

         if (prefixed) {
            message = langYaml.getString("general.prefix", "<#9753f5>[Dungeons]") + " " + message;
         }

         return HelperUtils.fullColor(message);
      }
   }

   public static List<String> getMessageList(String langNamespace) {
      List<String> messages = new ArrayList<>();

      for (String msg : langYaml.getStringList(langNamespace)) {
         messages.add(HelperUtils.fullColor(msg));
      }

      return messages;
   }

   public static Locale getLocale() {
      Locale locale = Locale.forLanguageTag(langYaml.getString("", "en-US"));
      if (locale == null) {
         locale = Locale.ENGLISH;
      }

      return locale;
   }

   public static void saveMissingValues() {
      for (String key : jarLang.getKeys(true)) {
         if (!langYaml.contains(key)) {
            langYaml.set(key, jarLang.get(key));
         }
      }

      File langFile = new File(Dungeons.inst().getDataFolder(), "lang.yml");

      try {
         langYaml.save(langFile);
      } catch (IOException var2) {
         Dungeons.inst().getLogger().info(HelperUtils.colorize("&cERROR :: Could not save lang file!"));
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var2.getMessage());
      }
   }
}
