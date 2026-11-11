package nl.hauntedmc.dungeons.gui.menu;

import java.lang.reflect.InvocationTargetException;
import java.util.Map.Entry;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.entity.Player;

/** Builds the conditions editor workflow. */
final class ConditionEditorMenus {
    private ConditionEditorMenus() {}

    /** Builds and registers all trigger-condition editor GUIs. */
    static void initializeConditionsMenus() {
        initializeConditionsBrowser();
        initializeConditionsEditor();
        initializeConditionsRemover();
    }

    /** Builds and registers the trigger-condition browser GUI. */
    private static void initializeConditionsBrowser() {
        GuiWindow gui =
                EditorMenuSupport.editorWindow("conditionsmenu", 54, "&8Select a Condition");
        int i = 0;

        for (Entry<String, MenuButton> pair :
                RuntimeContext.conditionRegistry().getConditionButtons().entrySet()) {
            String conditionName = pair.getKey();
            MenuButton menuButton = pair.getValue();
            Button button = new Button("trigger_" + i, menuButton.getItem());
            button.addAction(
                    "click",
                    event -> {
                        Player player = (Player) event.getWhoClicked();
                        DungeonPlayerSession playerSession =
                                RuntimeContext.playerSessions().get(player);

                        try {
                            DungeonTrigger trigger = playerSession.getActiveTrigger();
                            TriggerCondition condition =
                                    RuntimeContext.conditionRegistry()
                                            .getConditionClass(conditionName)
                                            .getDeclaredConstructor()
                                            .newInstance();
                            condition.setTrigger(trigger);
                            condition.initialize();
                            trigger.addCondition(condition);
                            player.closeInventory();
                            playerSession.setTargetLocation(null);
                            if (playerSession.getSavedHotbar() == null) {
                                playerSession.captureAndShowHotbar(condition.getMenu());
                            } else {
                                playerSession.showHotbar(condition.getMenu());
                            }
                        } catch (IllegalAccessException
                                | InvocationTargetException
                                | NoSuchMethodException
                                | InstantiationException exception) {
                            RuntimeContext.plugin()
                                    .getSLF4JLogger()
                                    .error(
                                            "Failed to create condition '{}' from the condition"
                                                    + " browser for player '{}'.",
                                            conditionName,
                                            player.getName(),
                                            exception);
                        }
                    });
            gui.addButton(i, button);
            i++;
        }
    }

    /** Builds and registers the trigger-condition edit GUI. */
    private static void initializeConditionsEditor() {
        GuiWindow gui = EditorMenuSupport.editorWindow("editcondition", 54, "&8Edit a Condition");
        gui.addOpenAction(
                "populate",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession =
                            RuntimeContext.playerSessions().get(player);
                    DungeonTrigger trigger = playerSession.getActiveTrigger();
                    GuiInventory guiInv = gui.getInventoryFor(player);

                    for (int x = 0; x < 27; x++) {
                        guiInv.removeButton(x);
                    }

                    int slot = 0;

                    for (TriggerCondition condition : trigger.getConditions()) {
                        MenuButton menuButton =
                                RuntimeContext.conditionRegistry()
                                        .getConditionButton(condition.getClass());
                        if (menuButton != null) {
                            Button button = new Button("condition_" + slot, menuButton.getItem());
                            button.addLore("");
                            button.addLore(ColorUtils.colorize("&6Options:"));
                            EditorMenuSupport.addPersistedFieldLore(
                                    button, condition, false, "condition");

                            button.addAction(
                                    "click",
                                    clickEvent -> {
                                        Player clickPlayer = (Player) clickEvent.getWhoClicked();
                                        DungeonPlayerSession clickAPlayer =
                                                RuntimeContext.playerSessions().get(clickPlayer);
                                        clickPlayer.closeInventory();
                                        clickAPlayer.showHotbar(condition.getMenu());
                                    });
                            guiInv.setButton(slot, button);
                            slot++;
                        }
                    }
                });
    }

    /** Builds and registers the trigger-condition removal GUI. */
    private static void initializeConditionsRemover() {
        GuiWindow gui =
                EditorMenuSupport.editorWindow("removecondition", 54, "&8Remove a Condition");
        gui.addOpenAction(
                "populate", event -> reloadConditionsRemovalGui(gui, (Player) event.getPlayer()));
    }

    /** Reloads condition-removal buttons for the active player's trigger. */
    private static void reloadConditionsRemovalGui(GuiWindow gui, Player player) {
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        DungeonTrigger trigger = playerSession.getActiveTrigger();
        GuiInventory guiInv = gui.getInventoryFor(player);

        for (int x = 0; x < 27; x++) {
            guiInv.removeButton(x);
        }

        int slot = 0;

        for (TriggerCondition condition : trigger.getConditions()) {
            MenuButton menuButton =
                    RuntimeContext.conditionRegistry().getConditionButton(condition.getClass());
            if (menuButton != null) {
                Button button = new Button("condition_" + slot, menuButton.getItem());
                button.addAction(
                        "click",
                        clickEvent -> {
                            Player clickPlayer = (Player) clickEvent.getWhoClicked();
                            trigger.removeCondition(condition);
                            LangUtils.sendMessage(
                                    clickPlayer,
                                    "menus.remove.condition-from-trigger",
                                    LangUtils.placeholder(
                                            "name",
                                            ComponentUtils.component(menuButton.getDisplayName())));
                            reloadConditionsRemovalGui(gui, clickPlayer);
                        });
                guiInv.setButton(slot, button);
                slot++;
            }
        }

        gui.updateButtons(player);
    }
}
