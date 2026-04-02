package nl.hauntedmc.dungeons.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.events.DungeonEndEvent;
import nl.hauntedmc.dungeons.api.events.PlayerFinishDungeonEvent;
import nl.hauntedmc.dungeons.api.events.PlayerLeaveDungeonEvent;
import nl.hauntedmc.dungeons.api.generation.rooms.Connector;
import nl.hauntedmc.dungeons.api.generation.rooms.DungeonRoomContainer;
import nl.hauntedmc.dungeons.api.generation.rooms.WhitelistEntry;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.api.queue.QueueData;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonProcedural;
import nl.hauntedmc.dungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import nl.hauntedmc.dungeons.gui.inv.HotbarMenuHandler;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.entity.ParticleUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

public class DungeonListener implements Listener {
   private final Set<UUID> pendingRoomNameInputs = ConcurrentHashMap.newKeySet();

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      if (aPlayer != null) {
         aPlayer.setPlayer(player);
         Location savedLocation = aPlayer.getSavedPosition();
         if (aPlayer.getInstance() == null && savedLocation != null) {
            HelperUtils.forceTeleport(player, savedLocation);
            player.setGameMode(aPlayer.getSavedGameMode());
         }

         QueueData qData = Dungeons.inst().getQueueManager().getQueue(aPlayer);
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
         Dungeons.inst().getPlayerManager().put(player);
         aPlayer = Dungeons.inst().getDungeonPlayer(player);
         Location exitLoc = aPlayer.getExitLocation();
         if (exitLoc != null) {
            HelperUtils.forceTeleport(player, exitLoc);
            aPlayer.clearExitLocation();
         }
      }
   }

   @EventHandler
   public void restoreEditPlayerHotbar(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
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
               if (HelperUtils.hasPermissionSilent(player, "dungeons.functioneditor")) {
                  DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
                  if (aPlayer.isEditMode()) {
                     if (ItemUtils.isFunctionTool(item)) {
                        event.setCancelled(true);
                        AbstractDungeon dungeon = aPlayer.getInstance().getDungeon();
                         Location blockLoc;
                         if (event.getClickedBlock() != null) {
                             blockLoc = event.getClickedBlock().getLocation().clone();
                         } else {
                            return;
                         }
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
                           Dungeons.inst().getGuiApi().openGUI(player, "functionmenu");
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
               if (HelperUtils.hasPermissionSilent(player, "dungeons.roomeditor")) {
                  DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
                  if (aPlayer.isEditMode()) {
                     if (ItemUtils.isRoomTool(item)) {
                        InstanceEditableProcedural editSesh = aPlayer.getInstance().as(InstanceEditableProcedural.class);
                         DungeonProcedural dungeon;
                         if (editSesh != null) {
                             dungeon = editSesh.getDungeon();
                         } else {
                            return;
                         }
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
               if (HelperUtils.hasPermissionSilent(player, "dungeons.roomeditor")) {
                  DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
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
                                 ParticleUtils.displayBoundingBox(player, 200, HelperUtils.captureOffsetBoundingBox(aPlayer.getPos1(), aPlayer.getPos2()));
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
   public void onRoomNameInput(AsyncChatEvent event) {
      Player player = event.getPlayer();
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      if (aPlayer != null) {
         if (aPlayer.getInstance() != null) {
            InstanceEditableProcedural instance = aPlayer.getInstance().as(InstanceEditableProcedural.class);
            if (instance != null) {
               if (aPlayer.isAwaitingRoomName()) {
                  event.setCancelled(true);
                  UUID playerId = player.getUniqueId();
                  if (!this.pendingRoomNameInputs.add(playerId)) {
                     return;
                  }

                  String message = HelperUtils.plainText(event.originalMessage());
                  Bukkit.getScheduler().runTask(Dungeons.inst(), () -> {
                     try {
                        this.handleRoomNameInput(player, aPlayer, instance, message);
                     } finally {
                        this.pendingRoomNameInputs.remove(playerId);
                     }
                  });
               }
            }
         }
      }
   }

   @EventHandler
   public void onRoomBlockPlace(BlockPlaceEvent event) {
      Player player = event.getPlayer();
      DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
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
      DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
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
      final DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
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
            }).runTaskLater(Dungeons.inst(), 5L);
         }
      }
   }

   @EventHandler
   public void onDungeonEnd(DungeonEndEvent event) {
      if (event.getParty() == null || event.getParty().getPlayers().size() == 1) {
         DungeonPlayer mPlayer = event.getGamePlayers().getFirst();
         IDungeonParty party = mPlayer.getiDungeonParty();
         if (party == null) {
            return;
         }
         mPlayer.setiDungeonParty(null);
      }
   }

   @EventHandler
   public void onDungeonFinish(PlayerFinishDungeonEvent event) {
      DungeonPlayer mPlayer = event.getMPlayer();
      mPlayer.clearDungeonSavePoint(event.getDungeon().getWorldName());
   }

   private void handleRoomNameInput(Player player, DungeonPlayer aPlayer, InstanceEditableProcedural instance, String message) {
      if (!aPlayer.isAwaitingRoomName()) {
         return;
      }

      Pattern pat = Pattern.compile("[^a-z0-9]", Pattern.CASE_INSENSITIVE);
      Matcher matcher = pat.matcher(message);
      if (matcher.find()) {
         LangUtils.sendMessage(player, "instance.editmode.room-name-invalid");
         return;
      }

      DungeonProcedural dungeon = instance.getDungeon();
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
            } else {
               room.getRoomWhitelist().add(new WhitelistEntry(targetRoom));
               aPlayer.setAddingWhitelistEntry(false);
            }

            LangUtils.sendMessage(player, "instance.editmode.room-whitelist.add-success", message);
         }
      } else if (aPlayer.isEditingWhitelistEntry()) {
         DungeonRoomContainer targetRoom = dungeon.getRoom(message);
         if (targetRoom != null) {
            player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
            player.sendMessage(HelperUtils.colorize("&cNOT IMPLEMENTED!"));
            aPlayer.setEditingWhitelistEntry(false);
         }
      } else if (aPlayer.isRemovingWhitelistEntry()) {
         room = aPlayer.getActiveRoom();
         DungeonRoomContainer targetRoom = dungeon.getRoom(message);
         if (targetRoom != null) {
            player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
            List<WhitelistEntry> snapshot = connector != null ? new ArrayList<>(connector.getRoomWhitelist()) : new ArrayList<>(room.getRoomWhitelist());
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
         BoundingBox bounds = HelperUtils.captureBoundingBox(aPlayer.getPos1(), aPlayer.getPos2());
         room = dungeon.defineRoom(message, bounds);
         if (room != null) {
            instance.setRoomLabel(room);
            LangUtils.sendMessage(player, "instance.editmode.room-created", message);
            aPlayer.setPos1(null);
            aPlayer.setPos2(null);
         }
      }
   }

   @EventHandler
   public void onLeaveDungeon(PlayerLeaveDungeonEvent event) {
      if (!event.isCancelled()) {
         DungeonPlayer mPlayer = event.getMPlayer();
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
      if (Dungeons.inst().getDungeons().get(name) != null) {
         HelperUtils.releaseSpawnChunk(world);
         world.setAutoSave(false);
      }
   }
}
