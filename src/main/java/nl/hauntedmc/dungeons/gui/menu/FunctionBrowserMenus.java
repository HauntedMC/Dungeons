package nl.hauntedmc.dungeons.gui.menu;
import nl.hauntedmc.dungeons.content.function.CommandFunction;
import nl.hauntedmc.dungeons.content.function.GiveItemFunction;
import nl.hauntedmc.dungeons.content.function.meta.CompositeFunction;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.staff.StaffPermissions;
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

/** Builds the functions editor workflow. */
final class FunctionBrowserMenus {
    private FunctionBrowserMenus() {}

    /** Builds and registers the function browser GUI. */
    static void initializeFunctionMenu() {
        GuiWindow gui = EditorMenuSupport.editorWindow("functionmenu", 27, "&8Select a Function");
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
                    EditorMenuSupport.editorWindow(
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
                                if (CommandFunction.class
                                        .isAssignableFrom(
                                                RuntimeContext.functionRegistry()
                                                        .getFunctionClass(functionName))) {
                                    try {
                                        StaffPermissions
                                                .require(
                                                        player,
                                                        "scripts",
                                                        instance.getDungeon().getWorldName());
                                    } catch (IllegalArgumentException denied) {
                                        LangUtils.sendRaw(player, denied.getMessage());
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

    /** Builds and registers the function item-selection GUI. */
    static void initializeItemSelectFunctionMenu() {
        GuiWindow gui = EditorMenuSupport.editorWindow("selectitem_function", 9, "&8Put your item here");
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
}
