package nl.hauntedmc.dungeons.gui.menu;

import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Builds the function editor hotbar workflow. */
final class FunctionEditHotbar {
    private FunctionEditHotbar() {}

    private static PlayerHotbarMenu functionEditMenu;

    /** Builds the editor hotbar menu used for function editing actions. */
    static void initializeFunctionEditMenu() {
        functionEditMenu = PlayerHotbarMenu.createMenu();
        functionEditMenu.addMenuItem(createEditFunctionItem());
        functionEditMenu.addMenuItem(createChangeTriggerItem());
        functionEditMenu.addMenuItem(createCopyFunctionItem());
        functionEditMenu.addMenuItem(createCutFunctionItem());
        functionEditMenu.addMenuItem(createPasteFunctionItem());
        functionEditMenu.addMenuItem(createDeleteFunctionItem());
    }

    /** Returns the initialized function-edit hotbar menu. */
    static PlayerHotbarMenu getFunctionEditMenu() {
        return functionEditMenu;
    }

    private static Location normalizeFunctionLocation(Location location) {
        Location normalized = location.clone();
        normalized.setWorld(null);
        return normalized;
    }

    private static MenuItem createEditFunctionItem() {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.COMMAND_BLOCK);
                this.button.setDisplayName("&a&lEdit Function");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                DungeonPlayerSession playerSession =
                        RuntimeContext.playerSessions().get(player);
                playerSession.showHotbar(playerSession.getActiveFunction().getMenu(), true);
            }
        };
    }

    private static MenuItem createChangeTriggerItem() {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
                this.button.setDisplayName("&e&lChange Trigger");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                RuntimeContext.guiService().openGui(player, "triggermenu");
            }
        };
    }

    private static MenuItem createCopyFunctionItem() {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.BOOK);
                this.button.setDisplayName("&3&lCopy Function");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                DungeonPlayerSession playerSession =
                        RuntimeContext.playerSessions().get(player);
                if (playerSession.getActiveFunction() == null) {
                    LangUtils.sendMessage(player, "editor.session.tip-edit-existing");
                    return;
                }

                playerSession.beginFunctionCopy(playerSession.getActiveFunction());
                LangUtils.sendMessage(player, "editor.session.hotbar.function-copied");
            }
        };
    }

    private static MenuItem createCutFunctionItem() {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.SHEARS);
                this.button.setDisplayName("&3&lCut Function");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                DungeonPlayerSession playerSession =
                        RuntimeContext.playerSessions().get(player);
                if (playerSession.getActiveFunction() == null) {
                    LangUtils.sendMessage(player, "editor.session.tip-edit-existing");
                    return;
                }

                playerSession.beginFunctionCut(playerSession.getActiveFunction());
                LangUtils.sendMessage(player, "editor.session.hotbar.function-cut");
            }
        };
    }

    private static MenuItem createPasteFunctionItem() {
        return new MenuItem() {
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
                        DungeonPlayerSession playerSession =
                                RuntimeContext.playerSessions().get(player);
                        EditableInstance instance =
                                playerSession.getInstance().asEditInstance();
                        if (instance != null) {
                            Location targetLocation;
                            if (interactEvent.getClickedBlock() != null) {
                                targetLocation =
                                        normalizeFunctionLocation(
                                                interactEvent
                                                        .getClickedBlock()
                                                        .getLocation());
                            } else {
                                LangUtils.sendMessage(
                                        player,
                                        "editor.session.hotbar.look-at-block-paste");
                                return;
                            }
                            DungeonDefinition dungeon = instance.getDungeon();
                            if (playerSession.isFunctionCopying()) {
                                DungeonFunction copiedFunction =
                                        playerSession.getCopiedFunction();
                                if (copiedFunction == null) {
                                    LangUtils.sendMessage(
                                            player, "editor.session.tip-edit-existing");
                                    playerSession.clearFunctionClipboard();
                                    return;
                                }
                                if (dungeon.getFunctions().containsKey(targetLocation)) {
                                    LangUtils.sendMessage(
                                            player,
                                            "editor.session.hotbar.function-already-here");
                                } else {
                                    DungeonFunction pastedFunction = copiedFunction.clone();
                                    if (pastedFunction == null) {
                                        LangUtils.sendMessage(
                                                player, "editor.session.tip-edit-existing");
                                        return;
                                    }

                                    dungeon.addFunction(targetLocation, pastedFunction);
                                    playerSession.setActiveFunction(pastedFunction);
                                    instance.trackEditorFunction(pastedFunction);
                                    instance.addFunctionLabel(pastedFunction);
                                    LangUtils.sendMessage(
                                            player,
                                            "editor.session.hotbar.function-pasted");
                                }
                            } else {
                                if (playerSession.isFunctionCutting()) {
                                    if (dungeon.getFunctions()
                                            .containsKey(targetLocation)) {
                                        LangUtils.sendMessage(
                                                player,
                                                "editor.session.hotbar.function-already-here");
                                        return;
                                    }

                                    DungeonFunction cutFunction =
                                            playerSession.getCopiedFunction();
                                    if (cutFunction == null) {
                                        playerSession.clearFunctionClipboard();
                                        LangUtils.sendMessage(
                                                player, "editor.session.tip-edit-existing");
                                        return;
                                    }

                                    instance.removeFunctionLabelByFunction(cutFunction);
                                    instance.untrackEditorFunction(cutFunction);
                                    dungeon.removeFunction(cutFunction.getLocation());
                                    dungeon.addFunction(targetLocation, cutFunction);
                                    instance.trackEditorFunction(cutFunction);
                                    instance.addFunctionLabel(cutFunction);
                                    playerSession.setActiveFunction(cutFunction);
                                    LangUtils.sendMessage(
                                            player,
                                            "editor.session.hotbar.function-cut-pasted");
                                    playerSession.clearFunctionClipboard();
                                }
                            }
                        }
                    }
                }
            }
        };
    }

    private static MenuItem createDeleteFunctionItem() {
        return new MenuItem() {
            @Override
            public void buildButton() {
                this.button = new MenuButton(Material.BARRIER);
                this.button.setDisplayName("&c&lDelete Function");
            }

            @Override
            public void onSelect(PlayerEvent event) {
                Player player = event.getPlayer();
                DungeonPlayerSession playerSession =
                        RuntimeContext.playerSessions().get(player);
                EditableInstance instance = playerSession.getInstance().asEditInstance();
                if (instance != null) {
                    DungeonFunction activeFunction = playerSession.getActiveFunction();
                    if (activeFunction == null) {
                        playerSession.restoreCapturedHotbar();
                        return;
                    }
                    instance.getDungeon().removeFunction(activeFunction.getLocation());
                    instance.untrackEditorFunction(activeFunction);
                    instance.removeFunctionLabelByFunction(activeFunction);
                    if (playerSession.isFunctionCutting()
                            && playerSession.getCopiedFunction() == activeFunction) {
                        playerSession.clearFunctionClipboard();
                    }
                    playerSession.setActiveFunction(null);
                    playerSession.setActiveTrigger(null);
                    playerSession.setTargetLocation(null);
                    LangUtils.sendMessage(player, "editor.session.hotbar.function-deleted");
                    playerSession.restoreCapturedHotbar();
                }
            }
        };
    }
}
