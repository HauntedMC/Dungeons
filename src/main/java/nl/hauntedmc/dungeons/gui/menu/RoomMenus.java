package nl.hauntedmc.dungeons.gui.menu;

import java.util.List;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.WhitelistEntry;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Inventory menu builders for branching room editing.
 */
public class RoomMenus {
    private static ItemStack openSlotItem;

    /** Initializes the default open-slot placeholder item used in whitelist menus. */
    private static void initializeOpenSlotItem() {
        openSlotItem = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = openSlotItem.getItemMeta();

        assert meta != null;

        meta.displayName(
                ComponentUtils.component(LangUtils.getMessage("menus.common.empty-slot", false)));
        openSlotItem.setItemMeta(meta);
    }

    /** Builds and registers the room whitelist editor GUI. */
    public static void initializeRoomWhitelistMenu(
            BranchingDungeon dungeon, BranchingRoomDefinition room) {
        if (openSlotItem == null) {
            initializeOpenSlotItem();
        }

        GuiWindow tableMenu =
                                new GuiWindow(
                        "whitelist_" + dungeon.getWorldName() + "_" + room.getNamespace(),
                        54,
                        "&8Edit Room Whitelist");
        tableMenu.setCancelClick(false);
        tableMenu.addOpenAction(
                "load",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);

                    for (int slot = 9; slot < 54; slot++) {
                        int i = slot - 9;
                        GuiInventory gui = tableMenu.getInventoryFor(player);
                        if (i >= room.getRoomWhitelist().size()) {
                            gui.removeButton(slot);
                            if (!gui.buttons().containsKey(slot + 1)) {
                                break;
                            }
                        } else {
                            WhitelistEntry entry = room.getRoomWhitelist().get(i);
                            BranchingRoomDefinition whiteRoom = entry.getRoom(dungeon);
                            Material mat = entry.getMaterial();
                            if (mat == null) {
                                mat = Material.STRUCTURE_BLOCK;
                            }

                            // The room whitelist menu doubles as both a weight editor and an icon
                            // picker, so each button carries multiple click behaviors.
                            Button button =
                                                                        new Button(
                                            "whitelist_"
                                                    + dungeon.getWorldName()
                                                    + "_"
                                                    + room.getNamespace()
                                                    + "_"
                                                    + slot,
                                            mat,
                                            "&b&l" + whiteRoom.getNamespace());
                            int finalSlot = slot;
                            button.addAction(
                                    "add",
                                    click -> {
                                        if (click.getClick() == ClickType.LEFT
                                                || click.getClick() == ClickType.SHIFT_LEFT) {
                                            if (click.getCursor().getType() == Material.AIR) {
                                                click.setCancelled(true);
                                                Player clicker = (Player) click.getWhoClicked();
                                                if (playerSession.isRemovingWhitelistEntry()) {
                                                    gui.removeButton(finalSlot);
                                                    tableMenu.updateButtons(clicker);
                                                    clicker.playSound(
                                                            clicker.getLocation(), "entity.enderman.teleport", 1.0F, 0.7F);
                                                    LangUtils.sendMessage(
                                                            player,
                                                            "editor.session.room-whitelist.remove-success",
                                                            LangUtils.placeholder("room", whiteRoom.getNamespace()));
                                                    room.getRoomWhitelist().remove(entry);
                                                } else {
                                                    clicker.playSound(
                                                            clicker.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
                                                    int amount = 1;
                                                    if (click.getClick() == ClickType.SHIFT_LEFT) {
                                                        amount = 5;
                                                    }

                                                    entry.setWeight(entry.getWeight() + amount);
                                                    updateWeightButton(
                                                            button, whiteRoom, entry.getMaterial(), entry.getWeight());
                                                    tableMenu.updateButtons(clicker);
                                                }
                                            }
                                        }
                                    });
                            button.addAction(
                                    "subtract",
                                    click -> {
                                        if (click.getClick() == ClickType.RIGHT
                                                || click.getClick() == ClickType.SHIFT_RIGHT) {
                                            if (click.getCursor().getType() == Material.AIR) {
                                                click.setCancelled(true);
                                                Player clicker = (Player) click.getWhoClicked();
                                                if (playerSession.isRemovingWhitelistEntry()) {
                                                    gui.removeButton(finalSlot);
                                                    tableMenu.updateButtons(clicker);
                                                    clicker.playSound(
                                                            clicker.getLocation(), "entity.enderman.teleport", 1.0F, 0.7F);
                                                    LangUtils.sendMessage(
                                                            player,
                                                            "editor.session.room-whitelist.remove-success",
                                                            LangUtils.placeholder("room", whiteRoom.getNamespace()));
                                                    room.getRoomWhitelist().remove(entry);
                                                } else {
                                                    clicker.playSound(
                                                            clicker.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
                                                    int amount = 1;
                                                    if (click.getClick() == ClickType.SHIFT_RIGHT) {
                                                        amount = 5;
                                                    }

                                                    entry.setWeight(entry.getWeight() - amount);
                                                    updateWeightButton(
                                                            button, whiteRoom, entry.getMaterial(), entry.getWeight());
                                                    tableMenu.updateButtons(clicker);
                                                }
                                            }
                                        }
                                    });
                            button.addAction(
                                    "set_icon",
                                    click -> {
                                        Material cursorMat = click.getCursor().getType();
                                        if (cursorMat != Material.AIR) {
                                            click.setCancelled(true);
                                            Player clicker = (Player) click.getWhoClicked();
                                            clicker.playSound(
                                                    clicker.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.0F);
                                            entry.setMaterial(cursorMat);
                                            updateWeightButton(button, whiteRoom, entry.getMaterial(), entry.getWeight());
                                            tableMenu.updateButtons(clicker);
                                        }
                                    });
                            tableMenu.getInventoryFor(player).setButton(slot, button);
                            updateWeightButton(button, whiteRoom, mat, entry.getWeight());
                        }
                    }

                    tableMenu.updateButtons(player);
                });
        ItemStack blockedSlot = ItemUtils.getBlockedMenuItem();
        tableMenu.addButton(0, new Button("blocked_0", blockedSlot));
        tableMenu.addButton(1, new Button("blocked_1", blockedSlot));
        tableMenu.addButton(2, new Button("blocked_2", blockedSlot));
        tableMenu.addButton(6, new Button("blocked_6", blockedSlot));
        tableMenu.addButton(7, new Button("blocked_7", blockedSlot));
        tableMenu.addButton(8, new Button("blocked_8", blockedSlot));
        Button addButton =
                                new Button(
                        "whitelist_" + dungeon.getWorldName() + "_" + room.getNamespace() + "_ADD",
                        Material.JIGSAW,
                        "&aAdd Room");
        addButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                    playerSession.setAwaitingRoomName(true);
                    playerSession.setAddingWhitelistEntry(true);
                    LangUtils.sendMessage(player, "editor.session.room-whitelist.add-prompt");
                    player.closeInventory();
                });
        tableMenu.addButton(3, addButton);
        Button editButton =
                                new Button(
                        "whitelist_" + dungeon.getWorldName() + "_" + room.getNamespace() + "_EDIT",
                        Material.NAME_TAG,
                        "&eEdit Room");
        editButton.addLore(ColorUtils.colorize("&cNO FUNCTIONALITY HERE!"));
        editButton.addLore(
                ColorUtils.colorize("&cThis button may be replaced with an explainer tooltip."));
        editButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.playerSessions().get(player);
                    LangUtils.sendMessage(player, "menus.rooms.no-functionality-here");
                });
        tableMenu.addButton(4, editButton);
        Button remButton =
                                new Button(
                        "whitelist_" + dungeon.getWorldName() + "_" + room.getNamespace() + "_REMOVE",
                        Material.STRUCTURE_VOID,
                        "&cRemove Room");
        remButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                    event.setCancelled(true);
                    if (playerSession.isRemovingWhitelistEntry()) {
                        playerSession.setRemovingWhitelistEntry(false);
                        remButton.setEnchanted(false);
                        remButton.clearLore();
                        remButton.addLore(ColorUtils.colorize("&eClick to &aACTIVATE &eremoval mode."));
                        tableMenu.updateButtons(player);
                    } else {
                        playerSession.setRemovingWhitelistEntry(true);
                        remButton.setEnchanted(true);
                        remButton.clearLore();
                        remButton.addLore(ColorUtils.colorize("&eClick to &cDEACTIVATE &eremoval mode."));
                        LangUtils.sendMessage(player, "editor.session.room-whitelist.remove-prompt");
                        tableMenu.updateButtons(player);
                    }
                });
        tableMenu.addButton(5, remButton);
        tableMenu.addCloseAction(
                "cancel_removal",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                    playerSession.setRemovingWhitelistEntry(false);
                    remButton.setEnchanted(false);
                    remButton.clearLore();
                    remButton.addLore(ColorUtils.colorize("&eClick to &aACTIVATE &eremoval mode."));
                    tableMenu.updateButtons(player);
                });
    }

    /** Builds and registers the connector whitelist editor GUI. */
    public static void initializeConnectorWhitelistMenu() {
        if (openSlotItem == null) {
            initializeOpenSlotItem();
        }

        GuiWindow tableMenu = new GuiWindow("connector_whitelist", 54, "&8Edit Connector Whitelist");
        tableMenu.setCancelClick(false);
        tableMenu.addOpenAction(
                "load",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                    BranchingRoomDefinition room = playerSession.getActiveRoom();
                    if (room != null) {
                        BranchingDungeon dungeon = room.getDungeon();
                        if (dungeon != null) {
                            Connector connector = playerSession.getActiveConnector();
                            if (connector != null) {
                                List<WhitelistEntry> entries = connector.getRoomWhitelist();

                                for (int slot = 9; slot < 54; slot++) {
                                    int i = slot - 9;
                                    GuiInventory gui = tableMenu.getInventoryFor(player);
                                    gui.removeButton(slot);
                                    if (i >= entries.size()) {
                                        gui.removeButton(slot);
                                        if (!gui.buttons().containsKey(slot + 1)) {
                                            break;
                                        }
                                    } else {
                                        WhitelistEntry entry = entries.get(i);
                                        BranchingRoomDefinition whiteRoom = entry.getRoom(dungeon);
                                        Material mat = entry.getMaterial();
                                        if (mat == null) {
                                            mat = Material.STRUCTURE_BLOCK;
                                        }

                                        Button button =
                                                                                                new Button(
                                                        "whitelist_" + player.getName() + "_" + slot,
                                                        mat,
                                                        "&b&l" + whiteRoom.getNamespace());
                                        int finalSlot = slot;
                                        button.addAction(
                                                "add",
                                                click -> {
                                                    if (click.getClick() == ClickType.LEFT
                                                            || click.getClick() == ClickType.SHIFT_LEFT) {
                                                        if (click.getCursor().getType() == Material.AIR) {
                                                            click.setCancelled(true);
                                                            Player clicker = (Player) click.getWhoClicked();
                                                            if (playerSession.isRemovingWhitelistEntry()) {
                                                                gui.removeButton(finalSlot);
                                                                tableMenu.updateButtons(clicker);
                                                                clicker.playSound(
                                                                        clicker.getLocation(), "entity.enderman.teleport", 1.0F, 0.7F);
                                                                LangUtils.sendMessage(
                                                                        player,
                                                                        "editor.session.room-whitelist.remove-success",
                                                                        LangUtils.placeholder("room", whiteRoom.getNamespace()));
                                                                connector.getRoomWhitelist().remove(entry);
                                                            } else {
                                                                clicker.playSound(
                                                                        clicker.getLocation(),
                                                                        "entity.experience_orb.pickup",
                                                                        1.0F,
                                                                        1.2F);
                                                                int amount = 1;
                                                                if (click.getClick() == ClickType.SHIFT_LEFT) {
                                                                    amount = 5;
                                                                }

                                                                entry.setWeight(entry.getWeight() + amount);
                                                                updateWeightButton(
                                                                        button, whiteRoom, entry.getMaterial(), entry.getWeight());
                                                                tableMenu.updateButtons(clicker);
                                                            }
                                                        }
                                                    }
                                                });
                                        button.addAction(
                                                "subtract",
                                                click -> {
                                                    if (click.getClick() == ClickType.RIGHT
                                                            || click.getClick() == ClickType.SHIFT_RIGHT) {
                                                        if (click.getCursor().getType() == Material.AIR) {
                                                            click.setCancelled(true);
                                                            Player clicker = (Player) click.getWhoClicked();
                                                            if (playerSession.isRemovingWhitelistEntry()) {
                                                                gui.removeButton(finalSlot);
                                                                tableMenu.updateButtons(clicker);
                                                                clicker.playSound(
                                                                        clicker.getLocation(), "entity.enderman.teleport", 1.0F, 0.7F);
                                                                LangUtils.sendMessage(
                                                                        player,
                                                                        "editor.session.room-whitelist.remove-success",
                                                                        LangUtils.placeholder("room", whiteRoom.getNamespace()));
                                                                connector.getRoomWhitelist().remove(entry);
                                                            } else {
                                                                clicker.playSound(
                                                                        clicker.getLocation(),
                                                                        "entity.experience_orb.pickup",
                                                                        1.0F,
                                                                        0.7F);
                                                                int amount = 1;
                                                                if (click.getClick() == ClickType.SHIFT_RIGHT) {
                                                                    amount = 5;
                                                                }

                                                                entry.setWeight(entry.getWeight() - amount);
                                                                updateWeightButton(
                                                                        button, whiteRoom, entry.getMaterial(), entry.getWeight());
                                                                tableMenu.updateButtons(clicker);
                                                            }
                                                        }
                                                    }
                                                });
                                        button.addAction(
                                                "set_icon",
                                                click -> {
                                                    Material cursorMat = click.getCursor().getType();
                                                    if (cursorMat != Material.AIR) {
                                                        click.setCancelled(true);
                                                        Player clicker = (Player) click.getWhoClicked();
                                                        clicker.playSound(
                                                                clicker.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.0F);
                                                        entry.setMaterial(cursorMat);
                                                        updateWeightButton(
                                                                button, whiteRoom, entry.getMaterial(), entry.getWeight());
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
        addButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                    event.setCancelled(true);
                    playerSession.setAwaitingRoomName(true);
                    playerSession.setAddingWhitelistEntry(true);
                    LangUtils.sendMessage(player, "editor.session.room-whitelist.add-prompt");
                    player.closeInventory();
                });
        tableMenu.addButton(3, addButton);
        Button editButton = new Button("connector_whitelist_EDIT", Material.NAME_TAG, "&eEdit Room");
        editButton.addLore(ColorUtils.colorize("&cNO FUNCTIONALITY HERE!"));
        editButton.addLore(
                ColorUtils.colorize("&cThis button may be replaced with an explainer tooltip."));
        editButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.playerSessions().get(player);
                    LangUtils.sendMessage(player, "menus.rooms.no-functionality-here");
                });
        tableMenu.addButton(4, editButton);
        Button remButton =
                                new Button("connector_whitelist_REMOVE", Material.STRUCTURE_VOID, "&cRemove Room");
        remButton.addLore(ColorUtils.colorize("&eClick to &aACTIVATE &eremoval mode."));
        remButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                    event.setCancelled(true);
                    if (playerSession.isRemovingWhitelistEntry()) {
                        playerSession.setRemovingWhitelistEntry(false);
                        remButton.setEnchanted(false);
                        remButton.clearLore();
                        remButton.addLore(ColorUtils.colorize("&eClick to &aACTIVATE &eremoval mode."));
                        tableMenu.updateButtons(player);
                    } else {
                        playerSession.setRemovingWhitelistEntry(true);
                        remButton.setEnchanted(true);
                        remButton.clearLore();
                        remButton.addLore(ColorUtils.colorize("&eClick to &cDEACTIVATE &eremoval mode."));
                        LangUtils.sendMessage(player, "editor.session.room-whitelist.remove-prompt");
                        tableMenu.updateButtons(player);
                    }
                });
        tableMenu.addButton(5, remButton);
        tableMenu.addCloseAction(
                "cancel_removal",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                    playerSession.setRemovingWhitelistEntry(false);
                    remButton.setEnchanted(false);
                    remButton.clearLore();
                    remButton.addLore(ColorUtils.colorize("&eClick to &aACTIVATE &eremoval mode."));
                    tableMenu.updateButtons(player);
                });
    }

    /** Updates the weight display for the currently selected whitelist entry. */
    private static void updateWeightButton(
            Button weightButton, BranchingRoomDefinition room, Material mat, double weight) {
        if (mat != null) {
            weightButton.setItem(new ItemStack(mat));
        }

        weightButton.setDisplayName("&b&l" + room.getNamespace());
        weightButton.addLore(ColorUtils.colorize("&eWeight of &6" + weight));
        weightButton.addLore(ColorUtils.colorize(""));
        weightButton.addLore(ColorUtils.colorize("&7Determines the chance of this room"));
        weightButton.addLore(ColorUtils.colorize("&7compared to others. A room with a"));
        weightButton.addLore(ColorUtils.colorize("&7weight of 4 is twice as common as a"));
        weightButton.addLore(ColorUtils.colorize("&7room with a weight of 2."));
        weightButton.addLore(ColorUtils.colorize(""));
        weightButton.addLore(ColorUtils.colorize("&8Left and Shift-Left click increases."));
        weightButton.addLore(ColorUtils.colorize("&8Right and Shift-Right click decreases."));
        weightButton.addLore(ColorUtils.colorize("&8Click with an item to set icon."));
    }
}
