package net.playavalon.mythicdungeons.gui;

import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.generation.rooms.Connector;
import net.playavalon.mythicdungeons.api.generation.rooms.DungeonRoomContainer;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.instances.InstanceEditable;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonProcedural;
import net.playavalon.mythicdungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import net.playavalon.mythicdungeons.menu.HotbarMenu;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.SimpleLocation;
import net.playavalon.mythicdungeons.utility.helpers.DirectionUtils;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.MathUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import net.playavalon.mythicdungeons.utility.numbers.RangedNumber;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.util.BoundingBox;

public class HotbarMenuHandler {
   private static HotbarMenu functionEditMenu;
   private static HotbarMenu roomEditMenu;
   private static HotbarMenu roomRulesMenu;

   public static void initEditMenu() {
      functionEditMenu = HotbarMenu.create();
      functionEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.COMMAND_BLOCK);
            this.button.setDisplayName("&a&lEdit Function");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            aPlayer.setHotbar(aPlayer.getActiveFunction().getMenu(), true);
         }
      });
      functionEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
            this.button.setDisplayName("&e&lChange Trigger");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicDungeons.inst().getAvnAPI().openGUI(player, "triggermenu");
         }
      });
      functionEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BOOK);
            this.button.setDisplayName("&3&lCopy Function");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            aPlayer.setCutting(false);
            aPlayer.setCopying(true);
            aPlayer.setCopiedFunction(aPlayer.getActiveFunction());
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eFunction copied!"));
         }
      });
      functionEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.SHEARS);
            this.button.setDisplayName("&3&lCut Function");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            aPlayer.setCopying(false);
            aPlayer.setCutting(true);
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eFunction cut!"));
         }
      });
      functionEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.GLOBE_BANNER_PATTERN);
            this.button.setDisplayName("&3&lPaste Function");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            if (event instanceof PlayerInteractEvent interactEvent) {
               if (interactEvent.getAction() == Action.RIGHT_CLICK_BLOCK) {
                  Player player = event.getPlayer();
                  MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
                  InstanceEditable instance = aPlayer.getInstance().asEditInstance();
                  if (instance != null) {
                     Location targetLocation = interactEvent.getClickedBlock().getLocation();
                     AbstractDungeon dungeon = instance.getDungeon();
                     if (aPlayer.isCopying()) {
                        if (dungeon.getFunctions().containsKey(targetLocation)) {
                           player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cThere is already a function here!"));
                        } else {
                           DungeonFunction copiedFunction = aPlayer.getCopiedFunction().clone();
                           dungeon.addFunction(targetLocation, copiedFunction);
                           aPlayer.setActiveFunction(copiedFunction);
                           instance.addFunctionLabel(copiedFunction);
                           player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&ePasted function to the clicked block!"));
                        }
                     } else {
                        if (aPlayer.isCutting()) {
                           if (dungeon.getFunctions().containsKey(targetLocation)) {
                              player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cThere is already a function here!"));
                              return;
                           }

                           instance.removeFunctionLabelByFunction(aPlayer.getActiveFunction());
                           dungeon.removeFunction(aPlayer.getActiveFunction().getLocation());
                           dungeon.addFunction(targetLocation, aPlayer.getActiveFunction());
                           instance.addFunctionLabel(dungeon.getFunctions().get(targetLocation));
                           player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eCut-and-pasted function to the clicked block!"));
                           aPlayer.setCutting(false);
                        }
                     }
                  }
               }
            }
         }
      });
      functionEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BARRIER);
            this.button.setDisplayName("&c&lDelete Function");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            InstanceEditable inst = aPlayer.getInstance().asEditInstance();
            if (inst != null) {
               inst.getDungeon().removeFunction(aPlayer.getActiveFunction().getLocation());
               inst.removeFunctionLabelByFunction(aPlayer.getActiveFunction());
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cFunction deleted..."));
               aPlayer.restoreHotbar();
            }
         }
      });
   }

   public static void initRoomEditMenu() {
      roomEditMenu = HotbarMenu.create();
      roomEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.IRON_DOOR);
            this.button.setDisplayName("&a&lEdit Connector");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            Block block = player.getTargetBlockExact(10);
            if (block == null) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cLook at a block to select or create a connector!"));
            } else {
               InstanceEditableProcedural inst = aPlayer.getInstance().as(InstanceEditableProcedural.class);
               if (inst != null) {
                  DungeonProcedural dungeon = inst.getDungeon();
                  DungeonRoomContainer targetRoom = dungeon.getRoom(block.getLocation());
                  DungeonRoomContainer activeRoom = aPlayer.getActiveRoom();
                  SimpleLocation loc = SimpleLocation.from(block);
                  Connector connector = activeRoom.getConnector(loc);
                  if (connector == null) {
                     if (targetRoom == null || targetRoom != activeRoom) {
                        player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cThis block isn't inside the selected room!"));
                        return;
                     }

                     connector = activeRoom.addConnector(loc);
                     if (connector == null) {
                        player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cRoom connectors must be added to the edge of a room!"));
                        return;
                     }

                     player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aAdded room connector at the target location!"));
                  }

                  aPlayer.setConfirmRoomAction(false);
                  aPlayer.setActiveConnector(connector);
                  aPlayer.setHotbar(dungeon.getLayout().getConnectorEditMenu(), true);
               }
            }
         }
      });
      roomEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NETHER_STAR);
            this.button.setDisplayName("&e&lEdit Rules");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
            mPlayer.setHotbar(HotbarMenuHandler.roomRulesMenu, true);
         }
      });
      roomEditMenu.addMenuItem(
         new ChatMenuItem() {
            @Override
            public void buildButton() {
               this.button = new MenuButton(Material.ENDER_EYE);
               this.button.setDisplayName("&d&lDefault Weight");
               this.button.addLore("&eHow likely this room will");
               this.button.addLore("&egenerate relative to others.");
            }

            @Override
            public void onSelect(Player player) {
               MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
               DungeonRoomContainer room = mPlayer.getActiveRoom();
               if (room != null) {
                  player.sendMessage(
                     Util.colorize(MythicDungeons.debugPrefix + "&eHow is this room weighted? Higher values makes the room more likely to generate.")
                  );
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent weight: &6" + MathUtils.round(room.getWeight(), 2)));
               }
            }

            @Override
            public void onInput(Player player, String message) {
               MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
               DungeonRoomContainer room = mPlayer.getActiveRoom();
               if (room != null) {
                  InstanceEditableProcedural inst = mPlayer.getInstance().as(InstanceEditableProcedural.class);
                  if (inst != null) {
                     Optional<Double> value = StringUtils.readDoubleInput(player, message);
                     room.setWeight(value.orElse(room.getWeight()));
                     inst.setRoomLabel(room);
                     if (value.isPresent()) {
                        double weight = value.get();
                        player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet room default weight to '&6" + MathUtils.round(weight, 2) + "&a'"));
                        player.sendMessage(
                           Util.colorize(MythicDungeons.debugPrefix + "&bNOTE: You can set up connectors to have a custom weight for each room.")
                        );
                     }
                  }
               }
            }
         }
      );
      roomEditMenu.addMenuItem(
         new MenuItem() {
            @Override
            public void buildButton() {
               this.button = new MenuButton(Material.RESPAWN_ANCHOR);
               this.button.setDisplayName("&d&lSet Spawn");
               this.button.addLore("&eMakes this room a valid starting");
               this.button.addLore("&eroom and places the spawn point");
               this.button.addLore("&eat the target location.");
            }

            @Override
            public void onSelect(PlayerEvent event) {
               Player player = event.getPlayer();
               MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
               DungeonRoomContainer room = aPlayer.getActiveRoom();
               if (room != null) {
                  InstanceEditableProcedural inst = aPlayer.getInstance().as(InstanceEditableProcedural.class);
                  if (inst != null) {
                     Block block = player.getTargetBlockExact(10);
                     if (block == null) {
                        player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cLook at a block to set or delete this room's spawn point!"));
                     } else {
                        Location target = block.getLocation();
                        target.setWorld(null);
                        target.add(0.5, 0.0, 0.5);
                        if (target.equals(room.getSpawn())) {
                           player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eRemoved this room's spawn point."));
                           inst.removeRoomLabel(room);
                           room.setSpawn(null);
                           inst.setRoomLabel(room);
                        } else {
                           player.sendMessage(
                              MythicDungeons.debugPrefix
                                 + Util.colorize("&aSet this room's spawn to the block you're looking at. Use left-click to set facing direction.")
                           );
                           if (room.getSpawn() == null) {
                              player.sendMessage(
                                 MythicDungeons.debugPrefix + Util.colorize("&bThis room has also been added to the list of possible starting rooms.")
                              );
                           }

                           inst.removeRoomLabel(room);
                           room.setSpawn(block.getLocation().add(0.5, 0.0, 0.5));
                           inst.setRoomLabel(room);
                        }
                     }
                  }
               }
            }

            @Override
            public void onClick(PlayerEvent event) {
               Player player = event.getPlayer();
               MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
               DungeonRoomContainer room = aPlayer.getActiveRoom();
               if (room != null) {
                  InstanceEditableProcedural inst = aPlayer.getInstance().as(InstanceEditableProcedural.class);
                  if (inst != null) {
                     if (room.getSpawn() != null) {
                        float yaw = (float)MathUtils.round(player.getYaw(), 0);
                        room.getSpawn().setYaw(yaw);
                        player.sendMessage(
                           Util.colorize(MythicDungeons.debugPrefix + "&aSet spawn direction to '&6" + yaw + "&a' degrees. (Where you're looking.)")
                        );
                     }
                  }
               }
            }
         }
      );
      roomEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CAMPFIRE);
            this.button.setDisplayName("&3&lExpand & Shrink");
            this.button.addLore("&eExpands or shrinks the room by one");
            this.button.addLore("&ein the direction you're facing.");
            this.button.addLore("");
            this.button.addLore("&8Left-click expands.");
            this.button.addLore("&8Right-click shrinks.");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            DungeonRoomContainer room = aPlayer.getActiveRoom();
            if (room != null) {
               InstanceEditableProcedural inst = aPlayer.getInstance().as(InstanceEditableProcedural.class);
               if (inst != null) {
                  BlockFace facingDir = DirectionUtils.getFacingDirection(player);
                  inst.clearRoomDisplay(room);
                  inst.removeRoomLabel(room);
                  room.expand(facingDir, -1);
                  inst.setRoomLabel(room);
                  inst.displayRoomParticles(player, room);
                  player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aShrank room 1 block " + facingDir.getOppositeFace() + "."));
               }
            }
         }

         @Override
         public void onClick(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            DungeonRoomContainer room = aPlayer.getActiveRoom();
            if (room != null) {
               InstanceEditableProcedural inst = aPlayer.getInstance().as(InstanceEditableProcedural.class);
               if (inst != null) {
                  inst.clearRoomDisplay(room);
                  BlockFace facingDir = DirectionUtils.getFacingDirection(player);
                  inst.removeRoomLabel(room);
                  room.expand(facingDir, 1);
                  inst.setRoomLabel(room);
                  inst.displayRoomParticles(player, room);
                  player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aExpanded room 1 block " + facingDir + "."));
               }
            }
         }
      });
      roomEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.DIAMOND_AXE);
            this.button.setDisplayName("&d&lSelect Area");
            this.button.addLore("&8Right-click selects corner 1");
            this.button.addLore("&8Left-click selects corner 2");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
            DungeonRoomContainer room = mPlayer.getActiveRoom();
            if (room != null) {
               if (mPlayer.getInstance() != null) {
                  InstanceEditableProcedural instance = mPlayer.getInstance().as(InstanceEditableProcedural.class);
                  if (instance != null) {
                     Block block = player.getTargetBlockExact(10);
                     if (block == null) {
                        LangUtils.sendMessage(player, "instance.editmode.room-click-required");
                     } else {
                        LangUtils.sendMessage(player, "instance.editmode.room-select-2");
                        Location pos1 = block.getLocation();
                        mPlayer.setPos1(pos1);
                        if (mPlayer.getPos2() != null) {
                           LangUtils.sendMessage(player, "instance.editmode.room-edited", room.getNamespace());
                           BoundingBox bounds = Util.captureBoundingBox(mPlayer.getPos1(), mPlayer.getPos2());
                           instance.clearRoomDisplay(room);
                           instance.removeRoomLabel(room);
                           room.setBounds(player, bounds);
                           instance.setRoomLabel(room);
                           instance.displayRoomParticles(player, room);
                           mPlayer.setPos1(null);
                           mPlayer.setPos2(null);
                        }
                     }
                  }
               }
            }
         }

         @Override
         public void onClick(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
            DungeonRoomContainer room = mPlayer.getActiveRoom();
            if (room != null) {
               if (mPlayer.getInstance() != null) {
                  InstanceEditableProcedural instance = mPlayer.getInstance().as(InstanceEditableProcedural.class);
                  if (instance != null) {
                     Block block = player.getTargetBlockExact(10);
                     if (block == null) {
                        LangUtils.sendMessage(player, "instance.editmode.room-click-required");
                     } else {
                        LangUtils.sendMessage(player, "instance.editmode.room-select-1");
                        Location pos2 = block.getLocation();
                        mPlayer.setPos2(pos2);
                        if (mPlayer.getPos1() != null) {
                           LangUtils.sendMessage(player, "instance.editmode.room-edited", room.getNamespace());
                           BoundingBox bounds = Util.captureBoundingBox(mPlayer.getPos1(), mPlayer.getPos2());
                           instance.clearRoomDisplay(room);
                           instance.removeRoomLabel(room);
                           room.setBounds(player, bounds);
                           instance.setRoomLabel(room);
                           instance.displayRoomParticles(player, room);
                           mPlayer.setPos1(null);
                           mPlayer.setPos2(null);
                        }
                     }
                  }
               }
            }
         }

         @Override
         public void onUnhover(PlayerItemHeldEvent event) {
            Player player = event.getPlayer();
            MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
            if (mPlayer.getPos1() != null || mPlayer.getPos2() != null) {
               mPlayer.setPos1(null);
               mPlayer.setPos2(null);
               LangUtils.sendMessage(player, "instance.editmode.room-edit-cancelled");
            }
         }
      });
      roomEditMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BARRIER);
            this.button.setDisplayName("&c&lDelete Room");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            if (!aPlayer.isConfirmRoomAction()) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eAre you sure you want to delete this room? Click again to confirm."));
               aPlayer.setConfirmRoomAction(true);
            } else {
               aPlayer.setConfirmRoomAction(false);
               InstanceEditableProcedural inst = aPlayer.getInstance().as(InstanceEditableProcedural.class);
               if (inst != null) {
                  DungeonRoomContainer room = aPlayer.getActiveRoom();
                  if (room != null) {
                     DungeonProcedural dungeon = inst.getDungeon();
                     dungeon.removeRoom(room);
                     inst.removeRoomLabel(room);

                     for (DungeonFunction function : room.getFunctionsMapRelative().values()) {
                        inst.removeFunctionLabelByFunction(function);
                        inst.getFunctions().remove(function.getLocation());
                     }

                     inst.clearRoomDisplay(room);
                     aPlayer.setActiveRoom(null);
                     player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cDeleted the selected room."));
                     aPlayer.previousHotbar();
                  }
               }
            }
         }
      });
   }

   public static void initRoomRulesMenu() {
      roomRulesMenu = HotbarMenu.create();
      roomRulesMenu.addMenuItem(MenuItem.BACK);
      roomRulesMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.JIGSAW);
            this.button.setDisplayName("&e&lEdit Allowed Rooms");
            this.button.addLore("&eOpens a menu for customizing");
            this.button.addLore("&ea whitelist of what rooms can");
            this.button.addLore("&ebe connected to this one.");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            DungeonRoomContainer room = aPlayer.getActiveRoom();
            if (room != null) {
               MythicDungeons.inst().getAvnAPI().openGUI(player, "whitelist_" + room.getDungeon().getWorldName() + "_" + room.getNamespace());
            }
         }
      });
      roomRulesMenu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.EMERALD);
            this.button.setDisplayName("&d&lGeneration Limit");
            this.button.addLore("&eHow many times this room should");
            this.button.addLore("&egenerate in the dungeon.");
            this.button.addLore("");
            this.button.addLore("&dSupports ranges like 0-3, or 2+!");
         }

         @Override
         public void onSelect(Player player) {
            MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
            DungeonRoomContainer room = mPlayer.getActiveRoom();
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eHow many times should this room generate? (Supports ranges like 0-3 or 2+)"));
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eCurrent value: &6" + room.getOccurrencesString()));
         }

         @Override
         public void onInput(Player player, String message) {
            MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
            InstanceEditableProcedural inst = mPlayer.getInstance().as(InstanceEditableProcedural.class);
            if (inst != null) {
               DungeonRoomContainer room = mPlayer.getActiveRoom();
               RangedNumber range = new RangedNumber(message);
               inst.removeRoomLabel(room);
               room.setOccurrencesString(message);
               room.setOccurrences(range);
               inst.setRoomLabel(room);
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet generation limit to '&6" + message + "&a' copies of this room."));
            }
         }
      });
      roomRulesMenu.addMenuItem(
         new ChatMenuItem() {
            @Override
            public void buildButton() {
               this.button = new MenuButton(Material.COMPASS);
               this.button.setDisplayName("&d&lGeneration Depth");
               this.button.addLore("&eHow many rooms deep into the dungeon");
               this.button.addLore("&ethis room should generate.");
               this.button.addLore("");
               this.button.addLore("&dSupports ranges like 0-3, or 2+!");
            }

            @Override
            public void onSelect(Player player) {
               MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
               DungeonRoomContainer room = mPlayer.getActiveRoom();
               player.sendMessage(
                  MythicDungeons.debugPrefix
                     + Util.colorize("&eHow many rooms deep into the dungeon should this room generate? (Supports ranges like 0-3 or 2+)")
               );
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eCurrent value: &6" + room.getDepthString()));
            }

            @Override
            public void onInput(Player player, String message) {
               MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
               InstanceEditableProcedural inst = mPlayer.getInstance().as(InstanceEditableProcedural.class);
               if (inst != null) {
                  DungeonRoomContainer room = mPlayer.getActiveRoom();
                  RangedNumber range = new RangedNumber(message);
                  inst.removeRoomLabel(room);
                  room.setDepthString(message);
                  room.setDepth(range);
                  inst.setRoomLabel(room);
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet generation depth for this room to '&6" + message + "&a'."));
               }
            }
         }
      );
   }

   public static HotbarMenu getFunctionEditMenu() {
      return functionEditMenu;
   }

   public static HotbarMenu getRoomEditMenu() {
      return roomEditMenu;
   }

   public static HotbarMenu getRoomRulesMenu() {
      return roomRulesMenu;
   }
}
