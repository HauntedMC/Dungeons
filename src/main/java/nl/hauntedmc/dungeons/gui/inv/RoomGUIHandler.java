package nl.hauntedmc.dungeons.gui.inv;

import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.generation.rooms.Connector;
import nl.hauntedmc.dungeons.api.generation.rooms.DungeonRoomContainer;
import nl.hauntedmc.dungeons.api.generation.rooms.WhitelistEntry;
import nl.hauntedmc.dungeons.api.gui.window.GUIInventory;
import nl.hauntedmc.dungeons.api.gui.window.GUIWindow;
import nl.hauntedmc.dungeons.api.gui.buttons.Button;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonProcedural;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RoomGUIHandler {
   private static ItemStack openSlotItem;

   private static void initOpenSlotItem() {
      openSlotItem = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
      ItemMeta meta = openSlotItem.getItemMeta();

      assert meta != null;

      meta.setDisplayName(HelperUtils.colorize("&7Empty Slot"));
      openSlotItem.setItemMeta(meta);
   }

   public static void initRoomWhitelist(DungeonProcedural dungeon, DungeonRoomContainer room) {
      if (openSlotItem == null) {
         initOpenSlotItem();
      }

      GUIWindow tableMenu = new GUIWindow("whitelist_" + dungeon.getWorldName() + "_" + room.getNamespace(), 54, "&8Edit Room Whitelist");
      tableMenu.setCancelClick(false);
      tableMenu.addOpenAction(
         "load",
         event -> {
            Player player = (Player)event.getPlayer();
            DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);

            for (int slot = 9; slot < 54; slot++) {
               int i = slot - 9;
               GUIInventory gui = tableMenu.getPlayersGui(player);
               if (i >= room.getRoomWhitelist().size()) {
                  gui.removeButton(slot);
                  if (!gui.getButtons().containsKey(slot + 1)) {
                     break;
                  }
               } else {
                  WhitelistEntry entry = room.getRoomWhitelist().get(i);
                  DungeonRoomContainer whiteRoom = entry.getRoom(dungeon);
                  Material mat = entry.getMaterial();
                  if (mat == null) {
                     mat = Material.STRUCTURE_BLOCK;
                  }

                  Button button = new Button(
                     "whitelist_" + dungeon.getWorldName() + "_" + room.getNamespace() + "_" + slot, mat, "&b&l" + whiteRoom.getNamespace()
                  );
                  int finalSlot = slot;
                  button.addAction("add", click -> {
                     if (click.getClick() == ClickType.LEFT || click.getClick() == ClickType.SHIFT_LEFT) {
                        if (click.getCursor().getType() == Material.AIR) {
                           click.setCancelled(true);
                           Player clicker = (Player)click.getWhoClicked();
                           if (mPlayer.isRemovingWhitelistEntry()) {
                              gui.removeButton(finalSlot);
                              tableMenu.updateButtons(clicker);
                              clicker.playSound(clicker.getLocation(), "entity.enderman.teleport", 1.0F, 0.7F);
                              LangUtils.sendMessage(player, "instance.editmode.room-whitelist.remove-success", whiteRoom.getNamespace());
                              room.getRoomWhitelist().remove(entry);
                           } else {
                              clicker.playSound(clicker.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
                              int amount = 1;
                              if (click.getClick() == ClickType.SHIFT_LEFT) {
                                 amount = 5;
                              }

                              entry.setWeight(entry.getWeight() + amount);
                              updateWeightButton(button, whiteRoom, entry.getMaterial(), entry.getWeight());
                              tableMenu.updateButtons(clicker);
                           }
                        }
                     }
                  });
                  button.addAction("subtract", click -> {
                     if (click.getClick() == ClickType.RIGHT || click.getClick() == ClickType.SHIFT_RIGHT) {
                        if (click.getCursor().getType() == Material.AIR) {
                           click.setCancelled(true);
                           Player clicker = (Player)click.getWhoClicked();
                           if (mPlayer.isRemovingWhitelistEntry()) {
                              gui.removeButton(finalSlot);
                              tableMenu.updateButtons(clicker);
                              clicker.playSound(clicker.getLocation(), "entity.enderman.teleport", 1.0F, 0.7F);
                              LangUtils.sendMessage(player, "instance.editmode.room-whitelist.remove-success", whiteRoom.getNamespace());
                              room.getRoomWhitelist().remove(entry);
                           } else {
                              clicker.playSound(clicker.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
                              int amount = 1;
                              if (click.getClick() == ClickType.SHIFT_RIGHT) {
                                 amount = 5;
                              }

                              entry.setWeight(entry.getWeight() - amount);
                              updateWeightButton(button, whiteRoom, entry.getMaterial(), entry.getWeight());
                              tableMenu.updateButtons(clicker);
                           }
                        }
                     }
                  });
                  button.addAction("set_icon", click -> {
                     Material cursorMat = click.getCursor().getType();
                     if (cursorMat != Material.AIR) {
                        click.setCancelled(true);
                        Player clicker = (Player)click.getWhoClicked();
                        clicker.playSound(clicker.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.0F);
                        entry.setMaterial(cursorMat);
                        updateWeightButton(button, whiteRoom, entry.getMaterial(), entry.getWeight());
                        tableMenu.updateButtons(clicker);
                     }
                  });
                  tableMenu.getPlayersGui(player).setButton(slot, button);
                  updateWeightButton(button, whiteRoom, mat, entry.getWeight());
               }
            }

             tableMenu.updateButtons(player);
         }
      );
      ItemStack blockedSlot = ItemUtils.getBlockedMenuItem();
      tableMenu.addButton(0, new Button("blocked_0", blockedSlot));
      tableMenu.addButton(1, new Button("blocked_1", blockedSlot));
      tableMenu.addButton(2, new Button("blocked_2", blockedSlot));
      tableMenu.addButton(6, new Button("blocked_6", blockedSlot));
      tableMenu.addButton(7, new Button("blocked_7", blockedSlot));
      tableMenu.addButton(8, new Button("blocked_8", blockedSlot));
      Button addButton = new Button("whitelist_" + dungeon.getWorldName() + "_" + room.getNamespace() + "_ADD", Material.JIGSAW, "&aAdd Room");
      addButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         mPlayer.setAwaitingRoomName(true);
         mPlayer.setAddingWhitelistEntry(true);
         LangUtils.sendMessage(player, "instance.editmode.room-whitelist.add-prompt");
         player.closeInventory();
      });
      tableMenu.addButton(3, addButton);
      Button editButton = new Button("whitelist_" + dungeon.getWorldName() + "_" + room.getNamespace() + "_EDIT", Material.NAME_TAG, "&eEdit Room");
      editButton.addLore(HelperUtils.colorize("&cNO FUNCTIONALITY HERE!"));
      editButton.addLore(HelperUtils.colorize("&cThis button may be replaced with an explainer tooltip."));
      editButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getDungeonPlayer(player);
         player.sendMessage(HelperUtils.colorize("&cNO FUNCTIONALITY HERE! This button may be replaced with an explainer tooltip."));
      });
      tableMenu.addButton(4, editButton);
      Button remButton = new Button("whitelist_" + dungeon.getWorldName() + "_" + room.getNamespace() + "_REMOVE", Material.STRUCTURE_VOID, "&cRemove Room");
      remButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         event.setCancelled(true);
         if (mPlayer.isRemovingWhitelistEntry()) {
            mPlayer.setRemovingWhitelistEntry(false);
            remButton.setEnchanted(false);
            remButton.clearLore();
            remButton.addLore(HelperUtils.colorize("&eClick to &aACTIVATE &eremoval mode."));
            tableMenu.updateButtons(player);
         } else {
            mPlayer.setRemovingWhitelistEntry(true);
            remButton.setEnchanted(true);
            remButton.clearLore();
            remButton.addLore(HelperUtils.colorize("&eClick to &cDEACTIVATE &eremoval mode."));
            LangUtils.sendMessage(player, "instance.editmode.room-whitelist.remove-prompt");
            tableMenu.updateButtons(player);
         }
      });
      tableMenu.addButton(5, remButton);
      tableMenu.addCloseAction("cancel_removal", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         mPlayer.setRemovingWhitelistEntry(false);
         remButton.setEnchanted(false);
         remButton.clearLore();
         remButton.addLore(HelperUtils.colorize("&eClick to &aACTIVATE &eremoval mode."));
         tableMenu.updateButtons(player);
      });
   }

   public static void initConnectorWhitelist() {
      if (openSlotItem == null) {
         initOpenSlotItem();
      }

      GUIWindow tableMenu = new GUIWindow("connector_whitelist", 54, "&8Edit Connector Whitelist");
      tableMenu.setCancelClick(false);
      tableMenu.addOpenAction("load", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         DungeonRoomContainer room = mPlayer.getActiveRoom();
         if (room != null) {
            DungeonProcedural dungeon = room.getDungeon();
            if (dungeon != null) {
               Connector con = mPlayer.getActiveConnector();
               if (con != null) {
                  List<WhitelistEntry> entries = con.getRoomWhitelist();

                  for (int slot = 9; slot < 54; slot++) {
                     int i = slot - 9;
                     GUIInventory gui = tableMenu.getPlayersGui(player);
                     gui.removeButton(slot);
                     if (i >= entries.size()) {
                        gui.removeButton(slot);
                        if (!gui.getButtons().containsKey(slot + 1)) {
                           break;
                        }
                     } else {
                        WhitelistEntry entry = entries.get(i);
                        DungeonRoomContainer whiteRoom = entry.getRoom(dungeon);
                        Material mat = entry.getMaterial();
                        if (mat == null) {
                           mat = Material.STRUCTURE_BLOCK;
                        }

                        Button button = new Button("whitelist_" + player.getName() + "_" + slot, mat, "&b&l" + whiteRoom.getNamespace());
                        int finalSlot = slot;
                        button.addAction("add", click -> {
                           if (click.getClick() == ClickType.LEFT || click.getClick() == ClickType.SHIFT_LEFT) {
                              if (click.getCursor().getType() == Material.AIR) {
                                 click.setCancelled(true);
                                 Player clicker = (Player)click.getWhoClicked();
                                 if (mPlayer.isRemovingWhitelistEntry()) {
                                    gui.removeButton(finalSlot);
                                    tableMenu.updateButtons(clicker);
                                    clicker.playSound(clicker.getLocation(), "entity.enderman.teleport", 1.0F, 0.7F);
                                    LangUtils.sendMessage(player, "instance.editmode.room-whitelist.remove-success", whiteRoom.getNamespace());
                                    con.getRoomWhitelist().remove(entry);
                                 } else {
                                    clicker.playSound(clicker.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
                                    int amount = 1;
                                    if (click.getClick() == ClickType.SHIFT_LEFT) {
                                       amount = 5;
                                    }

                                    entry.setWeight(entry.getWeight() + amount);
                                    updateWeightButton(button, whiteRoom, entry.getMaterial(), entry.getWeight());
                                    tableMenu.updateButtons(clicker);
                                 }
                              }
                           }
                        });
                        button.addAction("subtract", click -> {
                           if (click.getClick() == ClickType.RIGHT || click.getClick() == ClickType.SHIFT_RIGHT) {
                              if (click.getCursor().getType() == Material.AIR) {
                                 click.setCancelled(true);
                                 Player clicker = (Player)click.getWhoClicked();
                                 if (mPlayer.isRemovingWhitelistEntry()) {
                                    gui.removeButton(finalSlot);
                                    tableMenu.updateButtons(clicker);
                                    clicker.playSound(clicker.getLocation(), "entity.enderman.teleport", 1.0F, 0.7F);
                                    LangUtils.sendMessage(player, "instance.editmode.room-whitelist.remove-success", whiteRoom.getNamespace());
                                    con.getRoomWhitelist().remove(entry);
                                 } else {
                                    clicker.playSound(clicker.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.7F);
                                    int amount = 1;
                                    if (click.getClick() == ClickType.SHIFT_RIGHT) {
                                       amount = 5;
                                    }

                                    entry.setWeight(entry.getWeight() - amount);
                                    updateWeightButton(button, whiteRoom, entry.getMaterial(), entry.getWeight());
                                    tableMenu.updateButtons(clicker);
                                 }
                              }
                           }
                        });
                        button.addAction("set_icon", click -> {
                           Material cursorMat = click.getCursor().getType();
                           if (cursorMat != Material.AIR) {
                              click.setCancelled(true);
                              Player clicker = (Player)click.getWhoClicked();
                              clicker.playSound(clicker.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.0F);
                              entry.setMaterial(cursorMat);
                              updateWeightButton(button, whiteRoom, entry.getMaterial(), entry.getWeight());
                              tableMenu.updateButtons(clicker);
                           }
                        });
                        gui.setButton(slot, button);
                        updateWeightButton(button, whiteRoom, mat, entry.getWeight());
                     }
                  }

                  tableMenu.updateButtons(player);
               }
            }
         }
      });
      ItemStack blockedSlot = ItemUtils.getBlockedMenuItem();
      tableMenu.addButton(0, new Button("blocked_0", blockedSlot));
      tableMenu.addButton(1, new Button("blocked_1", blockedSlot));
      tableMenu.addButton(2, new Button("blocked_2", blockedSlot));
      tableMenu.addButton(6, new Button("blocked_6", blockedSlot));
      tableMenu.addButton(7, new Button("blocked_7", blockedSlot));
      tableMenu.addButton(8, new Button("blocked_8", blockedSlot));
      Button addButton = new Button("connector_whitelist_ADD", Material.JIGSAW, "&aAdd Room");
      addButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         event.setCancelled(true);
         mPlayer.setAwaitingRoomName(true);
         mPlayer.setAddingWhitelistEntry(true);
         LangUtils.sendMessage(player, "instance.editmode.room-whitelist.add-prompt");
         player.closeInventory();
      });
      tableMenu.addButton(3, addButton);
      Button editButton = new Button("connector_whitelist_EDIT", Material.NAME_TAG, "&eEdit Room");
      editButton.addLore(HelperUtils.colorize("&cNO FUNCTIONALITY HERE!"));
      editButton.addLore(HelperUtils.colorize("&cThis button may be replaced with an explainer tooltip."));
      editButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getDungeonPlayer(player);
         player.sendMessage(HelperUtils.colorize("&cNO FUNCTIONALITY HERE! This button may be replaced with an explainer tooltip."));
      });
      tableMenu.addButton(4, editButton);
      Button remButton = new Button("connector_whitelist_REMOVE", Material.STRUCTURE_VOID, "&cRemove Room");
      remButton.addLore(HelperUtils.colorize("&eClick to &aACTIVATE &eremoval mode."));
      remButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         event.setCancelled(true);
         if (mPlayer.isRemovingWhitelistEntry()) {
            mPlayer.setRemovingWhitelistEntry(false);
            remButton.setEnchanted(false);
            remButton.clearLore();
            remButton.addLore(HelperUtils.colorize("&eClick to &aACTIVATE &eremoval mode."));
            tableMenu.updateButtons(player);
         } else {
            mPlayer.setRemovingWhitelistEntry(true);
            remButton.setEnchanted(true);
            remButton.clearLore();
            remButton.addLore(HelperUtils.colorize("&eClick to &cDEACTIVATE &eremoval mode."));
            LangUtils.sendMessage(player, "instance.editmode.room-whitelist.remove-prompt");
            tableMenu.updateButtons(player);
         }
      });
      tableMenu.addButton(5, remButton);
      tableMenu.addCloseAction("cancel_removal", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         mPlayer.setRemovingWhitelistEntry(false);
         remButton.setEnchanted(false);
         remButton.clearLore();
         remButton.addLore(HelperUtils.colorize("&eClick to &aACTIVATE &eremoval mode."));
         tableMenu.updateButtons(player);
      });
   }

   private static void updateWeightButton(Button weightButton, DungeonRoomContainer room, Material mat, double weight) {
      if (mat != null) {
         weightButton.setItem(new ItemStack(mat));
      }

      weightButton.setDisplayName("&b&l" + room.getNamespace());
      weightButton.addLore(HelperUtils.colorize("&eWeight of &6" + weight));
      weightButton.addLore(HelperUtils.colorize(""));
      weightButton.addLore(HelperUtils.colorize("&7Determines the chance of this room"));
      weightButton.addLore(HelperUtils.colorize("&7compared to others. A room with a"));
      weightButton.addLore(HelperUtils.colorize("&7weight of 4 is twice as common as a"));
      weightButton.addLore(HelperUtils.colorize("&7room with a weight of 2."));
      weightButton.addLore(HelperUtils.colorize(""));
      weightButton.addLore(HelperUtils.colorize("&8Left and Shift-Left click increases."));
      weightButton.addLore(HelperUtils.colorize("&8Right and Shift-Right click decreases."));
      weightButton.addLore(HelperUtils.colorize("&8Click with an item to set icon."));
   }
}
