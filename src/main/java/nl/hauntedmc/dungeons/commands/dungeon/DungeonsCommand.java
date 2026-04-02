package nl.hauntedmc.dungeons.commands.dungeon;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map.Entry;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.generation.rooms.InstanceRoom;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.api.parents.instances.InstanceEditable;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.api.gui.GUIAPI;
import nl.hauntedmc.dungeons.commands.Command;
import nl.hauntedmc.dungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import nl.hauntedmc.dungeons.dungeons.instancetypes.play.InstanceProcedural;
import nl.hauntedmc.dungeons.dungeons.rewards.LootTable;
import nl.hauntedmc.dungeons.managers.DungeonTypeManager;
import nl.hauntedmc.dungeons.managers.LootTableManager;
import nl.hauntedmc.dungeons.managers.QueueManager;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.DXLUtils;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.World.Environment;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

public class DungeonsCommand extends Command<Dungeons> {
   public DungeonsCommand(Dungeons plugin, String command) {
      super(plugin, command);
   }

   @Override
   protected boolean onCommand(CommandSender sender, String[] args) {
      if (args.length == 0) {
         sender.sendMessage(HelperUtils.fullColor("<#9753f5>-= Dungeons - Version " + this.getPlugin().getVersion() + "=-"));
         return true;
      } else {
         LootTableManager lootTableManager = this.getPlugin().getLootTableManager();
         GUIAPI guiApi = this.getPlugin().getGuiApi();
         QueueManager queueManager = this.getPlugin().getQueueManager();
         String var7 = args[0];
          return switch (var7) {
              case "help" -> handleHelpCommand(sender, args);
              case "loot" -> handleLootCommand(sender, args, lootTableManager, guiApi);
              case "play" -> this.handlePlayCommand(sender, args, queueManager);
              case "stuck" -> this.handleStuckCommand(sender, args);
              case "lives" -> this.handleLivesCommand(sender);
              case "join" -> this.handleJoinCommand(sender, args);
              case "leave" -> this.handleLeaveCommand(sender);
              case "kick" -> this.handleKickCommand(sender, args);
              case "status" -> this.handleStatusCommand(sender, args);
              case "reload" -> this.handleReloadCommand(sender, args);
              case "create" -> this.handleCreateCommand(sender, args);
              case "edit" -> this.handleEditCommand(sender, args);
              case "delete" -> this.handleDeleteCommand(sender, args);
              case "addkey" -> this.handleAddKeyCommand(sender, args);
              case "removekey" -> this.handleRemoveKeyCommand(sender, args);
              case "clearkeys" -> this.handleRemoveAllkeysCommand(sender, args);
              case "setlobby" -> this.handleSetLobbyCommand(sender, args);
              case "setspawn" -> this.handleSetSpawnCommand(sender, args);
              case "setexit" -> this.handleSetExitCommand(sender, args);
              case "banitem" -> this.handleBanItemCommand(sender, args);
              case "unbanitem" -> this.handleUnbanItemCommand(sender, args);
              case "dungeonitem" -> this.handleDungeonItemCommand(sender, args);
              case "functiontool" -> this.handleFunctionToolCommand(sender);
              case "roomtool" -> this.handleRoomToolCommand(sender);
              case "save" -> this.handleSaveCommand(sender);
              case "import" -> this.handleImportCommand(sender, args);
              case "dxlimport" -> handleDXLImportCommand(sender, args);
              case "cleansigns" -> this.handleCleanSignsCommand(sender, args);
              case "setcooldown" -> this.handleSetCooldownCommand(sender, args);
              case "testdoor" -> handleTestDoorCommand(sender, args);
              case "testalldoors" -> handleTestAllDoorsCommand(sender, args);
              case "getmap" -> handleMapCommand(sender, args);
              default -> true;
          };
      }
   }

   @Override
   protected List<String> onTabComplete(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return null;
      } else {
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         List<String> options = new ArrayList<>();
         if (args.length == 1) {
            if (HelperUtils.hasPermissionSilent(sender, "dungeons.play")) {
               options.add("play");
            }

            if (HelperUtils.hasPermissionSilent(sender, "dungeons.stuck")) {
               options.add("stuck");
            }

            options.add("leave");
            options.add("help");

             if (HelperUtils.hasPermissionSilent(sender, "dungeons.lives")) {
                 options.add("lives");
             }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.edit")) {
               options.add("edit");
               options.add("addkey");
               options.add("removekey");
               options.add("clearkeys");
               options.add("setexit");
               if (aPlayer.isEditMode()) {
                  if (HelperUtils.hasPermissionSilent(player, "dungeons.functioneditor")) {
                     options.add("functiontool");
                     options.add("roomtool");
                  }

                  options.add("setlobby");
                  options.add("setspawn");
                  options.add("banitem");
                  options.add("unbanitem");
                  options.add("save");
               }
            }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.loottables")) {
               options.add("loot");
            }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.admin")) {
               options.add("join");
               options.add("status");
               options.add("reload");
               options.add("create");
               options.add("import");
               options.add("dxlimport");
               options.add("delete");
               if (aPlayer.isEditMode()) {
                  options.add("cleansigns");
               }
            }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.setcooldown")) {
               options.add("setcooldown");
            }
         }

         if (args.length == 2) {
            if (args[0].equalsIgnoreCase("help")) {
               options.add("<help page>");
            }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.edit")) {
               if (args[0].equalsIgnoreCase("edit")) {
                  for (AbstractDungeon dungeon : Dungeons.inst().getDungeonManager().getAll()) {
                     String namespace = dungeon.getWorldName().toLowerCase();
                     if (namespace.contains(args[1].toLowerCase())) {
                        options.add(dungeon.getWorldName());
                     }
                  }
               }

               if (args[0].equalsIgnoreCase("addkey")) {
                  for (AbstractDungeon dungeonx : Dungeons.inst().getDungeonManager().getAll()) {
                     String namespace = dungeonx.getWorldName().toLowerCase();
                     if (namespace.contains(args[1].toLowerCase())) {
                        options.add(dungeonx.getWorldName());
                     }
                  }
               }

               if (args[0].equalsIgnoreCase("clearkeys")) {
                  for (AbstractDungeon dungeonxx : Dungeons.inst().getDungeonManager().getAll()) {
                     String namespace = dungeonxx.getWorldName().toLowerCase();
                     if (namespace.contains(args[1].toLowerCase())) {
                        options.add(dungeonxx.getWorldName());
                     }
                  }
               }

               if (args[0].equalsIgnoreCase("removekey")) {
                  for (AbstractDungeon dungeonxx : Dungeons.inst().getDungeonManager().getAll()) {
                     String namespace = dungeonxx.getWorldName().toLowerCase();
                     if (namespace.contains(args[1].toLowerCase())) {
                        options.add(dungeonxx.getWorldName());
                     }
                  }
               }

               if (args[0].equalsIgnoreCase("setexit")) {
                  for (AbstractDungeon dungeonxxx : Dungeons.inst().getDungeonManager().getAll()) {
                     String namespace = dungeonxxx.getWorldName().toLowerCase();
                     if (namespace.contains(args[1].toLowerCase())) {
                        options.add(dungeonxxx.getWorldName());
                     }
                  }
               }
            }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.play") && args[0].equalsIgnoreCase("play")) {
               for (AbstractDungeon dungeonxxxx : Dungeons.inst().getDungeonManager().getAll()) {
                  String namespace = dungeonxxxx.getWorldName().toLowerCase();
                  if (namespace.contains(args[1].toLowerCase())) {
                     options.add(dungeonxxxx.getWorldName());
                  }
               }
            }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.loottables") && args[0].equalsIgnoreCase("loot")) {
               options.add("create");
               options.add("remove");
               options.add("edit");
            }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.admin")) {
               if (args[0].equalsIgnoreCase("status")) {
                  for (AbstractDungeon dungeonxxxxx : Dungeons.inst().getDungeonManager().getAll()) {
                     String namespace = dungeonxxxxx.getWorldName().toLowerCase();
                     if (namespace.contains(args[1].toLowerCase())) {
                        options.add(dungeonxxxxx.getWorldName());
                     }
                  }
               } else if (args[0].equalsIgnoreCase("delete")) {
                  for (AbstractDungeon dungeonxxxxxx : Dungeons.inst().getDungeonManager().getAll()) {
                     String namespace = dungeonxxxxxx.getWorldName().toLowerCase();
                     if (namespace.contains(args[1].toLowerCase())) {
                        options.add(dungeonxxxxxx.getWorldName());
                     }
                  }
               } else if (args[0].equalsIgnoreCase("reload")) {
                  options.add("all");

                  for (AbstractDungeon dungeonxxxxxxx : Dungeons.inst().getDungeonManager().getAll()) {
                     String namespace = dungeonxxxxxxx.getWorldName().toLowerCase();
                     if (namespace.contains(args[1].toLowerCase())) {
                        options.add(dungeonxxxxxxx.getWorldName());
                     }
                  }
               } else if (args[0].equalsIgnoreCase("import")) {
                  options.add(HelperUtils.colorize("<world>"));
               } else if (args[0].equalsIgnoreCase("create")) {
                  options.add(HelperUtils.colorize("<name>"));
               }
            }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.setcooldown") && args[0].equalsIgnoreCase("setcooldown")) {
               for (AbstractDungeon dungeonxxxxxxxx : Dungeons.inst().getDungeonManager().getAll()) {
                  String namespace = dungeonxxxxxxxx.getWorldName().toLowerCase();
                  if (namespace.contains(args[1].toLowerCase())) {
                     options.add(dungeonxxxxxxxx.getWorldName());
                  }
               }
            }
         }

         if (args.length == 3) {
            if (HelperUtils.hasPermissionSilent(player, "dungeons.admin") && args[0].equalsIgnoreCase("import")) {
               options.add("NORMAL");
               options.add("NETHER");
               options.add("THE_END");
            }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.loottables") && args[0].equalsIgnoreCase("loot") && !args[1].equalsIgnoreCase("create")) {
               for (LootTable table : Dungeons.inst().getLootTableManager().getTables()) {
                  String namespace = table.getNamespace().toLowerCase();
                  if (namespace.contains(args[2].toLowerCase())) {
                     options.add(table.getNamespace());
                  }
               }
            }

            if (HelperUtils.hasPermissionSilent(sender, "dungeons.play.send") && args[0].equalsIgnoreCase("play")) {
               for (Player target : Bukkit.getOnlinePlayers()) {
                  if (target.getName().contains(args[2])) {
                     options.add(target.getName());
                  }
               }
            }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.setcooldown") && args[0].equalsIgnoreCase("setcooldown")) {
               for (Player targetx : Bukkit.getOnlinePlayers()) {
                  if (targetx.getName().contains(args[2])) {
                     options.add(targetx.getName());
                  }
               }
            }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.admin") && args[0].equalsIgnoreCase("create")) {
               List<Class<?>> found = new ArrayList<>();

               for (Entry<String, Class<? extends AbstractDungeon>> pair : DungeonTypeManager.getDungeonTypes().entrySet()) {
                  if (!found.contains(pair.getValue())) {
                     found.add(pair.getValue());
                     options.add(HelperUtils.colorize(pair.getKey()));
                  }
               }
            }
         }

         if (args.length == 4) {
            if (HelperUtils.hasPermissionSilent(player, "dungeons.setcooldown") && args[0].equalsIgnoreCase("setcooldown")) {
               options.add("<seconds>s");
               options.add("<minutes>m");
               options.add("<hours>h");
               options.add("<days>d");
            }

            if (HelperUtils.hasPermissionSilent(player, "dungeons.admin") && args[0].equalsIgnoreCase("create")) {
               options.add("<generator>");
            }
         }

         return options;
      }
   }

   @Override
   protected String getPermissionNode() {
      return "dungeons.core";
   }

   @Override
   protected boolean isConsoleFriendly() {
      return true;
   }

   @Override
   protected String getName() {
      return null;
   }

   private boolean handleSetCooldownCommand(CommandSender sender, String[] args) {
      if (!HelperUtils.hasPermission(sender, "dungeons.setcooldown")) {
         return false;
      } else if (args.length < 2) {
         sender.sendMessage(HelperUtils.colorize("&a/dungeon setcooldown &f| &eSets the access cooldown for the target player."));
         return true;
      } else {
         AbstractDungeon dungeon = this.getPlugin().getDungeons().get(args[1]);
         if (dungeon == null) {
            LangUtils.sendMessage(sender, "commands.setcooldown.dungeon-not-found", args[1]);
            return true;
         } else {
            Player kickPlayer = Bukkit.getPlayer(args[2]);
            if (kickPlayer == null) {
               LangUtils.sendMessage(sender, "commands.setcooldown.player-not-found", args[2]);
               return true;
            } else if (args.length == 3) {
               dungeon.addAccessCooldown(kickPlayer);
               LangUtils.sendMessage(sender, "commands.setcooldown.success", HelperUtils.playerDisplayName(kickPlayer), dungeon.getWorldName());
               LangUtils.sendMessage(sender, "commands.setcooldown.cooldown-time", StringUtils.formatDate(dungeon.getNextUnlockTime()));
               return true;
            } else {
               String duration = args[3];
               Date resetTime = StringUtils.convertDurationString(duration);
               if (resetTime == null) {
                  LangUtils.sendMessage(sender, "commands.setcooldown.invalid-duration");
                  sender.sendMessage(
                     Dungeons.logPrefix
                        + HelperUtils.colorize(
                           "&cInvalid duration! Enter a number and use &6s (seconds), m (minutes), h (hours), or d (days)&c to determine the duration."
                        )
                  );
               } else {
                  dungeon.addAccessCooldown(kickPlayer, resetTime);
                  LangUtils.sendMessage(sender, "commands.setcooldown.success", HelperUtils.playerDisplayName(kickPlayer), dungeon.getWorldName());
                  LangUtils.sendMessage(sender, "commands.setcooldown.cooldown-time", StringUtils.formatDate(resetTime));
               }
                return true;
            }
         }
      }
   }

   private boolean handleCleanSignsCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.cleansigns")) {
         return false;
      } else {
          if (args.length != 1) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon cleansigns &f| &eRemoves all signs from function locations."));
          } else {
            DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
            if (!aPlayer.isEditMode()) {
               LangUtils.sendMessage(player, "commands.cleansigns.not-in-dungeon");
            } else {
               int cleanedSigns = ((InstanceEditable)aPlayer.getInstance()).cleanSigns();
               LangUtils.sendMessage(player, "commands.cleansigns.success", String.valueOf(cleanedSigns));
            }
          }
          return true;
      }
   }

   private static boolean handleDXLImportCommand(CommandSender sender, String[] args) {
      if (!HelperUtils.hasPermission(sender, "dungeons.admin")) {
         return false;
      } else if (args.length != 2) {
         sender.sendMessage(HelperUtils.colorize("&a/dungeon dxlimport &6<dxl map> &f| &eImports from a DXL dungeon."));
         return true;
      } else {
         LangUtils.sendMessage(sender, "commands.dxlimport.importing", args[1]);
         boolean success = DXLUtils.convertFromDXL(args[1]);
         if (success) {
            LangUtils.sendMessage(sender, "commands.dxlimport.success");
            LangUtils.sendMessage(sender, "commands.dxlimport.check-console");
            LangUtils.sendMessage(sender, "commands.dxlimport.clean-signs");
         } else {
            LangUtils.sendMessage(sender, "commands.dxlimport.failed");
         }

         return true;
      }
   }

   private boolean handleImportCommand(CommandSender sender, String[] args) {
      if (!HelperUtils.hasPermission(sender, "dungeons.admin")) {
         return false;
      } else if (args.length != 2 && args.length != 3) {
         sender.sendMessage(HelperUtils.colorize("&a/dungeon import &6<world> &b[type] &f| &eImports a world as a dungeon."));
         return true;
      } else {
         File importFolder = new File(Bukkit.getWorldContainer(), args[1]);
         if (!importFolder.isDirectory()) {
            LangUtils.sendMessage(sender, "commands.import.world-not-found", args[1]);
            return true;
         } else {
            WorldCreator importer = new WorldCreator(args[1]);
            if (args.length == 3) {
               try {
                  Environment dimension = Environment.valueOf(args[2]);
                  importer.environment(dimension);
               } catch (IllegalArgumentException var8) {
                  LangUtils.sendMessage(sender, "commands.import.invalid-dimension", args[2].toUpperCase(Locale.ROOT));
                  return true;
               }
            }

            importer.type(WorldType.FLAT);
            String dimension = importer.environment().toString();
            World importWorld = importer.createWorld();
            if (importWorld == null) {
               return false;
            } else {
               Location importSpawn = importWorld.getSpawnLocation();
               Bukkit.unloadWorld(importWorld, false);
               Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                  File saveFolder = new File(this.getPlugin().getDungeonFiles(), args[1]);
                  saveFolder.mkdir();

                  try {
                     FileUtils.copyDirectory(importFolder, saveFolder);
                  } catch (IOException var8x) {
                     LangUtils.sendMessage(sender, "commands.import.failed");
                     Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var8x.getMessage());

                  }

                  AbstractDungeon importDungeon = this.getPlugin().getDungeons().loadDungeon(saveFolder);
                  if (importDungeon == null) {
                     LangUtils.sendMessage(sender, "commands.import.failed");
                  } else {
                     importDungeon.setSaveConfig("General.Dimension", dimension);
                     importDungeon.setLobbySpawn(importSpawn);
                     this.getPlugin().getDungeons().put(importDungeon);
                     LangUtils.sendMessage(sender, "commands.import.success", args[1]);
                  }
               });
               return true;
            }
         }
      }
   }

   private boolean handleSaveCommand(CommandSender sender) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.edit")) {
         return false;
      } else {
          DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
         AbstractInstance instance = aPlayer.getInstance();
         if (instance == null) {
            LangUtils.sendMessage(player, "commands.save.not-in-dungeon");
         } else {
            LangUtils.sendMessage(player, "commands.save.saving");
            instance.saveWorld();
            LangUtils.sendMessage(player, "commands.save.success", instance.getDungeon().getWorldName());
         }
          return true;
      }
   }

   private boolean handleFunctionToolCommand(CommandSender sender) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.functioneditor")) {
         return false;
      } else {
          DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
         if (!aPlayer.isEditMode()) {
            LangUtils.sendMessage(player, "commands.functiontool.not-in-dungeon");
         } else {
            ItemUtils.giveOrDrop(player, ItemUtils.getFunctionTool());
         }
          return true;
      }
   }

   private boolean handleRoomToolCommand(CommandSender sender) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.functioneditor")) {
         return false;
      } else {
          DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
         if (!aPlayer.isEditMode()) {
            LangUtils.sendMessage(player, "commands.functiontool.not-in-dungeon");
            return true;
         } else if (aPlayer.getInstance().as(InstanceEditableProcedural.class) == null) {
            LangUtils.sendMessage(player, "commands.roomtool.not-in-procedural");
            return true;
         } else {
            ItemUtils.giveOrDrop(player, ItemUtils.getRoomTool());
            return true;
         }
      }
   }

   private boolean handleDungeonItemCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.edit")) {
         return false;
      } else {
          if (args.length != 1) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon dungeonitem &f| &eMakes the item in your hand a dungeon item."));
          } else {
            DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
            if (!aPlayer.isEditMode()) {
               LangUtils.sendMessage(player, "commands.dungeonitem.not-in-dungeon");
            } else {
               ItemStack item = player.getInventory().getItemInMainHand();
               ItemMeta meta = item.getItemMeta();
               PersistentDataContainer data = meta.getPersistentDataContainer();
               data.set(new NamespacedKey(this.plugin, "DungeonItem"), PersistentDataType.INTEGER, 1);
               item.setItemMeta(meta);
               LangUtils.sendMessage(player, "commands.dungeonitem.success");
            }
          }
          return true;
      }
   }

   private boolean handleUnbanItemCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.edit")) {
         return false;
      } else {
          if (args.length != 1) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon unbanitem &f| &eUnbans your held item from the dungeon."));
          } else {
            DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
            if (!aPlayer.isEditMode()) {
               LangUtils.sendMessage(player, "commands.unbanitem.not-in-dungeon");
            } else {
               AbstractDungeon dungeon = aPlayer.getInstance().getDungeon();
               boolean unbanSuccess = dungeon.unbanItem(player.getInventory().getItemInMainHand());
               if (unbanSuccess) {
                  LangUtils.sendMessage(player, "commands.unbanitem.success");
               } else {
                  LangUtils.sendMessage(player, "commands.unbanitem.not-banned");
               }

            }
          }
          return true;
      }
   }

   private boolean handleBanItemCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.edit")) {
         return false;
      } else {
          if (args.length != 1) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon banitem &f| &eBans your held item from the dungeon."));
          } else {
            DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
            if (!aPlayer.isEditMode()) {
               LangUtils.sendMessage(player, "commands.banitem.not-in-dungeon");
            } else {
               AbstractDungeon dungeon = aPlayer.getInstance().getDungeon();
               dungeon.banItem(player.getInventory().getItemInMainHand());
               LangUtils.sendMessage(player, "commands.banitem.success");
            }
          }
          return true;
      }
   }

   private boolean handleSetExitCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.edit") && !HelperUtils.hasPermissionSilent(sender, "dungeons.edit.*")) {
         return false;
      } else {
          if (args.length != 2) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon setexit &6<dungeon> &f| &eSets the dungeon's exit location."));
            return true;
         } else if (this.getPlugin().getDungeons().get(args[1]) == null) {
            LangUtils.sendMessage(player, "commands.setexit.dungeon-not-found", args[1]);
            return true;
         } else {
            AbstractDungeon dungeon = this.getPlugin().getDungeons().get(args[1]);
            Location exitLoc = player.getLocation();
            dungeon.setExit(exitLoc);
            double exitX = MathUtils.round(exitLoc.getX(), 2);
            double exitY = MathUtils.round(exitLoc.getY(), 2);
            double exitZ = MathUtils.round(exitLoc.getZ(), 2);
            double exitYAW = MathUtils.round(exitLoc.getYaw(), 2);
            LangUtils.sendMessage(
               player, "commands.setexit.success", String.valueOf(exitX), String.valueOf(exitY), String.valueOf(exitZ), String.valueOf(exitYAW)
            );
            return true;
         }
      }
   }

   private boolean handleSetSpawnCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.edit")) {
         return false;
      } else {
          if (args.length != 1) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon setspawn &f| &eSets the dungeon's starting spawn."));
          } else {
            DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
            if (!aPlayer.isEditMode()) {
               LangUtils.sendMessage(player, "commands.setspawn.not-in-dungeon");
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cYou are not currently editing a dungeon!"));
            } else {
               AbstractDungeon dungeon = aPlayer.getInstance().getDungeon();
               Location lobbyLoc = player.getLocation();
               dungeon.setStartSpawn(lobbyLoc);
               double spawnX = MathUtils.round(lobbyLoc.getX(), 2);
               double spawnY = MathUtils.round(lobbyLoc.getY(), 2);
               double spawnZ = MathUtils.round(lobbyLoc.getZ(), 2);
               double spawnYAW = MathUtils.round(lobbyLoc.getYaw(), 2);
               LangUtils.sendMessage(
                  player, "commands.setspawn.success", String.valueOf(spawnX), String.valueOf(spawnY), String.valueOf(spawnZ), String.valueOf(spawnYAW)
               );
            }
          }
          return true;
      }
   }

   private boolean handleSetLobbyCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.edit")) {
         return true;
      } else {
          if (args.length != 1) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon setlobby &f| &eSets the dungeon's lobby spawn."));
          } else {
            DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
            if (!aPlayer.isEditMode()) {
               LangUtils.sendMessage(player, "commands.setlobby.not-in-dungeon");
            } else {
               AbstractDungeon dungeon = aPlayer.getInstance().getDungeon();
               Location lobbyLoc = player.getLocation();
               dungeon.setLobbySpawn(lobbyLoc);
               double lobbyX = MathUtils.round(lobbyLoc.getX(), 2);
               double lobbyY = MathUtils.round(lobbyLoc.getY(), 2);
               double lobbyZ = MathUtils.round(lobbyLoc.getZ(), 2);
               double lobbyYAW = MathUtils.round(lobbyLoc.getYaw(), 2);
               LangUtils.sendMessage(
                  player, "commands.setlobby.success", String.valueOf(lobbyX), String.valueOf(lobbyY), String.valueOf(lobbyZ), String.valueOf(lobbyYAW)
               );
            }
          }
          return true;
      }
   }

   private boolean handleRemoveAllkeysCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.edit") && !HelperUtils.hasPermissionSilent(sender, "dungeons.edit.*")) {
         return true;
      } else {
         if (args.length != 2) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon clearkeys &6<dungeon> &f| &eRemove all keys."));
            return true;
         } else if (this.getPlugin().getDungeons().get(args[1]) == null) {
            LangUtils.sendMessage(player, "commands.clearkeys.dungeon-not-found", args[1]);
            return true;
         } else {
            if (!HelperUtils.hasPermissionSilent(player, "dungeons.edit." + args[1])) {
               LangUtils.sendMessage(player, "commands.clearkeys.no-permission");
            } else {
               AbstractDungeon dungeon = this.getPlugin().getDungeons().get(args[1]);
               dungeon.removeAllAccessKeys();
               LangUtils.sendMessage(player, "commands.clearkeys.success");
            }
            return true;
         }
      }
   }

   private boolean handleRemoveKeyCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.edit") && !HelperUtils.hasPermissionSilent(sender, "dungeons.edit.*")) {
         return true;
      } else {
          if (args.length != 2) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon removekey &6<dungeon> &f| &eRemove the held item as an access key."));
            return true;
         } else if (this.getPlugin().getDungeons().get(args[1]) == null) {
            LangUtils.sendMessage(player, "commands.removekey.dungeon-not-found", args[1]);
            return true;
         } else {
              if (!HelperUtils.hasPermissionSilent(player, "dungeons.edit." + args[1])) {
                 LangUtils.sendMessage(player, "commands.removekey.no-permission");
              } else {
                 AbstractDungeon dungeon = this.getPlugin().getDungeons().get(args[1]);
                 ItemStack heldItem = player.getInventory().getItemInMainHand();
                 if (heldItem.getType() != Material.AIR) {
                    boolean keyFound = dungeon.removeAccessKey(heldItem);
                    if (keyFound) {
                       LangUtils.sendMessage(player, "commands.removekey.success");
                    } else {
                       LangUtils.sendMessage(player, "commands.removekey.no-key-found");
                       player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cNo access key was found matching the item in your hand for this dungeon."));
                    }

                 } else {
                    LangUtils.sendMessage(player, "commands.removekey.no-held-item");
                 }
              }
              return true;
          }
      }
   }

   private boolean handleAddKeyCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.edit") && !HelperUtils.hasPermissionSilent(sender, "dungeons.edit.*")) {
         return true;
      } else {
          if (args.length != 2) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon addkey &6<dungeon> &f| &eMake the held item an access key."));
            return true;
         } else if (this.getPlugin().getDungeons().get(args[1]) == null) {
            LangUtils.sendMessage(player, "commands.addkey.dungeon-not-found", args[1]);
            return true;
         } else if (!HelperUtils.hasPermissionSilent(player, "dungeons.edit." + args[1])) {
            LangUtils.sendMessage(player, "commands.addkey.no-permission");
            return true;
         } else {
            AbstractDungeon dungeon = this.getPlugin().getDungeons().get(args[1]);
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            if (heldItem.getType() != Material.AIR) {
               dungeon.addAccessKey(heldItem);
               LangUtils.sendMessage(player, "commands.addkey.success");
            } else {
               LangUtils.sendMessage(player, "commands.addkey.no-held-item");
            }
              return true;
          }
      }
   }

   private boolean handleDeleteCommand(CommandSender sender, String[] args) {
      if (!HelperUtils.hasPermission(sender, "dungeons.admin")) {
         return false;
      } else if (args.length != 2) {
         sender.sendMessage(HelperUtils.colorize("&a/dungeon delete &6<dungeon> &f| &eDeletes a dungeon. (Also confirms deletion.)"));
         return true;
      } else {
         AbstractDungeon dungeon = this.getPlugin().getDungeons().get(args[1]);
         if (dungeon == null) {
            LangUtils.sendMessage(sender, "commands.delete.dungeon-not-found", args[1]);
            return true;
         } else if (dungeon.getEditSession() != null) {
            LangUtils.sendMessage(sender, "commands.delete.edit-in-progress");
            return true;
         } else if (!dungeon.isMarkedForDelete()) {
            dungeon.setMarkedForDelete(true);
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> dungeon.setMarkedForDelete(false), 200L);
            LangUtils.sendMessage(sender, "commands.delete.delete-warning");
            LangUtils.sendMessage(sender, "commands.delete.delete-confirm");
            return true;
         } else {
            for (AbstractInstance inst : this.getPlugin().getActiveInstances()) {
               if (inst.getDungeon() == dungeon) {
                  for (DungeonPlayer mPlayer : inst.getPlayers()) {
                     inst.removePlayer(mPlayer);
                     LangUtils.sendMessage(mPlayer.getPlayer(), "commands.delete.notification");
                  }
               }
            }

            this.getPlugin().getDungeons().remove(dungeon);
            HelperUtils.deleteRecursively(dungeon.getFolder());
            LangUtils.sendMessage(sender, "commands.delete.success", args[1]);
            return true;
         }
      }
   }

   private boolean handleEditCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.edit") && !HelperUtils.hasPermissionSilent(sender, "dungeons.edit.*")) {
         return false;
      } else {
          if (args.length != 2) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon edit &6<dungeon> &f| &eOpens an edit session of the dungeon."));
            return true;
         } else {
            DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
            if (aPlayer.getInstance() != null) {
               LangUtils.sendMessage(player, "commands.edit.already-in-dungeon");
               return true;
            } else if (this.getPlugin().getDungeons().get(args[1]) == null) {
               LangUtils.sendMessage(player, "commands.edit.dungeon-not-found", args[1]);
               return true;
            } else if (!HelperUtils.hasPermissionSilent(player, "dungeons.edit." + args[1])) {
               LangUtils.sendMessage(player, "commands.edit.no-permission");
               return true;
            } else {
               LangUtils.sendMessage(player, "commands.edit.loading");
               Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.getPlugin().getDungeons().editDungeon(args[1], player));
               return true;
            }
         }
      }
   }

   private boolean handleCreateCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.admin")) {
         return false;
      } else {
          if (args.length < 2 || args.length > 4) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon create &6<name> [type] [generator] &f| &eCreates a new blank dungeon."));
            return true;
         } else if (this.getPlugin().getDungeons().get(args[1]) != null) {
            LangUtils.sendMessage(player, "commands.create.already-exists");
            return true;
         } else {
            String dunType;
            if (args.length >= 3) {
               dunType = args[2];
            } else {
               dunType = "";
            }

            String genType;
            if (args.length == 4) {
               genType = args[3];
            } else {
               genType = "";
            }

            WorldCreator loader = new WorldCreator(args[1]);
            if (!genType.isEmpty()) {
               loader.generator(genType);
            } else {
               loader.type(WorldType.FLAT);
            }

            loader.generateStructures(false);
            World world = loader.createWorld();
            if (world != null) {
                 Location spawnpoint = world.getSpawnLocation();
                 File worldFolder = world.getWorldFolder();
                 Bukkit.unloadWorld(world, true);
                 Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                     File saveFolder = new File(this.getPlugin().getDungeonFiles(), args[1]);
                     saveFolder.mkdir();

                     try {
                         FileUtils.copyDirectory(worldFolder, saveFolder);
                         FileUtils.deleteDirectory(worldFolder);
                     } catch (IOException var9x) {
                         LangUtils.sendMessage(player, "commands.create.failed-to-create");
                         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var9x.getMessage());

                     }

                     AbstractDungeon newDungeon = this.getPlugin().getDungeons().loadDungeon(saveFolder, dunType, genType);
                     if (newDungeon == null) {
                         LangUtils.sendMessage(player, "commands.create.failed-to-create");
                         Dungeons.inst().getLogger().warning(HelperUtils.colorize("&cNo dungeon type by name " + dunType + " found!"));
                     } else {
                         newDungeon.setLobbySpawn(spawnpoint);
                         if (!dunType.isEmpty()) {
                             newDungeon.getConfig().set("General.DungeonType", dunType);
                         }

                         newDungeon.saveConfig();
                         LangUtils.sendMessage(player, "commands.create.success", args[1]);
                     }
                 });
             }
             return true;
         }
      }
   }

   private boolean handleReloadCommand(CommandSender sender, String[] args) {
      if (!HelperUtils.hasPermission(sender, "dungeons.admin")) {
         return false;
      } else if (args.length != 2 && args.length != 1) {
         sender.sendMessage(HelperUtils.colorize("&a/dungeon reload &b[dungeon] &f| &eReloads the config and/or dungeon."));
         return true;
      } else if (!(sender instanceof Player player)) {
         if (args.length == 1) {
            LangUtils.sendMessage(sender, "commands.reload.config-reloading");
            this.getPlugin().reloadConfigs();
            LangUtils.sendMessage(sender, "commands.reload.config-reloaded");
         } else {
            if (args[1].equalsIgnoreCase("all")) {
               LangUtils.sendMessage(sender, "commands.reload.all-dungeons-reloading");
               this.getPlugin().reloadAllDungeons();
               LangUtils.sendMessage(sender, "commands.reload.all-dungeons-reloaded");
            } else {
               AbstractDungeon dungeon = this.getPlugin().getDungeons().get(args[1]);
               if (dungeon == null) {
                  LangUtils.sendMessage(sender, "commands.reload.dungeon-not-found", args[1]);
                  return true;
               }

               LangUtils.sendMessage(sender, "commands.reload.dungeon-reloading", args[1]);
               this.getPlugin().reloadDungeon(dungeon);
               LangUtils.sendMessage(sender, "commands.reload.dungeon-reloaded", args[1]);
            }

         }
          return true;
      } else {
         DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
         if (args.length == 1) {
            LangUtils.sendMessage(sender, "commands.reload.config-reloading");
            this.getPlugin().reloadConfigs();
            LangUtils.sendMessage(sender, "commands.reload.config-reloaded");
         } else {
            if (args[1].equalsIgnoreCase("all")) {
               if (aPlayer.isReloadQueued() || this.getPlugin().getActiveInstances().isEmpty()) {
                  LangUtils.sendMessage(sender, "commands.reload.all-dungeons-reloading");
                  this.getPlugin().reloadAllDungeons();
                  aPlayer.unqueueReload();
                  LangUtils.sendMessage(sender, "commands.reload.all-dungeons-reloaded");
                  return true;
               }

            } else {
               AbstractDungeon dungeon = this.getPlugin().getDungeons().get(args[1]);
               if (dungeon == null) {
                  LangUtils.sendMessage(sender, "commands.reload.dungeon-not-found", args[1]);
                  return true;
               }

               if (aPlayer.isReloadQueued() || dungeon.getInstances().isEmpty()) {
                  LangUtils.sendMessage(sender, "commands.reload.dungeon-reloading", args[1]);
                  this.getPlugin().reloadDungeon(dungeon);
                  aPlayer.unqueueReload();
                  LangUtils.sendMessage(sender, "commands.reload.dungeon-reloaded", args[1]);
                  return true;
               }

            }
             aPlayer.queueReload();
             LangUtils.sendMessage(sender, "commands.reload.reload-dungeon-warning", args[1]);
             LangUtils.sendMessage(sender, "commands.reload.reload-dungeon-confirm", args[1]);

         }
          return true;
      }
   }

   private boolean handleStatusCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.admin")) {
         return false;
      } else {
          if (args.length == 1) {
            player.sendMessage(HelperUtils.fullColor("<#9753f5>-= Dungeons - Version " + this.getPlugin().getVersion() + " =-"));
            LangUtils.sendMessage(player, "commands.status.total-instances", String.valueOf(this.getPlugin().getActiveInstances().size()));
            List<DungeonPlayer> players = new ArrayList<>();

            for (AbstractInstance inst : this.getPlugin().getActiveInstances()) {
               players.addAll(inst.getPlayers());
            }

            LangUtils.sendMessage(player, "commands.status.total-players", String.valueOf(players.size()));

            for (AbstractDungeon dungeonInfo : this.getPlugin().getDungeons().getAll()) {
               player.sendMessage(HelperUtils.fullColor(" <#9753f5>[ " + dungeonInfo.getWorldName() + " ]"));
               LangUtils.sendMessage(player, "commands.status.dungeon-instances", String.valueOf(dungeonInfo.getInstances().size()));
               players = new ArrayList<>();

               for (AbstractInstance inst : dungeonInfo.getInstances()) {
                  players.addAll(inst.getPlayers());
               }

               LangUtils.sendMessage(player, "commands.status.dungeon-players", String.valueOf(players.size()));
            }
         } else if (args.length == 2) {
            AbstractDungeon dungeon = this.getPlugin().getDungeons().get(args[1]);
            if (dungeon == null) {
               LangUtils.sendMessage(player, "commands.status.dungeon-not-found", args[1]);
               return true;
            }

            player.sendMessage(HelperUtils.fullColor("<#9753f5>---[ " + dungeon.getWorldName() + " STATUS ]---"));
            if (dungeon.getInstances().isEmpty()) {
               LangUtils.sendMessage(player, "commands.status.none-loaded");
            }

            for (AbstractInstance inst : dungeon.getInstances()) {
               player.sendMessage(HelperUtils.colorize(" &e[ " + inst.getInstanceWorld().getName() + " ]"));
               if (inst.isEditInstance()) {
                  player.sendMessage(HelperUtils.colorize("&6EDIT SESSION"));
               }

               for (DungeonPlayer dPlayer : inst.getPlayers()) {
                  player.sendMessage(HelperUtils.colorize("&6 - " + dPlayer.getPlayer().getName()));
               }
            }
         } else {
            player.sendMessage(HelperUtils.colorize("&a/dungeon status &b[dungeon] &f| &eShows active dungeon status info."));
         }

         return true;
      }
   }

   private boolean handleKickCommand(CommandSender sender, String[] args) {
      if (!HelperUtils.hasPermission(sender, "dungeons.admin")) {
         return false;
      } else if (args.length != 2) {
         sender.sendMessage(HelperUtils.colorize("&a/dungeon kick &6<player> &f| &eKicks the player from the dungeon they're in."));
         return true;
      } else {
         Player kickPlayer = Bukkit.getPlayer(args[1]);
         if (kickPlayer == null) {
            return false;
         } else {
            DungeonPlayer kickAPlayer = this.getPlugin().getDungeonPlayer(kickPlayer);
            if (kickAPlayer.getInstance() == null) {
               LangUtils.sendMessage(kickPlayer, "commands.kick.not-in-dungeon");
            } else {
               LangUtils.sendMessage(kickPlayer, "commands.kick.kick-alert");
               kickAPlayer.getInstance().removePlayer(kickAPlayer);
            }
             return true;
         }
      }
   }

   private boolean handleLeaveCommand(CommandSender sender) {
      if (!(sender instanceof Player player)) {
         return false;
      } else {
         DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
         if (aPlayer.getInstance() == null) {
            if (Dungeons.inst().getQueueManager().getQueue(aPlayer) != null) {
               Dungeons.inst().getQueueManager().unqueue(aPlayer);
               LangUtils.sendMessage(player, "commands.leave.left-queue");
            } else {
               AbstractInstance inst = Dungeons.inst().getDungeonInstance(player.getWorld().getName());
               if (inst != null) {
                  if (aPlayer.getSavedPosition() != null) {
                     HelperUtils.forceTeleport(player, aPlayer.getSavedPosition());
                  } else {
                     HelperUtils.forceTeleport(player, player.getRespawnLocation());
                  }
               }

               LangUtils.sendMessage(player, "commands.leave.not-in-dungeon");
            }
         } else {
            aPlayer.getInstance().removePlayer(aPlayer);
         }
          return true;
      }
   }

   private boolean handleJoinCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.admin")) {
         return false;
      } else {
          if (args.length != 2) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon join &6<player> &f| &eSends you to the player's dungeon."));
         } else {
            DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer != null) {
                 DungeonPlayer targetAPlayer = this.getPlugin().getDungeonPlayer(targetPlayer);
                 if (targetAPlayer.getInstance() == null) {
                     LangUtils.sendMessage(player, "commands.join.not-in-dungeon");
                 } else {
                     targetAPlayer.getInstance().addPlayer(aPlayer);
                 }
             }
         }
          return true;
      }
   }

   private boolean handleLivesCommand(CommandSender sender) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.lives")) {
          return false;
      } else {
         DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
         AbstractInstance instance = aPlayer.getInstance();
         if (instance == null) {
            LangUtils.sendMessage(player, "commands.lives.not-in-dungeon");
            return true;
         }

         InstancePlayable instancePlayable = instance.asPlayInstance();
         if (instancePlayable != null) {
             if (instancePlayable.getPlayerLives().get(player.getUniqueId()) == null) {
                 LangUtils.sendMessage(player, "commands.lives.infinite-lives");
             } else {
                 int livesRemaining = instancePlayable.getPlayerLives().get(player.getUniqueId());
                 LangUtils.sendMessage(player, "commands.lives.lives-remaining", String.valueOf(livesRemaining));
             }
         }
         return true;
      }
   }

   private boolean handleStuckCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.stuck")) {
         return false;
      } else {
          if (args.length != 1) {
            if (!this.getPlugin().isStuckKillsPlayer()) {
               player.sendMessage(HelperUtils.colorize("&a/dungeon stuck &f| &eSends you to your last checkpoint safely."));
            } else {
               player.sendMessage(HelperUtils.colorize("&a/dungeon stuck &f| &eForce-kills you so you can respawn."));
            }

         } else {
            DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
            if (aPlayer.getInstance() == null) {
               LangUtils.sendMessage(player, "commands.stuck.not-in-dungeon");
            } else {
               if (this.getPlugin().isStuckKillsPlayer()) {
                  player.damage(9.99999999E8);
               } else {
                  LangUtils.sendMessage(player, "commands.stuck.success");
                  aPlayer.sendToCheckpoint();
               }

            }
         }
          return true;
      }
   }

   private boolean handlePlayCommand(CommandSender sender, String[] args, QueueManager queueManager) {
      if (!HelperUtils.hasPermission(sender, "dungeons.play") && !HelperUtils.hasPermission(sender, "dungeons.play.send")) {
         return true;
      } else if (args.length < 2) {
         sender.sendMessage(HelperUtils.colorize("&a/dungeon play &6<dungeon>[:difficulty] [player] &f| &eStarts the dungeon for the player."));
         return true;
      }

      @Nullable Player playerx = null;

      if (args.length == 2) {
         if (!HelperUtils.hasPermission(sender, "dungeons.play")) {
            return true;
         }

         if (!(sender instanceof Player)) {
            LangUtils.sendMessage(sender, "commands.play.console-needs-player");
            return true;
         }

         playerx = (Player) sender;
      }

      if (args.length == 3) {
         if (!HelperUtils.hasPermissionSilent(sender, "dungeons.play.send")) {
            return true;
         }

         playerx = Bukkit.getPlayer(args[2]);
         if (playerx == null || !playerx.isOnline()) {
            LangUtils.sendMessage(playerx, "commands.play.player-not-found", args[2]);
            return true;
         }

      }

       DungeonPlayer aPlayer;
       if (playerx != null) {
           aPlayer = this.getPlugin().getDungeonPlayer(playerx);
       } else {
          return true;
       }
       if (aPlayer.isAwaitingDungeon()) {
         LangUtils.sendMessage(sender, "commands.play.already-in-queue");
         return true;
      } else if (aPlayer.getInstance() != null) {
         LangUtils.sendMessage(sender, "commands.play.already-in-dungeon");
         return true;
      } else if (queueManager.getQueue(aPlayer) != null) {
         LangUtils.sendMessage(sender, "commands.play.already-in-queue");
         return true;
      } else {
         String dungeonName = args[1];
         String difficulty = "";
         if (dungeonName.contains(":")) {
            String[] split = dungeonName.split(":");
            dungeonName = split[0];
            difficulty = split[1];
         }

         AbstractDungeon dungeon = this.getPlugin().getDungeons().get(dungeonName);
         if (dungeon == null) {
            LangUtils.sendMessage(sender, "commands.play.dungeon-not-found", args[1]);
         } else {
            boolean useDifficulty = dungeon.isUseDifficultyLevels();
            boolean showMenu = dungeon.isShowDifficultyMenu();
            if (useDifficulty) {
               if (difficulty.isEmpty()) {
                  if (showMenu) {
                     this.getPlugin().getGuiApi().openGUI(playerx, "difficulty_" + dungeon.getWorldName());
                     return true;
                  }
               } else if (!HelperUtils.hasPermission(sender, "dungeons.play.difficulty")) {
                  return true;
               }
            }

            this.getPlugin().sendToDungeon(playerx, dungeonName, difficulty);
         }
          return true;
      }

   }

   private static boolean handleLootCommand(CommandSender sender, String[] args, LootTableManager lootTableManager, GUIAPI guiApi) {
      if (!(sender instanceof Player player)) {
         return true;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.loottables")) {
         return true;
      } else {
          if (args.length < 2) {
            player.sendMessage(HelperUtils.colorize("&a/dungeon loot &6<create|edit|remove> &f| &eManages loot tables."));
            return true;
         } else {
            String var6 = args[1];
            switch (var6) {
               case "create":
                  if (args.length != 3) {
                     player.sendMessage(HelperUtils.colorize("&a/dungeon loot create &6<name> &f| &eCreates a new loot table."));
                  } else {
                     LootTable tablex = lootTableManager.get(args[2]);
                     if (tablex != null) {
                        LangUtils.sendMessage(player, "commands.loot.create.already-exists", args[2]);
                        return true;
                     }

                     lootTableManager.put(new LootTable(args[2]));
                     LangUtils.sendMessage(player, "commands.loot.create.success", args[2]);
                     guiApi.openGUI(player, "loottable_" + args[2]);
                  }
                   return true;
                case "remove":
                  if (args.length != 3) {
                     player.sendMessage(HelperUtils.colorize("&a/dungeon loot remove &6<loot table> &f| &eDeletes the loot table."));
                     return true;
                  }

                  lootTableManager.remove(args[2]);
                  LangUtils.sendMessage(player, "commands.loot.remove.success", args[2]);
                  return true;
               case "edit":
                  if (args.length != 3) {
                     player.sendMessage(HelperUtils.colorize("&a/dungeon loot edit &6<loot table> &f| &eOpens the loot table editor."));
                  } else {
                     LootTable table = lootTableManager.get(args[2]);
                     if (table == null) {
                        LangUtils.sendMessage(player, "commands.loot.edit.not-found", args[2]);
                     } else {
                        if (table.getEditor() != null) {
                           LangUtils.sendMessage(player, "commands.loot.edit.already-editing");
                           return true;
                        }

                        table.setEditor(player);
                        guiApi.openGUI(player, "loottable_" + args[2]);
                     }
                  }
                   return true;
                default:
                  return false;
            }
         }
      }
   }

   private static boolean handleHelpCommand(CommandSender sender, String[] args) {
      if (args.length == 2) {
         Optional<Integer> helpPage = StringUtils.readIntegerInput(sender, args[1]);
         if (helpPage.isPresent()) {
            HelperUtils.displayHelpMenu(sender, helpPage.get());
         } else {
            HelperUtils.displayHelpMenu(sender, 1);
         }
      } else {
         HelperUtils.displayHelpMenu(sender, 1);
      }

      return true;
   }

   private static boolean handleTestDoorCommand(CommandSender sender, String[] args) {
      if (args.length != 2) {
         return false;
      } else if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.admin")) {
         return false;
      } else {
         DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         AbstractInstance inst = mPlayer.getInstance();
         if (inst != null) {
              InstanceProcedural proc = inst.as(InstanceProcedural.class);
              if (proc != null) {
                  InstanceRoom room = proc.getRoom(player.getLocation());
                  if (room != null) {
                      room.toggleDoor(args[1]);
                  }
              }
          }
          return false;
      }
   }

   private static boolean handleTestAllDoorsCommand(CommandSender sender, String[] args) {
      if (args.length != 1) {
         return false;
      } else if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.admin")) {
         return false;
      } else {
          DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         AbstractInstance inst = mPlayer.getInstance();
          if (inst != null) {
              InstanceProcedural proc = inst.as(InstanceProcedural.class);
              if (proc != null) {
                  InstanceRoom room = proc.getRoom(player.getLocation());
                  if (room != null) {
                      room.toggleValidDoors(true);
                  }
              }
          }
          return false;
      }
   }

   private static boolean handleMapCommand(CommandSender sender, String[] args) {
      if (args.length != 1) {
         return false;
      } else if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.admin")) {
         return false;
      } else {
          DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         AbstractInstance abs = mPlayer.getInstance();
          if (abs != null) {
              InstancePlayable inst = abs.as(InstancePlayable.class);
              if (inst != null) {
                  inst.giveDungeonMap(player);
              }
          }
          return false;
      }
   }
}
