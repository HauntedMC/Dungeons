package nl.hauntedmc.dungeons.util;

import com.google.common.collect.Lists;
import java.io.File;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.blocks.MovingBlock;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event.Result;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.util.BoundingBox;

public final class HelperUtils {
   private static final char SECTION_CHAR = '\u00A7';
   private static final Pattern ANGLE_HEX_PATTERN = Pattern.compile("<(#[a-fA-F0-9]{6})>");
   private static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER = LegacyComponentSerializer.builder()
      .character(SECTION_CHAR)
      .hexColors()
      .useUnusualXRepeatedCharacterHexFormat()
      .build();
   private static final PlainTextComponentSerializer PLAIN_TEXT_COMPONENT_SERIALIZER = PlainTextComponentSerializer.plainText();
   private static final String LEGACY_COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";
   private static final String LEGACY_FORMAT_CODES = "KkLlMmNnOo";

   public static String colorize(String s) {
      if (s == null) {
         return null;
      }

      char[] characters = s.toCharArray();
      for (int i = 0; i < characters.length - 1; i++) {
         if (characters[i] == '&' && LEGACY_COLOR_CODES.indexOf(characters[i + 1]) >= 0) {
            characters[i] = SECTION_CHAR;
            characters[i + 1] = Character.toLowerCase(characters[i + 1]);
         }
      }

      return new String(characters);
   }

   public static String fullColor(String s) {
      return colorize(replaceHexColors(s, ANGLE_HEX_PATTERN));
   }

   public static Component component(String s) {
      return s == null ? Component.empty() : LEGACY_COMPONENT_SERIALIZER.deserialize(fullColor(s));
   }

   public static List<Component> components(List<String> lines) {
      if (lines == null || lines.isEmpty()) {
         return List.of();
      }

      return lines.stream().map(HelperUtils::component).toList();
   }

   public static String serialize(Component component) {
      return component == null ? "" : LEGACY_COMPONENT_SERIALIZER.serialize(component);
   }

   public static String plainText(Component component) {
      return component == null ? "" : PLAIN_TEXT_COMPONENT_SERIALIZER.serialize(component);
   }

   public static String playerDisplayName(Player player) {
      return player == null ? "" : serialize(player.displayName());
   }

   public static String humanize(String value) {
      if (value == null || value.isBlank()) {
         return "";
      }

      String[] parts = value.toLowerCase(Locale.ROOT).split("_");
      StringBuilder output = new StringBuilder();
      for (String part : parts) {
         if (part.isEmpty()) {
            continue;
         }

         if (!output.isEmpty()) {
            output.append(' ');
         }

         output.append(Character.toUpperCase(part.charAt(0)));
         if (part.length() > 1) {
            output.append(part.substring(1));
         }
      }

      return output.toString();
   }

   public static String itemDisplayName(ItemStack item) {
      if (item == null) {
         return "";
      }

      return itemDisplayName(item.getItemMeta(), item.getType());
   }

   public static String itemDisplayName(ItemMeta meta, org.bukkit.Material material) {
      if (meta != null) {
         String displayName = serialize(meta.displayName());
         if (!displayName.isEmpty()) {
            return displayName;
         }

         String itemName = serialize(meta.itemName());
         if (!itemName.isEmpty()) {
            return itemName;
         }
      }

      return humanize(material.name());
   }

   public static double getMaxHealth(Damageable entity) {
      if (entity instanceof LivingEntity living) {
         AttributeInstance attribute = living.getAttribute(Attribute.MAX_HEALTH);
         if (attribute != null) {
            return attribute.getValue();
         }
      }

      return entity.getHealth();
   }

   public static void setMaxHealth(LivingEntity entity, double maxHealth) {
      AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
      if (attribute != null) {
         attribute.setBaseValue(maxHealth);
      }
   }

   public static void showTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
      Title.Times times = Title.Times.times(
         Duration.ofMillis(Math.max(fadeIn, 0) * 50L),
         Duration.ofMillis(Math.max(stay, 0) * 50L),
         Duration.ofMillis(Math.max(fadeOut, 0) * 50L)
      );
      player.showTitle(Title.title(component(title), component(subtitle), times));
   }

   public static void resetTitle(Player player) {
      player.resetTitle();
   }

   public static boolean isInteractionDenied(PlayerInteractEvent event) {
      return event.useInteractedBlock() == Result.DENY || event.useItemInHand() == Result.DENY;
   }

   public static void denyInteraction(PlayerInteractEvent event) {
      event.setUseInteractedBlock(Result.DENY);
      event.setUseItemInHand(Result.DENY);
   }

   public static String getLastColors(String input) {
      if (input == null || input.isEmpty()) {
         return "";
      }

      StringBuilder active = new StringBuilder();
      for (int i = 0; i < input.length() - 1; i++) {
         if (input.charAt(i) != SECTION_CHAR) {
            continue;
         }

         char code = Character.toLowerCase(input.charAt(i + 1));
         if (code == 'x' && i + 13 < input.length()) {
            String hex = input.substring(i, i + 14);
            if (hex.matches("(?i)\u00A7x(\u00A7[0-9a-f]){6}")) {
               active.setLength(0);
               active.append(hex);
               i += 13;
               continue;
            }
         }

         if (isLegacyColorCode(code)) {
            active.setLength(0);
            if (code != 'r') {
               active.append(SECTION_CHAR).append(code);
            }
         } else if (LEGACY_FORMAT_CODES.indexOf(code) >= 0) {
            active.append(SECTION_CHAR).append(code);
         }
      }

      return active.toString();
   }

   public static boolean hasPermission(CommandSender sender, String node) {
      if (!sender.hasPermission("*") && !sender.hasPermission(node)) {
         sender.sendMessage(colorize("&cYou do not have permission to do that. (" + node + ")"));
         return false;
      } else {
         return true;
      }
   }

   public static boolean hasPermissionSilent(CommandSender sender, String node) {
      return sender.hasPermission("*") || sender.hasPermission(node);
   }

   public static Timestamp getFutureTimeInSeconds(int seconds) {
      return new Timestamp(System.currentTimeMillis() + seconds * 1000L);
   }

   public static List<Player> getPlayersWithin(Location loc, double radius, GameMode... gameModes) {
      List<Player> players = new ArrayList<>();
      World world = loc.getWorld();
      if (world != null) {
           for (Entity ent : world.getNearbyEntities(loc, radius, radius, radius, entity -> entity.getType() == EntityType.PLAYER)) {
               if (ent instanceof Player player && Bukkit.getOnlinePlayers().contains(player) && Arrays.asList(gameModes).contains(player.getGameMode())) {
                   players.add(player);
               }
           }

       }
       return players;
   }

   public static org.bukkit.Color hexToColor(String colorStr) {
      return org.bukkit.Color.fromRGB(
         Integer.valueOf(colorStr.substring(1, 3), 16), Integer.valueOf(colorStr.substring(3, 5), 16), Integer.valueOf(colorStr.substring(5, 7), 16)
      );
   }

   public static boolean forceRunCommand(Player player, String fullCommand) {
      boolean success;
      if (Dungeons.inst().getConfig().getBoolean("General.CommandFunctionAsOp", false)) {
         try {
            player.setOp(true);
            success = Bukkit.dispatchCommand(player, fullCommand);
         } finally {
            player.setOp(false);
         }

         return success;
      } else {
         String commandName = fullCommand.split(" ")[0];
         PluginCommand command = Bukkit.getPluginCommand(commandName);
         if (command == null) {
            return false;
         } else if (command.getPermission() == null) {
            return false;
         } else {
            PermissionAttachment attachment = player.addAttachment(Dungeons.inst());

            try {
               attachment.setPermission(command.getPermission(), true);
               success = Bukkit.dispatchCommand(player, fullCommand);
            } finally {
               attachment.unsetPermission(command.getPermission());
            }

            return success;
         }
      }
   }

   public static void displayHelpMenu(CommandSender sender, int page) {
      if (page > 0) {
         page--;
      }

      sender.sendMessage(fullColor("<#9753f5>-= Dungeons - Version " + Dungeons.inst().getVersion() + " =-"));
      List<String> helpInfo = new ArrayList<>();
      boolean dungeonEditPerm = hasPermissionSilent(sender, "dungeons.edit");
      boolean dungeonAdminPerm = hasPermissionSilent(sender, "dungeons.admin");
      if (dungeonEditPerm) {
         helpInfo.add("&a/dungeons banitem &f| &eBans your held item from the dungeon.");
         helpInfo.add("&a/dungeons unbanitem &f| &eUnbans your held item from the dungeon.");
         helpInfo.add("&a/dungeons dungeonitem &f| &eMakes the item in your hand a dungeon item.");
         helpInfo.add("&a/dungeon edit &6<dungeon> &f| &eOpens an edit session of the dungeon.");
         helpInfo.add("&a/dungeon save &f| &eForce-saves the dungeon you're editing. ");
         helpInfo.add("&a/dungeon setlobby &f| &eSets the dungeon's lobby spawn.");
         helpInfo.add("&a/dungeon setspawn &f| &eSets the dungeon's starting spawn.");
         helpInfo.add("&a/dungeon setexit &6<dungeon> &f| &eSets the dungeon's exit location.");
         helpInfo.add("&a/dungeon addkey &6<dungeon> &f| &eMake the held item an access key.");
         helpInfo.add("&a/dungeon removekey &6<dungeon> &f| &eRemove the held item as an access key.");
      }

      if (dungeonAdminPerm) {
         helpInfo.add("&a/dungeon create &6<name> [type] [generator] &f| &eCreates a new blank dungeon.");
         helpInfo.add("&a/dungeon import &6<world> &b[type] &f| &eImports a world as a dungeon.");
         helpInfo.add("&a/dungeon dxlimport &6<dxl map> &f| &eImports from a DXL dungeon.");
         helpInfo.add("&a/dungeon join &6<player> &f| &eSends you to the player's dungeon.");
         helpInfo.add("&a/dungeon kick &6<player> &f| &eKicks the player from the dungeon they're in.");
         helpInfo.add("&a/dungeon reload &b[dungeon] &f| &eReloads the config and/or dungeon.");
         helpInfo.add("&a/dungeon status &b[dungeon] &f| &eShows active dungeon status info.");
         helpInfo.add("&a/dungeon delete &6<dungeon> &f| &eDeletes a dungeon. (Also confirms deletion.)");
      }

      if (hasPermissionSilent(sender, "dungeons.loottables")) {
         helpInfo.add("&a/dungeon loot create &6<name> &f| &eCreates a new loot table.");
         helpInfo.add("&a/dungeon loot edit &6<loot table> &f| &eOpens the loot table editor.");
         helpInfo.add("&a/dungeon loot remove &6<loot table> &f| &eDeletes the loot table.");
      }

      if (hasPermissionSilent(sender, "dungeons.cleansigns")) {
         helpInfo.add("&a/dungeon cleansigns &f| &eRemoves all signs from function locations.");
      }

      if (hasPermissionSilent(sender, "dungeons.functioneditor")) {
         helpInfo.add("&a/dungeon functiontool &f| &eGives you the function builder item.");
      }

      helpInfo.add("&a/dungeon help &b[page number] &f| &eDisplays this handy help menu!");
      helpInfo.add("&a/dungeon leave &f| &eSaves and exits the current dungeon.");
      helpInfo.add("&a/dungeon lives &f| &eTells you how many lives you have left.");
      if (hasPermissionSilent(sender, "dungeons.play")) {
         helpInfo.add("&a/dungeon play &6<dungeon> &f| &eStarts a dungeon for the player.");
      } else if (hasPermissionSilent(sender, "dungeons.play.send")) {
         helpInfo.add("&a/dungeon play &6<dungeon> [player] &f| &eStarts a dungeon for the player.");
      }

      if (hasPermissionSilent(sender, "dungeons.stuck")) {
         if (!Dungeons.inst().isStuckKillsPlayer()) {
            helpInfo.add("&a/dungeon stuck &f| &eSends you to your last checkpoint safely.");
         } else {
            helpInfo.add("&a/dungeon stuck &f| &eForce-kills you so you can respawn.");
         }
      }

      Collections.sort(helpInfo);
      List<List<String>> helpPages = new ArrayList<>(Lists.partition(helpInfo, 6));
      if (page < 0 || page >= helpPages.size()) {
         page = 0;
      }

      for (String line : helpPages.get(page)) {
         sender.sendMessage(fullColor(line));
      }
   }

   public static boolean deleteRecursively(File folder) {
      File[] contents = folder.listFiles();
       if (contents != null) {
           for (File file : contents) {
               deleteRecursively(file);
           }

       }
       return folder.delete();
   }

   public static Location readLocation(ConfigurationSection config) {
      if (config == null) {
         return null;
      } else {
         return new Location(
            null, config.getDouble("x"), config.getDouble("y"), config.getDouble("z"), (float)config.getDouble("yaw"), (float)config.getDouble("pitch")
         );
      }
   }

   public static Location readLocation(ConfigurationSection config, Location def) {
      Location loc = readLocation(config);
      if (loc == null) {
         loc = def;
      }

      return loc;
   }

   public static void writeLocation(String path, ConfigurationSection config, Location loc) {
      if (config != null) {
         if (loc.getWorld() != null) {
            config.set(path + ".world", loc.getWorld().getName());
         }

         config.set(path + ".x", loc.getX());
         config.set(path + ".y", loc.getY());
         config.set(path + ".z", loc.getZ());
         config.set(path + ".pitch", loc.getPitch());
         config.set(path + ".yaw", loc.getYaw());
      }
   }

   public static double addRespectedVelocity(double start, double addition) {
      return MathUtils.isNegative(start) ? start - addition : start + addition;
   }

   private static boolean withinDirection(double angle, double direction, double range) {
      double halfRange = range / 2.0;
      double lowerBound = direction - halfRange;
      double upperBound = direction + halfRange;
      return upperBound > 180.0 ? Math.abs(angle) >= lowerBound : lowerBound < angle && angle <= upperBound;
   }

   public static MovingBlock.Direction getDirectionFromAngle(double vectorAngle, double directionRange) {
      if (withinDirection(vectorAngle, 0.0, directionRange)) {
         return MovingBlock.Direction.NORTH;
      } else if (withinDirection(vectorAngle, 90.0, directionRange)) {
         return MovingBlock.Direction.WEST;
      } else if (withinDirection(vectorAngle, 180.0, directionRange)) {
         return MovingBlock.Direction.SOUTH;
      } else {
         return withinDirection(vectorAngle, -90.0, directionRange) ? MovingBlock.Direction.EAST : MovingBlock.Direction.NORTH;
      }
   }

   public static BoundingBox captureBoundingBox(Location pos1, Location pos2) {
      return new BoundingBox(pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(), pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ());
   }

   public static BoundingBox captureOffsetBoundingBox(Location pos1, Location pos2) {
      BoundingBox area = new BoundingBox(pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(), pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ());
      area.expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
      return area;
   }

   public static boolean compareVars(InstancePlayable instance, String comparison) {
      String parsed = parseVars(instance, comparison);
      if (parsed == null) {
         return false;
      } else {
         Pattern pat = Pattern.compile("([^<=>]*)(>=|<=|=+|>|<)([^<=>]*)");
         Matcher matcher = pat.matcher(parsed);
         matcher.find();
         String var1x = matcher.group(1).trim();
         String var2 = matcher.group(3).trim();
         String var11 = matcher.group(2);
         switch (var11) {
            case "=":
            case "==":
               try {
                  double dVar1x = Double.parseDouble(var1x);
                  double dVar2x = Double.parseDouble(var2);
                  return dVar1x == dVar2x;
               } catch (NumberFormatException var14) {
                  return var1x.equals(var2);
               }
            case ">": {
               double dVar1 = Double.parseDouble(var1x);
               double dVar2 = Double.parseDouble(var2);
               return dVar1 > dVar2;
            }
            case ">=": {
               double dVar1 = Double.parseDouble(var1x);
               double dVar2 = Double.parseDouble(var2);
               return dVar1 >= dVar2;
            }
            case "<": {
               double dVar1 = Double.parseDouble(var1x);
               double dVar2 = Double.parseDouble(var2);
               return dVar1 < dVar2;
            }
            case "<=": {
               double dVar1 = Double.parseDouble(var1x);
               double dVar2 = Double.parseDouble(var2);
               return dVar1 <= dVar2;
            }
            default:
               return false;
         }
      }
   }

   public static String parseVars(InstancePlayable instance, String str) {
      Pattern pat = Pattern.compile("<[^<>]*>");
      Matcher matcher = pat.matcher(str);

      while (matcher.find()) {
         String parsed = parseVar(instance, matcher.group(0));
         str = str.replace(matcher.group(0), Objects.requireNonNullElse(parsed, "null"));
      }

      return str;
   }

   public static String parseVar(InstancePlayable instance, String var) {
      String varName = var.replace("<", "").replace(">", "");
      return instance.getInstanceVariables().getString(varName);
   }

   public static void forceTeleport(Entity ent, Location loc) {
      List<Entity> passengers = new ArrayList<>(ent.getPassengers());

      for (Entity passenger : passengers) {
         ent.removePassenger(passenger);
      }

      ent.teleport(loc, TeleportCause.PLUGIN);

      for (Entity passenger : passengers) {
         ent.addPassenger(passenger);
      }
   }

   public static void releaseSpawnChunk(World world) {
      Location spawn = world.getSpawnLocation();
      world.setChunkForceLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4, false);
   }

   public static Location findSafeLocationInBox(World world, BoundingBox box) {
      int timeout = Dungeons.inst().getConfig().getInt("Generator.Timeout", 5);
      long startTime = System.currentTimeMillis();
      ExecutorService executor = Executors.newSingleThreadExecutor();
      Future<Location> future = executor.submit(() -> {
         while (System.currentTimeMillis() - startTime <= timeout * 1000L) {
            double x = MathUtils.getRandomNumberInRange((int)box.getMinX() + 1, (int)box.getMaxX() - 1);
            double y = box.getMaxY() - 1.0;
            double z = MathUtils.getRandomNumberInRange((int)box.getMinZ() + 1, (int)box.getMaxZ() - 1);
            Location target = new Location(world, x + 0.5, y, z + 0.5);
            if (!target.getChunk().isLoaded()) {
               target.getChunk().load(true);
            }

            BoundingBox ent = new BoundingBox(x - 0.4, y, z - 0.4, x + 0.4, y + 2.0, z + 0.4);
            if (!world.hasCollisionsIn(ent)) {
               for (double ty = y; ty >= box.getMinY(); ty--) {
                  if (world.hasCollisionsIn(ent)) {
                     target.setY(ty + 1.0);
                  }
               }

               return target;
            }
         }

         return null;
      });

      Location loc;
      try {
         loc = future.get(timeout, TimeUnit.SECONDS);
      } catch (InterruptedException | TimeoutException | ExecutionException var9) {
         future.cancel(true);
         loc = null;
      }

      Dungeons.inst().getLogger().info(colorize("&e- Finding valid spawn location took " + (System.currentTimeMillis() - startTime) + "ms -"));
      return loc;
   }

   private static String replaceHexColors(String input, Pattern pattern) {
      if (input == null) {
         return null;
      }

      Matcher matcher = pattern.matcher(input);
      StringBuilder output = new StringBuilder();
      while (matcher.find()) {
         matcher.appendReplacement(output, Matcher.quoteReplacement(toLegacyHex(matcher.group(1))));
      }

      matcher.appendTail(output);
      return output.toString();
   }

   private static String toLegacyHex(String hex) {
      StringBuilder output = new StringBuilder().append(SECTION_CHAR).append('x');
      for (char character : hex.substring(1).toLowerCase(Locale.ROOT).toCharArray()) {
         output.append(SECTION_CHAR).append(character);
      }

      return output.toString();
   }

   private static boolean isLegacyColorCode(char code) {
      return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r';
   }
}
