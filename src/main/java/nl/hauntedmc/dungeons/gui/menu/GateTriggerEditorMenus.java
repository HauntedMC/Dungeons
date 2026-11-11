package nl.hauntedmc.dungeons.gui.menu;

import java.lang.reflect.InvocationTargetException;
import java.util.Map.Entry;
import nl.hauntedmc.dungeons.content.trigger.gate.GateTrigger;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.entity.Player;

/** Builds the gates editor workflow. */
final class GateTriggerEditorMenus {
    private GateTriggerEditorMenus() {}

    /** Builds and registers all trigger-gate editor GUIs. */
    static void initializeGateTriggerMenus() {
        initializeGateTriggerBrowser();
        initializeGateTriggerEditor();
        initializeGateTriggerRemover();
    }

    /** Builds and registers the trigger-gate browser GUI. */
    private static void initializeGateTriggerBrowser() {
        GuiWindow gui =
                EditorMenuSupport.editorWindow("gatetriggersmenu", 54, "&8Select a Trigger");
        int i = 0;

        for (Entry<String, MenuButton> pair :
                RuntimeContext.triggerRegistry().getTriggerButtons().entrySet()) {
            String triggerName = pair.getKey();
            MenuButton menuButton = pair.getValue();
            Button button = new Button("trigger_" + i, menuButton.getItem());
            button.addAction(
                    "click",
                    event -> {
                        Player player = (Player) event.getWhoClicked();
                        DungeonPlayerSession playerSession =
                                RuntimeContext.playerSessions().get(player);

                        try {
                            if (!(playerSession.getActiveTrigger() instanceof GateTrigger gate)) {
                                return;
                            }

                            DungeonTrigger trigger =
                                    RuntimeContext.triggerRegistry()
                                            .getTriggerClass(triggerName)
                                            .getDeclaredConstructor()
                                            .newInstance();
                            trigger.initialize();
                            gate.addTrigger(trigger);
                            player.closeInventory();
                            playerSession.setTargetLocation(null);
                            if (playerSession.getSavedHotbar() == null) {
                                playerSession.captureAndShowHotbar(trigger.getMenu());
                            } else {
                                playerSession.showHotbar(trigger.getMenu());
                            }
                        } catch (IllegalAccessException
                                | InvocationTargetException
                                | NoSuchMethodException
                                | InstantiationException exception) {
                            RuntimeContext.plugin()
                                    .getSLF4JLogger()
                                    .error(
                                            "Failed to create nested trigger '{}' for player '{}'.",
                                            triggerName,
                                            player.getName(),
                                            exception);
                        }
                    });
            gui.addButton(i, button);
            i++;
        }
    }

    /** Builds and registers the trigger-gate edit GUI. */
    private static void initializeGateTriggerEditor() {
        GuiWindow gui = EditorMenuSupport.editorWindow("editgatetrigger", 54, "&8Edit a Trigger");
        gui.addOpenAction(
                "populate",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession =
                            RuntimeContext.playerSessions().get(player);
                    if (playerSession.getActiveTrigger() instanceof GateTrigger gate) {
                        GuiInventory guiInv = gui.getInventoryFor(player);

                        for (int x = 0; x < 54; x++) {
                            guiInv.removeButton(x);
                        }

                        int slot = 0;

                        for (DungeonTrigger trigger : gate.getTriggers()) {
                            MenuButton menuButton =
                                    RuntimeContext.triggerRegistry()
                                            .getTriggerButton(trigger.getClass());
                            if (menuButton != null) {
                                Button button = new Button("trigger_" + slot, menuButton.getItem());
                                button.addLore("");
                                button.addLore(ColorUtils.colorize("&6Options:"));
                                EditorMenuSupport.addPersistedFieldLore(
                                        button, trigger, false, "trigger");

                                if (trigger instanceof GateTrigger subGate) {
                                    button.addLore(ColorUtils.colorize(" &eTriggers:"));

                                    for (DungeonTrigger subTrig : subGate.getTriggers()) {
                                        button.addLore(
                                                ColorUtils.colorize(
                                                        " &e- &7" + subTrig.getDisplayName()));
                                    }
                                }

                                button.addAction(
                                        "click",
                                        clickEvent -> {
                                            Player clickPlayer =
                                                    (Player) clickEvent.getWhoClicked();
                                            DungeonPlayerSession clickAPlayer =
                                                    RuntimeContext.playerSessions()
                                                            .get(clickPlayer);
                                            clickPlayer.closeInventory();
                                            clickAPlayer.showHotbar(trigger.getMenu());
                                        });
                                guiInv.setButton(slot, button);
                                slot++;
                            }
                        }
                    }
                });
    }

    /** Builds and registers the trigger-gate removal GUI. */
    private static void initializeGateTriggerRemover() {
        GuiWindow gui =
                EditorMenuSupport.editorWindow("removegatetrigger", 54, "&8Remove a Trigger");
        gui.addOpenAction(
                "populate", event -> reloadTriggerRemovalGui(gui, (Player) event.getPlayer()));
    }

    /** Reloads trigger-removal buttons for the active player's trigger. */
    private static void reloadTriggerRemovalGui(GuiWindow gui, Player player) {
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        if (playerSession.getActiveTrigger() instanceof GateTrigger gate) {
            GuiInventory guiInv = gui.getInventoryFor(player);

            for (int x = 0; x < 54; x++) {
                guiInv.removeButton(x);
            }

            int slot = 0;

            for (DungeonTrigger trigger : gate.getTriggers()) {
                MenuButton menuButton =
                        RuntimeContext.triggerRegistry().getTriggerButton(trigger.getClass());
                if (menuButton != null) {
                    Button button = new Button("trigger_" + slot, menuButton.getItem());
                    button.addAction(
                            "click",
                            clickEvent -> {
                                Player clickPlayer = (Player) clickEvent.getWhoClicked();
                                gate.removeTrigger(trigger);
                                LangUtils.sendMessage(
                                        clickPlayer,
                                        "menus.remove.trigger",
                                        LangUtils.placeholder(
                                                "name",
                                                ComponentUtils.component(
                                                        menuButton.getDisplayName())));
                                reloadTriggerRemovalGui(gui, clickPlayer);
                            });
                    guiInv.setButton(slot, button);
                    slot++;
                }
            }

            gui.updateButtons(player);
        }
    }
}
