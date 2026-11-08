package nl.hauntedmc.dungeons.gui.menu;
import nl.hauntedmc.dungeons.content.function.GiveItemFunction;
import nl.hauntedmc.dungeons.content.function.meta.CompositeFunction;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.content.trigger.DungeonStartTrigger;
import nl.hauntedmc.dungeons.content.trigger.KeyItemTrigger;
import nl.hauntedmc.dungeons.content.trigger.gate.GateTrigger;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
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

/**
 * Inventory menu builders used by the dungeon editor.
 */
public class EditorMenus {
    public static GuiWindow editorWindow(String namespace, int size, String title) {
        return EditorMenuSupport.editorWindow(namespace, size, title);
    }

    /** Builds and registers the function browser GUI. */
    public static void initializeFunctionMenu() {
        GuiWindow gui = editorWindow("functionmenu", 27, "&8Select a Function");
        Button catButton =
                new Button("category_dungeon", Material.MOSSY_COBBLESTONE, "&6Dungeon Functions");
        catButton.addLore(ColorUtils.colorize("&eFunctions relating to general"));
        catButton.addLore(ColorUtils.colorize("&edungeon behaviour."));
        catButton.addLore(ColorUtils.colorize(""));
        catButton.addLore(ColorUtils.colorize("&8Checkpoint, start, signals, etc."));
        catButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.guiService().openGui(player, "functions_dungeon");
                });
        gui.addButton(11, catButton);
        catButton = new Button("category_player", Material.PLAYER_HEAD, "&playerSession Functions");
        catButton.addLore(ColorUtils.colorize("&eFunctions involving or effecting"));
        catButton.addLore(ColorUtils.colorize("&eplayers and team members."));
        catButton.addLore(ColorUtils.colorize(""));
        catButton.addLore(ColorUtils.colorize("&8Messages, keys, commands, etc."));
        catButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.guiService().openGui(player, "functions_player");
                });
        gui.addButton(12, catButton);
        catButton = new Button("category_location", Material.COMPASS, "&dLocation Functions");
        catButton.addLore(ColorUtils.colorize("&eFunctions involving or effecting"));
        catButton.addLore(ColorUtils.colorize("&etheir location."));
        catButton.addLore(ColorUtils.colorize(""));
        catButton.addLore(ColorUtils.colorize("&8Mob spawners, sounds, doors, etc."));
        catButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.guiService().openGui(player, "functions_location");
                });
        gui.addButton(13, catButton);
        catButton = new Button("category_meta", Material.NETHER_STAR, "&bMeta Functions");
        catButton.addLore(ColorUtils.colorize("&eFunctions involving or effecting"));
        catButton.addLore(ColorUtils.colorize("&eother functions and triggers."));
        catButton.addLore(ColorUtils.colorize(""));
        catButton.addLore(ColorUtils.colorize("&8Multi-functions, randoms, skills, etc."));
        catButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.guiService().openGui(player, "functions_meta");
                });
        gui.addButton(14, catButton);
        catButton = new Button("category_room", Material.JIGSAW, "&cRoom Functions");
        catButton.addLore(ColorUtils.colorize("&eFunctions involving or effecting"));
        catButton.addLore(ColorUtils.colorize("&erooms in branching dungeons."));
        catButton.addLore(ColorUtils.colorize(""));
        catButton.addLore(ColorUtils.colorize("&8Connector doors, locking and unlocking, etc."));
        catButton.addLore(ColorUtils.colorize("&dOnly available in branching dungeons."));
        catButton.addAction(
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
                            LangUtils.sendMessage(player, "menus.functions.branching-only");
                        } else {
                            RuntimeContext.guiService().openGui(player, "functions_room");
                        }
                    }
                });
        gui.addButton(15, catButton);
        initializeFunctionCategories();
    }

    /** Builds and registers per-category function browser GUIs. */
    private static void initializeFunctionCategories() {
        Map<FunctionCategory, Map<String, MenuButton>> categoryButtons =
                RuntimeContext.functionRegistry().getButtonsByCategory();
        Button backButton = new Button("back", Material.RED_STAINED_GLASS_PANE, "&c&lBACK");
        backButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.guiService().openGui(player, "functionmenu");
                });

        for (FunctionCategory category : FunctionCategory.values()) {
            String catName = category.name().toLowerCase(Locale.ROOT);
            GuiWindow gui =
                    editorWindow(
                            "functions_" + catName,
                            27,
                            "&8" + TextUtils.humanize(catName) + " Functions");
            gui.addButton(0, backButton);
            int i = 1;

            for (Entry<String, MenuButton> pair : categoryButtons.get(category).entrySet()) {
                String functionName = pair.getKey();
                MenuButton menuButton = pair.getValue();
                Button button = new Button("function_" + i, menuButton.getItem());
                button.addAction(
                        "click",
                        event -> {
                            Player player = (Player) event.getWhoClicked();
                            DungeonPlayerSession playerSession =
                                    RuntimeContext.playerSessions().get(player);
                            EditableInstance instance =
                                    playerSession.getInstance().asEditInstance();
                            if (instance != null) {
                                if (nl.hauntedmc.dungeons.content.function.CommandFunction.class
                                        .isAssignableFrom(
                                                RuntimeContext.functionRegistry()
                                                        .getFunctionClass(functionName))) {
                                    try {
                                        nl.hauntedmc.dungeons.runtime.staff.StaffPermissions
                                                .require(
                                                        player,
                                                        "scripts",
                                                        instance.getDungeon().getWorldName());
                                    } catch (IllegalArgumentException denied) {
                                        player.sendMessage("[Dungeons] " + denied.getMessage());
                                        return;
                                    }
                                }
                                try {
                                    if (playerSession.getTargetLocation() == null
                                            && playerSession.getActiveFunction()
                                                    instanceof CompositeFunction function) {
                                        DungeonFunction newFunction =
                                                RuntimeContext.functionRegistry()
                                                        .getFunctionClass(functionName)
                                                        .getDeclaredConstructor()
                                                        .newInstance();
                                        newFunction.setLocation(function.getLocation());
                                        newFunction.initialize();
                                        function.addFunction(newFunction);
                                        newFunction.setParentFunction(function);
                                        newFunction.setInstance(instance);
                                        player.closeInventory();
                                        playerSession.setTargetLocation(null);
                                        if (playerSession.getSavedHotbar() == null) {
                                            playerSession.captureAndShowHotbar(
                                                    newFunction.getMenu());
                                        } else {
                                            playerSession.showHotbar(newFunction.getMenu());
                                        }

                                        return;
                                    }

                                    DungeonFunction function =
                                            RuntimeContext.functionRegistry()
                                                    .getFunctionClass(functionName)
                                                    .getDeclaredConstructor()
                                                    .newInstance();
                                    playerSession.setActiveFunction(function);
                                    DungeonDefinition dungeon = instance.getDungeon();
                                    dungeon.addFunction(
                                            playerSession.getTargetLocation(), function);
                                    instance.trackEditorFunction(function);
                                    instance.addFunctionLabel(function);
                                    function.setInstance(instance);
                                    RuntimeContext.guiService().openGui(player, "triggermenu");
                                } catch (IllegalAccessException
                                        | InvocationTargetException
                                        | NoSuchMethodException
                                        | InstantiationException exception) {
                                    RuntimeContext.plugin()
                                            .getSLF4JLogger()
                                            .error(
                                                    "Failed to create function '{}' from the"
                                                        + " function browser for player '{}'.",
                                                    functionName,
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

    /** Builds and registers the trigger browser GUI. */
    public static void initializeTriggerMenu() {
        GuiWindow gui = editorWindow("triggermenu", 27, "&8Select a Trigger");
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
                    editorWindow(
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
    public static void initializeItemSelectTriggerMenu() {
        GuiWindow gui = editorWindow("selectitem_trigger", 9, "&8Put your item here");
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

    /** Builds and registers the function item-selection GUI. */
    public static void initializeItemSelectFunctionMenu() {
        GuiWindow gui = editorWindow("selectitem_function", 9, "&8Put your item here");
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
                    if (playerSession.getActiveFunction() instanceof GiveItemFunction function) {
                        Inventory inv = event.getInventory();
                        inv.setItem(4, function.getItem());
                    }
                });
        gui.addCloseAction(
                "save",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession =
                            RuntimeContext.playerSessions().get(player);
                    if (playerSession.getActiveFunction() instanceof GiveItemFunction function) {
                        Inventory inv = event.getInventory();
                        ItemStack savedItem = inv.getItem(4);
                        function.setItem(savedItem);
                        LangUtils.sendMessage(player, "menus.items.saved-for-function");
                    }
                });
    }

    /** Builds and registers all composite-function editor GUIs. */
    public static void initializeCompositeFunctionMenus() {
        initializeCompositeFunctionEditor();
        initializeCompositeFunctionRemover();
        initializeCompositeFunctionTriggerEditor();
    }

    /** Builds and registers the composite-function edit GUI. */
    private static void initializeCompositeFunctionEditor() {
        GuiWindow gui = editorWindow("editmultifunction", 54, "&8Edit a Function");
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
        GuiWindow gui = editorWindow("removemultifunction", 54, "&8Remove a Function");
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
                                        LangUtils.placeholder("name", menuButton.getDisplayName()));
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
        GuiWindow gui = editorWindow("editmultifunctiontriggers", 54, "&8Edit Function Triggers");
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

    public static void initializeConditionsMenus() {
        ConditionEditorMenus.initializeConditionsMenus();
    }

    public static void initializeGateTriggerMenus() {
        GateTriggerEditorMenus.initializeGateTriggerMenus();
    }

}
