package nl.hauntedmc.dungeons.gui.menu;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.content.trigger.DungeonStartTrigger;
import nl.hauntedmc.dungeons.content.trigger.KeyItemTrigger;
import nl.hauntedmc.dungeons.content.trigger.gate.GateTrigger;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.text.TextUtils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/** Builds the triggers editor workflow. */
final class TriggerBrowserMenus {
    private TriggerBrowserMenus() {}

    /** Builds and registers the trigger browser GUI. */
    static void initializeTriggerMenu() {
        GuiWindow gui = EditorMenuSupport.editorWindow("triggermenu", 27, "&8Select a Trigger");
        Button button = new Button("trigger_none", Material.BARRIER, "&cNONE");
        button.addLore(ColorUtils.fullColor("&cCreate a function without a"));
        button.addLore(ColorUtils.fullColor("&ctrigger."));
        button.addAction(
                "click",
                clickEvent -> {
                    Player player = (Player) clickEvent.getWhoClicked();
                    DungeonPlayerSession playerSession =
                            RuntimeContext.playerSessions().get(player);
                    DungeonFunction function = playerSession.getActiveFunction();
                    if (function == null) {
                        player.closeInventory();
                        return;
                    }

                    DungeonFunction parentFunction = function.getParentFunction();
                    if (parentFunction != null) {
                        function.setTrigger(null);
                    } else if (function.isRequiresTrigger()) {
                        LangUtils.sendMessage(player, "menus.functions.requires-trigger");
                        player.playSound(
                                player.getLocation(), "entity.enderman.teleport", 0.5F, 0.5F);
                        return;
                    }

                    player.closeInventory();
                    playerSession.setTargetLocation(null);
                    function.initialize();
                    EditableInstance instance = playerSession.getInstance().asEditInstance();
                    if (instance != null) {
                        instance.updateLabel(function);
                    }

                    if (playerSession.getSavedHotbar() == null) {
                        playerSession.captureAndShowHotbar(function.getMenu());
                    } else {
                        playerSession.showHotbar(function.getMenu());
                    }
                });
        gui.addButton(4, button);
        button = new Button("category_dungeon", Material.MOSSY_COBBLESTONE, "&6Dungeon Triggers");
        button.addLore(ColorUtils.colorize("&eTriggers caused by dungeon-related"));
        button.addLore(ColorUtils.colorize("&eevents."));
        button.addLore(ColorUtils.colorize(""));
        button.addLore(ColorUtils.colorize("&8Dungeon start, signals, etc."));
        button.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.guiService().openGui(player, "triggers_dungeon");
                });
        gui.addButton(11, button);
        button = new Button("category_player", Material.PLAYER_HEAD, "&playerSession Triggers");
        button.addLore(ColorUtils.colorize("&eTriggers caused by a direct player"));
        button.addLore(ColorUtils.colorize("&eor team action."));
        button.addLore(ColorUtils.colorize(""));
        button.addLore(ColorUtils.colorize("&8Right-click, player death, etc."));
        button.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.guiService().openGui(player, "triggers_player");
                });
        gui.addButton(12, button);
        button = new Button("category_meta", Material.STRUCTURE_BLOCK, "&bMeta Triggers");
        button.addLore(ColorUtils.colorize("&eTriggers contain and interact with"));
        button.addLore(ColorUtils.colorize("&eother triggers directly."));
        button.addLore(ColorUtils.colorize(""));
        button.addLore(ColorUtils.colorize("&8AND gates, OR gates, etc."));
        button.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.guiService().openGui(player, "triggers_meta");
                });
        gui.addButton(13, button);
        button = new Button("category_general", Material.COMPASS, "&5General Triggers");
        button.addLore(ColorUtils.colorize("&eTriggers caused by anything outside"));
        button.addLore(ColorUtils.colorize("&ethe other categories."));
        button.addLore(ColorUtils.colorize(""));
        button.addLore(ColorUtils.colorize("&8Mob deaths, redstone signal, etc."));
        button.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.guiService().openGui(player, "triggers_general");
                });
        gui.addButton(14, button);
        button = new Button("category_room", Material.JIGSAW, "&cRoom Triggers");
        button.addLore(ColorUtils.colorize("&eTriggers caused by something changing"));
        button.addLore(ColorUtils.colorize("&ein the room."));
        button.addLore(ColorUtils.colorize(""));
        button.addLore(ColorUtils.colorize("&8Doors opening/closing, etc."));
        button.addLore(ColorUtils.colorize("&dOnly available in branching dungeons."));
        button.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    DungeonPlayerSession playerSession =
                            RuntimeContext.playerSessions().get(player);
                    DungeonInstance instance = playerSession.getInstance();
                    if (instance != null) {
                        BranchingEditableInstance branchingEdit =
                                instance.as(BranchingEditableInstance.class);
                        if (branchingEdit == null) {
                            LangUtils.sendMessage(player, "menus.triggers.branching-only");
                        } else {
                            RuntimeContext.guiService().openGui(player, "triggers_room");
                        }
                    }
                });
        gui.addButton(15, button);
        initializeTriggerCategories();
        gui.addCloseAction(
                "checktrig",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession =
                            RuntimeContext.playerSessions().get(player);
                    DungeonFunction function = playerSession.getActiveFunction();
                    if (function == null) {
                        return;
                    }

                    if (function.getTrigger() == null) {
                        DungeonFunction parentFunction = function.getParentFunction();
                        if (function.isRequiresTrigger() && parentFunction == null) {
                            function.setTrigger(new DungeonStartTrigger());
                        }
                    }

                    if (!function.isInitialized()) {
                        function.initialize();
                    }

                    EditableInstance instance = playerSession.getInstance().asEditInstance();
                    if (instance != null) {
                        instance.updateLabel(function);
                    }
                });
    }

    /** Builds and registers per-category trigger browser GUIs. */
    private static void initializeTriggerCategories() {
        Map<TriggerCategory, Map<String, MenuButton>> categoryButtons =
                RuntimeContext.triggerRegistry().getButtonsByCategory();
        Button backButton = new Button("back", Material.RED_STAINED_GLASS_PANE, "&c&lBACK");
        backButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.guiService().openGui(player, "triggermenu");
                });

        for (TriggerCategory category : TriggerCategory.values()) {
            String catName = category.name().toLowerCase(Locale.ROOT);
            GuiWindow gui =
                    EditorMenuSupport.editorWindow(
                            "triggers_" + catName,
                            27,
                            "&8" + TextUtils.humanize(catName) + " Triggers");
            gui.addButton(0, backButton);
            int i = 1;

            for (Entry<String, MenuButton> pair : categoryButtons.get(category).entrySet()) {
                String triggerName = pair.getKey();
                MenuButton menuButton = pair.getValue();
                Button button = new Button("trigger_" + i, menuButton.getItem());
                button.addAction(
                        "click",
                        event -> {
                            Player player = (Player) event.getWhoClicked();
                            DungeonPlayerSession playerSession =
                                    RuntimeContext.playerSessions().get(player);
                            EditableInstance instance =
                                    playerSession.getInstance().asEditInstance();
                            if (instance != null) {
                                try {
                                    DungeonTrigger parentTrigger = playerSession.getActiveTrigger();
                                    if (parentTrigger instanceof GateTrigger gate) {
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

                                        instance.updateLabel(gate.getFunction());
                                        trigger.setInstance(gate.getInstance());
                                        return;
                                    }

                                    DungeonTrigger trigger =
                                            RuntimeContext.triggerRegistry()
                                                    .getTriggerClass(triggerName)
                                                    .getDeclaredConstructor()
                                                    .newInstance();
                                    DungeonFunction function = playerSession.getActiveFunction();
                                    function.setTrigger(trigger);
                                    if (!function.isInitialized()) {
                                        function.initialize();
                                    }

                                    if (!trigger.isInitialized()) {
                                        trigger.initialize();
                                    }

                                    player.closeInventory();
                                    playerSession.setTargetLocation(null);
                                    if (playerSession.getSavedHotbar() == null) {
                                        playerSession.captureAndShowHotbar(function.getMenu());
                                    } else {
                                        playerSession.showHotbar(function.getMenu());
                                    }

                                    instance.updateLabel(function);
                                    trigger.setInstance(function.getInstance());
                                } catch (IllegalAccessException
                                        | InvocationTargetException
                                        | NoSuchMethodException
                                        | InstantiationException exception) {
                                    RuntimeContext.plugin()
                                            .getSLF4JLogger()
                                            .error(
                                                    "Failed to create trigger '{}' from the trigger"
                                                        + " browser for player '{}'.",
                                                    triggerName,
                                                    player.getName(),
                                                    exception);
                                }
                            }
                        });
                gui.addButton(i, button);
                i++;
            }
        }
    }

    /** Builds and registers the trigger item-selection GUI. */
    static void initializeItemSelectTriggerMenu() {
        GuiWindow gui = EditorMenuSupport.editorWindow("selectitem_trigger", 9, "&8Put your item here");
        gui.setCancelClick(false);

        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                Button button = new Button("blocked_" + i, ItemUtils.getBlockedMenuItem());
                button.addAction("denyclick", event -> event.setCancelled(true));
                gui.addButton(i, button);
            }
        }

        gui.addOpenAction(
                "populate",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession =
                            RuntimeContext.playerSessions().get(player);
                    if (playerSession.getActiveTrigger() instanceof KeyItemTrigger trigger) {
                        Inventory inv = event.getInventory();
                        inv.setItem(4, trigger.getItem());
                    }
                });
        gui.addCloseAction(
                "save",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession =
                            RuntimeContext.playerSessions().get(player);
                    if (playerSession.getActiveTrigger() instanceof KeyItemTrigger trigger) {
                        Inventory inv = event.getInventory();
                        ItemStack savedItem = inv.getItem(4);
                        trigger.setItem(savedItem);
                        LangUtils.sendMessage(player, "menus.items.saved-for-trigger");
                    }
                });
    }
}
