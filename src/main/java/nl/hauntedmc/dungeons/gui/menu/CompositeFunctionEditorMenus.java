package nl.hauntedmc.dungeons.gui.menu;
import nl.hauntedmc.dungeons.content.function.meta.CompositeFunction;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;

import org.bukkit.entity.Player;

/** Builds the composites editor workflow. */
final class CompositeFunctionEditorMenus {
    private CompositeFunctionEditorMenus() {}

    /** Builds and registers all composite-function editor GUIs. */
    static void initializeCompositeFunctionMenus() {
        initializeCompositeFunctionEditor();
        initializeCompositeFunctionRemover();
        initializeCompositeFunctionTriggerEditor();
    }

    /** Builds and registers the composite-function edit GUI. */
    private static void initializeCompositeFunctionEditor() {
        GuiWindow gui = EditorMenuSupport.editorWindow("editmultifunction", 54, "&8Edit a Function");
        gui.addOpenAction(
                "populate",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession =
                            RuntimeContext.playerSessions().get(player);
                    if (playerSession.getActiveFunction() instanceof CompositeFunction function) {
                        GuiInventory guiInv = gui.getInventoryFor(player);

                        for (int x = 0; x < 54; x++) {
                            guiInv.removeButton(x);
                        }

                        int slot = 0;

                        for (DungeonFunction targetFunction : function.getFunctions()) {
                            MenuButton menuButton =
                                    RuntimeContext.functionRegistry()
                                            .getFunctionButton(targetFunction.getClass());
                            if (menuButton != null) {
                                Button button =
                                        new Button("function_" + slot, menuButton.getItem());
                                button.addLore("");
                                button.addLore(ColorUtils.colorize("&6Options:"));
                                EditorMenuSupport.addPersistedFieldLore(button, targetFunction, true, "function");

                                if (targetFunction instanceof CompositeFunction subMulti) {
                                    button.addLore(ColorUtils.colorize(" &eFunctions:"));

                                    for (DungeonFunction subFunction : subMulti.getFunctions()) {
                                        button.addLore(
                                                ColorUtils.colorize(
                                                        "  &e- &7" + subFunction.getDisplayName()));
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
                                            clickAPlayer.showHotbar(targetFunction.getMenu());
                                        });
                                guiInv.setButton(slot, button);
                                slot++;
                            }
                        }
                    }
                });
    }

    /** Builds and registers the composite-function removal GUI. */
    private static void initializeCompositeFunctionRemover() {
        GuiWindow gui = EditorMenuSupport.editorWindow("removemultifunction", 54, "&8Remove a Function");
        gui.addOpenAction(
                "populate",
                event -> reloadCompositeFunctionRemovalGui(gui, (Player) event.getPlayer()));
    }

    /** Reloads composite-function removal buttons for the active player's function. */
    private static void reloadCompositeFunctionRemovalGui(GuiWindow gui, Player player) {
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        if (playerSession.getActiveFunction() instanceof CompositeFunction function) {
            GuiInventory guiInv = gui.getInventoryFor(player);

            for (int x = 0; x < 54; x++) {
                guiInv.removeButton(x);
            }

            int slot = 0;

            for (DungeonFunction targetFunction : function.getFunctions()) {
                MenuButton menuButton =
                        RuntimeContext.functionRegistry()
                                .getFunctionButton(targetFunction.getClass());
                if (menuButton != null) {
                    Button button = new Button("function_" + slot, menuButton.getItem());
                    button.addAction(
                            "click",
                            clickEvent -> {
                                Player clickPlayer = (Player) clickEvent.getWhoClicked();
                                function.removeFunction(targetFunction);
                                LangUtils.sendMessage(
                                        clickPlayer,
                                        "menus.remove.function",
                                        LangUtils.placeholder("name", ComponentUtils.component(menuButton.getDisplayName())));
                                reloadCompositeFunctionRemovalGui(gui, clickPlayer);
                            });
                    guiInv.setButton(slot, button);
                    slot++;
                }
            }

            gui.updateButtons(player);
        }
    }

    /** Builds and registers the composite-function trigger-link GUI. */
    private static void initializeCompositeFunctionTriggerEditor() {
        GuiWindow gui = EditorMenuSupport.editorWindow("editmultifunctiontriggers", 54, "&8Edit Function Triggers");
        gui.addOpenAction(
                "populate",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession =
                            RuntimeContext.playerSessions().get(player);
                    if (playerSession.getActiveFunction() instanceof CompositeFunction function) {
                        GuiInventory guiInv = gui.getInventoryFor(player);

                        for (int x = 0; x < 54; x++) {
                            guiInv.removeButton(x);
                        }

                        int slot = 0;

                        for (DungeonFunction targetFunction : function.getFunctions()) {
                            MenuButton menuButton =
                                    RuntimeContext.functionRegistry()
                                            .getFunctionButton(targetFunction.getClass());
                            if (menuButton != null) {
                                Button button =
                                        new Button("function_" + slot, menuButton.getItem());
                                button.addLore("");
                                button.addLore(ColorUtils.colorize("&6Options:"));
                                EditorMenuSupport.addPersistedFieldLore(
                                        button, targetFunction, true, "function trigger");

                                if (targetFunction instanceof CompositeFunction subMulti) {
                                    button.addLore(ColorUtils.colorize(" &eFunctions:"));

                                    for (DungeonFunction subFunction : subMulti.getFunctions()) {
                                        button.addLore(
                                                ColorUtils.colorize(
                                                        "  &e- &7" + subFunction.getDisplayName()));
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
                                            clickAPlayer.setActiveFunction(targetFunction);
                                            RuntimeContext.guiService()
                                                    .openGui(clickPlayer, "triggermenu");
                                        });
                                guiInv.setButton(slot, button);
                                slot++;
                            }
                        }
                    }
                });
    }
}
