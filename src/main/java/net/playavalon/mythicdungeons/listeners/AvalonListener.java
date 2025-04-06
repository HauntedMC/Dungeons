package net.playavalon.mythicdungeons.listeners;

import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.entity.TeleportFlag.EntityState;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.events.dungeon.DungeonEndEvent;
import net.playavalon.mythicdungeons.api.events.dungeon.PlayerFinishDungeonEvent;
import net.playavalon.mythicdungeons.api.events.dungeon.PlayerLeaveDungeonEvent;
import net.playavalon.mythicdungeons.api.generation.rooms.Connector;
import net.playavalon.mythicdungeons.api.generation.rooms.DungeonRoomContainer;
import net.playavalon.mythicdungeons.api.generation.rooms.WhitelistEntry;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.api.queue.QueueData;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonProcedural;
import net.playavalon.mythicdungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import net.playavalon.mythicdungeons.gui.HotbarMenuHandler;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.ParticleUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

public class AvalonListener implements Listener {
   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         aPlayer.setPlayer(player);
         Location savedLocation = aPlayer.getSavedPosition();
         if (aPlayer.getInstance() == null && savedLocation != null) {
            if (MythicDungeons.inst().isSupportsTeleportFlags()) {
               player.teleport(savedLocation, new TeleportFlag[]{EntityState.RETAIN_PASSENGERS});
            } else {
               List<Entity> passengers = player.getPassengers();
               player.eject();
               player.teleport(savedLocation);
               if (!passengers.isEmpty()) {
                  player.addPassenger(passengers.get(0));
               }
            }

            player.setGameMode(aPlayer.getSavedGameMode());
         }

         QueueData qData = MythicDungeons.inst().getQueueManager().getQueue(aPlayer);
         if (qData != null) {
            aPlayer.setAwaitingDungeon(true);
            if (qData.isReadyCheckWaiting() && !qData.isPlayerReady(aPlayer)) {
               player.playSound(player.getLocation(), "entity.player.levelup", 1.0F, 0.7F);
               player.playSound(player.getLocation(), "block.beacon.activate", 1.0F, 0.7F);
               LangUtils.sendMessage(player, "instance.queue.dungeon-ready", qData.getDungeon().getWorldName());
               StringUtils.sendReadyCheckMessage(player);
            }
         }
      } else {
         MythicDungeons.inst().getPlayerManager().put(player);
         aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         Location exitLoc = aPlayer.getExitLocation();
         if (exitLoc != null) {
            if (MythicDungeons.inst().isSupportsTeleportFlags()) {
               player.teleport(exitLoc, new TeleportFlag[]{EntityState.RETAIN_PASSENGERS});
            } else {
               List<Entity> passengers = player.getPassengers();
               player.eject();
               player.teleport(exitLoc);
               if (!passengers.isEmpty()) {
                  player.addPassenger(passengers.get(0));
               }
            }

            aPlayer.clearExitLocation();
         }
      }
   }

   @EventHandler
   public void restoreEditPlayerHotbar(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      aPlayer.setAwaitingDungeon(false);
      if (aPlayer.isEditMode()) {
         aPlayer.restoreHotbar();
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onFunctionToolInteract(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
         ItemStack item = event.getItem();
         if (item != null) {
            if (event.getHand() != EquipmentSlot.OFF_HAND) {
               Player player = event.getPlayer();
               if (Util.hasPermissionSilent(player, "dungeons.functioneditor")) {
                  MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
                  if (aPlayer.isEditMode()) {
                     if (ItemUtils.isFunctionTool(item)) {
                        event.setCancelled(true);
                        AbstractDungeon dungeon = aPlayer.getInstance().getDungeon();
                        Location blockLoc = event.getClickedBlock().getLocation().clone();
                        blockLoc.setWorld(null);
                        if (dungeon.getFunctions().containsKey(blockLoc)) {
                           DungeonFunction function = dungeon.getFunctions().get(blockLoc);
                           LangUtils.sendMessage(player, "instance.editmode.function-selected", "<" + function.getColour() + ">" + function.getNamespace());
                           aPlayer.setActiveFunction(function);
                           aPlayer.switchHotbar(HotbarMenuHandler.getFunctionEditMenu());
                        } else {
                           AbstractInstance abs = aPlayer.getInstance();
                           if (abs == null) {
                              return;
                           }

                           InstanceEditableProcedural inst = abs.as(InstanceEditableProcedural.class);
                           if (inst != null) {
                              DungeonRoomContainer room = inst.getDungeon().getRoom(blockLoc);
                              if (room == null) {
                                 LangUtils.sendMessage(player, "instance.editmode.function-outside-room");
                                 return;
                              }
                           }

                           LangUtils.sendMessage(player, "instance.editmode.function-select");
                           aPlayer.setTargetLocation(blockLoc);
                           MythicDungeons.inst().getAvnAPI().openGUI(player, "functionmenu");
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onRoomToolInteract(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
         ItemStack item = event.getItem();
         if (item != null) {
            if (event.getHand() != EquipmentSlot.OFF_HAND) {
               Player player = event.getPlayer();
               if (Util.hasPermissionSilent(player, "dungeons.roomeditor")) {
                  MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
                  if (aPlayer.isEditMode()) {
                     if (ItemUtils.isRoomTool(item)) {
                        InstanceEditableProcedural editSesh = aPlayer.getInstance().as(InstanceEditableProcedural.class);
                        DungeonProcedural dungeon = editSesh.getDungeon();
                        event.setCancelled(true);
                        if (aPlayer.isAwaitingRoomName()) {
                           aPlayer.setAwaitingRoomName(false);
                           aPlayer.setPos1(null);
                           aPlayer.setPos2(null);
                           LangUtils.sendMessage(player, "instance.editmode.room-create-cancelled");
                        } else {
                           Block targetBlock = player.getTargetBlockExact(10);
                           Location pos;
                           if (targetBlock == null) {
                              pos = player.getLocation().toBlockLocation();
                           } else {
                              pos = targetBlock.getLocation();
                           }

                           DungeonRoomContainer room = dungeon.getRoom(pos);
                           if (room != null) {
                              aPlayer.setActiveRoom(room);
                              aPlayer.saveHotbar();
                              aPlayer.setHotbar(HotbarMenuHandler.getRoomEditMenu());
                           } else {
                              aPlayer.setPos2(pos);
                              LangUtils.sendMessage(player, "instance.editmode.room-select-2");
                              if (aPlayer.getPos1() != null) {
                                 aPlayer.setAwaitingRoomName(true);
                                 LangUtils.sendMessage(player, "instance.editmode.room-name-request");
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onRoomToolClick(PlayerInteractEvent event) {
      if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR) {
         if (event.getHand() != EquipmentSlot.OFF_HAND) {
            Player player = event.getPlayer();
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() != Material.AIR) {
               if (Util.hasPermissionSilent(player, "dungeons.roomeditor")) {
                  MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
                  if (aPlayer.isEditMode()) {
                     if (aPlayer.getInstance() instanceof InstanceEditableProcedural) {
                        if (ItemUtils.isRoomTool(item)) {
                           event.setCancelled(true);
                           if (aPlayer.isAwaitingRoomName()) {
                              aPlayer.setAwaitingRoomName(false);
                              aPlayer.setPos1(null);
                              aPlayer.setPos2(null);
                              LangUtils.sendMessage(player, "instance.editmode.room-create-cancelled");
                           } else {
                              Block targetBlock = player.getTargetBlockExact(10);
                              Location pos;
                              if (targetBlock == null) {
                                 pos = player.getLocation().toBlockLocation();
                              } else {
                                 pos = targetBlock.getLocation();
                              }

                              aPlayer.setPos1(pos);
                              LangUtils.sendMessage(player, "instance.editmode.room-select-1");
                              if (aPlayer.getPos2() != null) {
                                 aPlayer.setAwaitingRoomName(true);
                                 LangUtils.sendMessage(player, "instance.editmode.room-name-request");
                                 ParticleUtils.displayBoundingBox(player, 200, Util.captureOffsetBoundingBox(aPlayer.getPos1(), aPlayer.getPos2()));
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onRoomNameInput(AsyncPlayerChatEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         if (aPlayer.getInstance() != null) {
            InstanceEditableProcedural instance = aPlayer.getInstance().as(InstanceEditableProcedural.class);
            if (instance != null) {
               DungeonProcedural dungeon = instance.getDungeon();
               if (aPlayer.isAwaitingRoomName()) {
                  event.setCancelled(true);
                  String message = event.getMessage();
                  Pattern pat = Pattern.compile("[^a-z0-9]", 2);
                  Matcher matcher = pat.matcher(message);
                  if (matcher.find()) {
                     LangUtils.sendMessage(player, "instance.editmode.room-name-invalid");
                  } else {
                     event.setMessage("");
                     aPlayer.setAwaitingRoomName(false);
                     DungeonRoomContainer room;
                     Connector connector = aPlayer.getActiveConnector();
                     if (aPlayer.isAddingWhitelistEntry()) {
                         room = aPlayer.getActiveRoom();
                         DungeonRoomContainer targetRoom = dungeon.getRoom(message);
                        if (targetRoom != null) {
                           player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                           if (connector != null) {
                              connector.getRoomWhitelist().add(new WhitelistEntry(targetRoom));
                              LangUtils.sendMessage(player, "instance.editmode.room-whitelist.add-success", message);
                           } else {
                              room.getRoomWhitelist().add(new WhitelistEntry(targetRoom));
                              LangUtils.sendMessage(player, "instance.editmode.room-whitelist.add-success", message);
                              aPlayer.setAddingWhitelistEntry(false);
                           }
                        }
                     } else if (aPlayer.isEditingWhitelistEntry()) {
                         room = aPlayer.getActiveRoom();
                         DungeonRoomContainer targetRoom = dungeon.getRoom(message);
                        if (targetRoom != null) {
                           player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                           player.sendMessage(Util.colorize("&cNOT IMPLEMENTED!"));
                           aPlayer.setEditingWhitelistEntry(false);
                        }
                     } else if (aPlayer.isRemovingWhitelistEntry()) {
                         room = aPlayer.getActiveRoom();
                         DungeonRoomContainer targetRoom = dungeon.getRoom(message);
                        if (targetRoom != null) {
                           player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                           List<WhitelistEntry> snapshot = null;
                           if (connector != null) {
                              snapshot = new ArrayList<>(connector.getRoomWhitelist());
                           }

                           if (snapshot == null) {
                              snapshot = new ArrayList<>(room.getRoomWhitelist());
                           }

                           for (WhitelistEntry entry : snapshot) {
                              if (entry.getRoomName().equals(message)) {
                                 if (connector != null) {
                                    connector.getRoomWhitelist().remove(entry);
                                 } else {
                                    room.getRoomWhitelist().remove(entry);
                                 }
                              }
                           }

                           LangUtils.sendMessage(player, "instance.editmode.room-whitelist.remove-success", message);
                           aPlayer.setRemovingWhitelistEntry(false);
                        }
                     } else {
                        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                        BoundingBox bounds = Util.captureBoundingBox(aPlayer.getPos1(), aPlayer.getPos2());
                        room = dungeon.defineRoom(message, bounds);
                        if (room != null) {
                           Bukkit.getScheduler().runTask(MythicDungeons.inst(), () -> instance.setRoomLabel(room));
                           LangUtils.sendMessage(player, "instance.editmode.room-created", message);
                           aPlayer.setPos1(null);
                           aPlayer.setPos2(null);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onRoomBlockPlace(BlockPlaceEvent event) {
      Player player = event.getPlayer();
      MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (mPlayer.getInstance() != null) {
         InstanceEditableProcedural instance = mPlayer.getInstance().as(InstanceEditableProcedural.class);
         if (instance != null) {
            Block block = event.getBlock();
            Location target = block.getLocation();
            DungeonRoomContainer room = instance.getDungeon().getRoom(target);
            if (room != null) {
               if (!room.isChangedSinceLastSave()) {
                  room.setChangedSinceLastSave(true);
               }
            }
         }
      }
   }

   @EventHandler
   public void onRoomBlockBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (mPlayer.getInstance() != null) {
         InstanceEditableProcedural instance = mPlayer.getInstance().as(InstanceEditableProcedural.class);
         if (instance != null) {
            Block block = event.getBlock();
            Location target = block.getLocation();
            DungeonRoomContainer room = instance.getDungeon().getRoom(target);
            if (room != null) {
               if (!room.isChangedSinceLastSave()) {
                  room.setChangedSinceLastSave(true);
               }
            }
         }
      }
   }

   @EventHandler
   public void onCrouch(PlayerToggleSneakEvent event) {
      final Player player = event.getPlayer();
      final MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (!player.isFlying()) {
         if (aPlayer.getCurrentHotbar() != null) {
            (new BukkitRunnable() {
               public void run() {
                  if (!player.isSneaking()) {
                     this.cancel();
                  } else {
                     aPlayer.restoreHotbar();
                     aPlayer.setChatListening(false);
                     aPlayer.setActiveFunction(null);
                     aPlayer.setActiveTrigger(null);
                     aPlayer.setActiveCondition(null);
                     aPlayer.setCopiedFunction(null);
                     aPlayer.setCutting(false);
                     aPlayer.setCopying(false);
                     aPlayer.setActiveRoom(null);
                     aPlayer.setActiveConnector(null);
                     aPlayer.setActiveDoor(null);
                     aPlayer.setConfirmRoomAction(false);
                     aPlayer.setCopiedConnector(null);
                  }
               }
            }).runTaskLater(MythicDungeons.inst(), 5L);
         }
      }
   }

   @EventHandler
   public void onDungeonEnd(DungeonEndEvent event) {
      if (event.getParty() == null || event.getParty().getPlayers().size() == 1) {
         MythicPlayer mPlayer = event.getGamePlayers().get(0);
         IDungeonParty party = mPlayer.getDungeonParty();
         if (party == null) {
            return;
         }

         mPlayer.setDungeonParty(null);
         if (party instanceof MythicParty) {
            ((MythicParty)party).disband();
         }
      }
   }

   @EventHandler
   public void onDungeonFinish(PlayerFinishDungeonEvent event) {
      MythicPlayer mPlayer = event.getMPlayer();
      mPlayer.clearDungeonSavePoint(event.getDungeon().getWorldName());
   }

   @EventHandler
   public void onLeaveDungeon(PlayerLeaveDungeonEvent event) {
      if (!event.isCancelled()) {
         MythicPlayer mPlayer = event.getMPlayer();
         if (!mPlayer.getRewardsInv().isEmpty()) {
            StringUtils.sendClickableCommand(mPlayer.getPlayer(), LangUtils.getMessage("instance.rewards.unclaimed-rewards"), "rewards");
         }
      }
   }

   @EventHandler
   public void onLoadDungeonWorld(WorldInitEvent event) {
      World world = event.getWorld();
      String instName = world.getName();
      Pattern p = Pattern.compile("(_[0-9]*)?$");
      String name = p.matcher(instName).replaceFirst("");
      if (MythicDungeons.inst().getDungeons().get(name) != null) {
         world.setKeepSpawnInMemory(false);
         world.setAutoSave(false);
      }
   }
}
