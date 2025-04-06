package net.playavalon.mythicdungeons.utility.helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.playavalon.mythicdungeons.MythicDungeons;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LangUtils {
   private static FileConfiguration langYaml;
   private static FileConfiguration jarLang;

   public static void init() {
      langYaml = new YamlConfiguration();
      File langFile = new File(MythicDungeons.inst().getDataFolder(), "lang.yml");
      InputStream jarLangStream = MythicDungeons.inst().getResource("lang.yml");
      if (jarLangStream != null) {
         InputStreamReader reader = new InputStreamReader(jarLangStream);
         jarLang = YamlConfiguration.loadConfiguration(reader);
      }

      try {
         if (!langFile.exists()) {
            MythicDungeons.inst().saveResource("lang.yml", false);
         }

         langYaml.load(langFile);
      } catch (InvalidConfigurationException | IOException var3) {
         MythicDungeons.inst().getLogger().info(Util.colorize("&cERROR :: Could not load lang file!!"));
         var3.printStackTrace();
      }
   }

   public static void sendMessage(CommandSender target, String langNamespace) {
      String message = langYaml.getString(langNamespace);
      if (message != null && !message.isEmpty()) {
         String prefix = langYaml.getString("general.prefix", "<#9753f5>[Dungeons]");
         String msg;
         if (prefix.isEmpty()) {
            msg = Util.fullColor(message);
         } else {
            msg = Util.fullColor(prefix + " " + message);
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
            message = Util.fullColor(message);
         } else {
            message = Util.fullColor(prefix + " " + message);
         }

         target.sendMessage(Util.fullColor(langYaml.getString("general.prefix", "<#9753f5>[Dungeons]") + " " + message));
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

         return Util.fullColor(message);
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

         return Util.fullColor(message);
      }
   }

   public static List<String> getMessageList(String langNamespace) {
      List<String> messages = new ArrayList<>();

      for (String msg : langYaml.getStringList(langNamespace)) {
         messages.add(Util.fullColor(msg));
      }

      return messages;
   }

   public static Locale getLocale(String path) {
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

      File langFile = new File(MythicDungeons.inst().getDataFolder(), "lang.yml");

      try {
         langYaml.save(langFile);
      } catch (IOException var2) {
         MythicDungeons.inst().getLogger().info(Util.colorize("&cERROR :: Could not save lang file!"));
         var2.printStackTrace();
      }
   }
}
