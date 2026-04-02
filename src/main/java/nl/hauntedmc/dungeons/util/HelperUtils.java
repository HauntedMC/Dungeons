package nl.hauntedmc.dungeons.util;

import com.google.common.collect.Lists;
import io.papermc.paper.entity.TeleportFlag.EntityState;
import java.awt.Color;
import java.io.File;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.blocks.MovingBlock;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.entity.ParticleUtils;
import nl.hauntedmc.dungeons.util.file.ColorUtils;
import nl.hauntedmc.dungeons.util.file.DXLUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.util.BoundingBox;

public final class HelperUtils {
   public static String colorize(String s) {
      return s == null ? null : ChatColor.translateAlternateColorCodes('&', s);
   }

   public static String fullColor(String s) {
      StringBuilder sb = new StringBuilder();
      String[] strs = s.split("(?=(<#[a-fA-F0-9]*>))");
      Pattern pat = Pattern.compile("(<(#[a-fA-F0-9]*)>)(.*)");

      for (String str : strs) {
         Matcher matcher = pat.matcher(str);
         if (matcher.find()) {
            try {
               ChatColor.class.getDeclaredMethod("of", String.class);
               sb.append(ChatColor.of(matcher.group(2)));
            } catch (NoSuchMethodException var13) {
               String hex = matcher.group(2);
               Color color = Color.decode(hex);
               ChatColor cColor = ColorUtils.fromRGB(color.getRed(), color.getGreen(), color.getBlue());
               sb.append(cColor);
            }

            sb.append(matcher.group(3));
         } else {
            sb.append(str);
         }
      }

      return colorize(sb.toString());
   }

   public static boolean hasPermission(CommandSender sender, String node) {
      if (!sender.hasPermission("*") && !sender.hasPermission(node)) {
         sender.sendMessage(ChatColor.RED + "You do not have permission to do that. (" + node + ")");
         return false;
      } else {
         return true;
      }
   }

   public static boolean hasPermissionSilent(CommandSender sender, String node) {
      return sender.hasPermission("*") || sender.hasPermission(node);
   }

   @Deprecated(
      forRemoval = true
   )
   public static double round(double value, int places) {
      return MathUtils.round(value, places);
   }

   @Deprecated(
      forRemoval = true
   )
   public static int getRandomNumberInRange(int min, int max) {
      return MathUtils.getRandomNumberInRange(min, max);
   }

   @Deprecated(
      forRemoval = true
   )
   public static double getRandomDoubleInRange(double min, double max) {
      return MathUtils.getRandomDoubleInRange(min, max);
   }

   @Deprecated(
      forRemoval = true
   )
   public static boolean getRandomBoolean(double chance) {
      return MathUtils.getRandomBoolean(chance);
   }

   @Deprecated(
      forRemoval = true
   )
   public static boolean isNegative(double d) {
      return MathUtils.isNegative(d);
   }

   public static Timestamp getFutureTimeInSeconds(int seconds) {
      return new Timestamp(System.currentTimeMillis() + seconds * 1000L);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void giveOrDrop(Player player, ItemStack item) {
      ItemUtils.giveOrDrop(player, item);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void giveOrDrop(Player player, ItemStack... items) {
      ItemUtils.giveOrDrop(player, items);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void giveOrDropSilently(Player player, ItemStack item) {
      ItemUtils.giveOrDropSilently(player, item);
   }

   @Deprecated(
      forRemoval = true
   )
   public static ItemStack getFunctionTool() {
      return ItemUtils.getFunctionTool();
   }

   @Deprecated(
      forRemoval = true
   )
   public static boolean isFunctionTool(ItemStack item) {
      return ItemUtils.isFunctionTool(item);
   }

   @Deprecated(
      forRemoval = true
   )
   public static ItemStack getRoomTool() {
      return ItemUtils.getRoomTool();
   }

   @Deprecated(
      forRemoval = true
   )
   public static boolean isRoomTool(ItemStack item) {
      return ItemUtils.isRoomTool(item);
   }

   @Deprecated(
      forRemoval = true
   )
   public static ItemStack getDefaultKeyItem() {
      return ItemUtils.getDefaultKeyItem();
   }

   @Deprecated(
      forRemoval = true
   )
   public static boolean verifyKeyItem(ItemStack item) {
      return ItemUtils.verifyKeyItem(item);
   }

   @Deprecated(
      forRemoval = true
   )
   public static boolean verifyDungeonItem(ItemStack item) {
      return ItemUtils.verifyDungeonItem(item);
   }

   @Deprecated(
      forRemoval = true
   )
   public static String getItemDisplayName(ItemStack item) {
      return ItemUtils.getItemDisplayName(item);
   }

   @Deprecated(
      forRemoval = true
   )
   public static boolean isItemBanned(AbstractDungeon dungeon, ItemStack itemStack) {
      return ItemUtils.isItemBanned(dungeon, itemStack);
   }

   @Deprecated(
      forRemoval = true
   )
   public static ItemStack getBlockedMenuItem() {
      return ItemUtils.getBlockedMenuItem();
   }

   @Deprecated(
      forRemoval = true
   )
   public static ItemStack getBlockedMenuItem(Material mat) {
      return ItemUtils.getBlockedMenuItem(mat);
   }

   @Deprecated(
      forRemoval = true
   )
   public static ItemStack skullFromName(ItemStack item, String name) {
      return ItemUtils.skullFromName(item, name);
   }

   @Deprecated(
      forRemoval = true
   )
   public static ItemStack getPlayerHead(Player player) {
      return ItemUtils.getPlayerHead(player);
   }

   @Deprecated(
      forRemoval = true
   )
   public static boolean convertFromDXL(String worldName) {
      return DXLUtils.convertFromDXL(worldName);
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

   @Deprecated(
      forRemoval = true
   )
   public static Optional<Integer> readIntegerInput(CommandSender sender, String string) {
      return StringUtils.readIntegerInput(sender, string);
   }

   @Deprecated(
      forRemoval = true
   )
   public static Optional<Double> readDoubleInput(Player player, String string) {
      return StringUtils.readDoubleInput(player, string);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void sendClickableLink(Player player, String message, String url) {
      StringUtils.sendClickableLink(player, message, url);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void sendClickableCommand(Player player, String message, String command) {
      StringUtils.sendClickableCommand(player, message, command);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void sendReadyCheckMessage(Player player) {
      StringUtils.sendReadyCheckMessage(player);
   }

   @Deprecated(
      forRemoval = true
   )
   public static String formatDate(Date date) {
      return StringUtils.formatDate(date);
   }

   @Deprecated(
      forRemoval = true
   )
   public static Date convertDurationString(String durString) {
      return StringUtils.convertDurationString(durString);
   }

   @Deprecated(
      forRemoval = true
   )
   public static String formatDuration(long durationInMillis) {
      return StringUtils.formatDuration(durationInMillis);
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

   @Deprecated(
      forRemoval = true
   )
   public static void displayBoundingBox(Player to, int ticks, BoundingBox... boxes) {
      ParticleUtils.displayBoundingBox(to, ticks, boxes);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void displayBoundingBox(Player to, int ticks, Particle particle, BoundingBox... boxes) {
      ParticleUtils.displayBoundingBox(to, ticks, particle, boxes);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void displayBoundingBox(Player to, BoundingBox... boxes) {
      ParticleUtils.displayBoundingBox(to, boxes);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void displayStructureBox(Player to, BoundingBox... boxes) {
      ParticleUtils.displayStructureBox(to, boxes);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void clearStructureBox(World world, BoundingBox... boxes) {
      ParticleUtils.clearStructureBox(world, boxes);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void clearStructureBox(Player player, BoundingBox... boxes) {
      ParticleUtils.clearStructureBox(player, boxes);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void displayBoundingBox(Player to, Particle particle, BoundingBox... boxes) {
      ParticleUtils.displayBoundingBox(to, particle, boxes);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void displayBoundingBox(Player to, Particle particle, DustOptions opt, BoundingBox... boxes) {
      ParticleUtils.displayBoundingBox(to, particle, opt, boxes);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void drawLine(Player to, Location pos1, Location pos2, Particle particle) {
      ParticleUtils.drawLine(to, pos1, pos2, particle);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void drawLine(Player to, Location pos1, Location pos2, Particle particle, DustOptions opt) {
      ParticleUtils.drawLine(to, pos1, pos2, particle, opt);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void drawOutline3D(Player to, BoundingBox region, Particle particle) {
      ParticleUtils.drawOutline3D(to, region, particle);
   }

   @Deprecated(
      forRemoval = true
   )
   public static void drawOutline3D(Player to, BoundingBox region, Particle particle, DustOptions opt) {
      ParticleUtils.drawOutline3D(to, region, particle, opt);
   }

   @Deprecated(
      forRemoval = true
   )
   public static Particle getVersionParticle(String name) {
      return ParticleUtils.getVersionParticle(name);
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
      if (Dungeons.inst().isSupportsTeleportFlags()) {
         if (Dungeons.inst().isSupportsTeleportAsync()) {
            ent.teleportAsync(loc, TeleportCause.PLUGIN, EntityState.RETAIN_PASSENGERS);
         } else {
            ent.teleport(loc, TeleportCause.PLUGIN, EntityState.RETAIN_PASSENGERS);
         }
      } else {
         List<Entity> passengers = ent.getPassengers();

         for (Entity passenger : passengers) {
            ent.removePassenger(passenger);
         }

         ent.teleportAsync(loc);

         for (Entity passenger : passengers) {
            ent.addPassenger(passenger);
         }
      }
   }

   public static Enchantment getVersionEnchantment(String name) {
      Enchantment particle = Enchantment.getByName(name);
      if (particle == null) {
         String var2 = name.toUpperCase();
          particle = switch (var2) {
              case "UNBREAKING" -> Enchantment.getByName("DURABILITY");
              case "AQUA_AFFINITY" -> Enchantment.getByName("WATER_WORKER");
              default -> Enchantment.MENDING;
          };
      }

      return particle;
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

}
