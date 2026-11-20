package nl.hauntedmc.dungeons.gui.menu;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.dungeons.content.function.RevivePlayerFunction;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** Inventory menu builders used during live dungeon play. */
public class PlayMenus {

    /** Builds and registers the dungeon difficulty selector GUI. */
    public static void initializeDifficultySelector(DungeonDefinition dungeon) {
        if (!dungeon.isUseDifficultyLevels()) return;
        var difficulties = new ArrayList<>(dungeon.getDifficultyLevels().values());
        if (difficulties.isEmpty()) return;
        String prefix = "difficulty_" + dungeon.getWorldName();
        int pageSize = 14;
        int pages = (difficulties.size() + pageSize - 1) / pageSize;
        for (int index = 0; index < pages; index++) {
            int page = index;
            var options =
                    List.copyOf(
                            difficulties.subList(
                                    page * pageSize,
                                    Math.min(difficulties.size(), (page + 1) * pageSize)));
            var window =
                    RuntimeContext.guiService()
                            .createWindow(
                                    difficultyPage(prefix, page),
                                    36,
                                    ComponentUtils.serialize(
                                            LangUtils.component(
                                                            "experience.ui-menu-difficulty",
                                                            null,
                                                            false)
                                                    .color(NamedTextColor.DARK_AQUA)
                                                    .decorate(TextDecoration.BOLD)));
            MenuFrame.apply(window);
            window.setAccessCheck(
                    player -> player.hasPermission("dungeons.play") && dungeon.isEnabled());
            window.addOpenAction(
                    "difficulty-options",
                    event -> {
                        Player player = (Player) event.getPlayer();
                        var inventory = window.getInventoryFor(player);
                        var title =
                                new Button(
                                        "map",
                                        Material.COMPASS,
                                        ComponentUtils.serialize(
                                                dungeon.getDisplayNameComponent()
                                                        .decorate(TextDecoration.BOLD)));
                        title.addLore(
                                ComponentUtils.serialize(
                                        LangUtils.component(
                                                        "experience.ui-menu-difficulty",
                                                        player,
                                                        false)
                                                .color(NamedTextColor.GRAY)));
                        inventory.setButton(4, title);
                        for (int option = 0; option < options.size(); option++) {
                            var difficulty = options.get(option);
                            Button button =
                                    new Button(difficulty.getNamespace(), difficulty.getIcon());
                            button.addLore(
                                    ComponentUtils.serialize(
                                            LangUtils.component(
                                                            "experience.ui-menu-join",
                                                            player,
                                                            false)
                                                    .color(NamedTextColor.GREEN)));
                            button.addAction(
                                    "enter",
                                    click -> {
                                        player.closeInventory();
                                        RuntimeContext.dungeonQueueCoordinator()
                                                .sendToDungeon(
                                                        player,
                                                        dungeon.getWorldName(),
                                                        difficulty.getNamespace());
                                    });
                            int row = option / 7;
                            int count = Math.min(7, options.size() - row * 7);
                            inventory.setButton(
                                    (row + 1) * 9 + (9 - count) / 2 + option % 7, button);
                        }
                        if (page > 0)
                            inventory.setButton(
                                    30, difficultyNavigation(player, prefix, page - 1, "previous"));
                        if (page + 1 < pages)
                            inventory.setButton(
                                    32, difficultyNavigation(player, prefix, page + 1, "next"));
                    });
        }
    }

    private static Button difficultyNavigation(
            Player player, String prefix, int page, String direction) {
        var button =
                new Button(
                        direction,
                        Material.ARROW,
                        ComponentUtils.serialize(
                                LangUtils.component("commands.ui." + direction, player, false)
                                        .color(NamedTextColor.AQUA)));
        button.addAction(
                "page",
                click -> RuntimeContext.guiService().openGui(player, difficultyPage(prefix, page)));
        return button;
    }

    private static String difficultyPage(String prefix, int page) {
        return page == 0 ? prefix : prefix + ":" + page;
    }

    /** Builds and registers the player revival confirmation GUI. */
    public static void initializeRevivalMenu() {
        GuiWindow gui =
                RuntimeContext.guiService()
                        .createWindow(
                                "revivalmenu",
                                27,
                                LangUtils.getMessage("experience.ui-revive-title", false));
        MenuFrame.apply(gui);
        gui.addOpenAction(
                "populate",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession dungeonPlayer =
                            RuntimeContext.playerSessions().get(player);
                    PlayableInstance instance =
                            dungeonPlayer == null || dungeonPlayer.getInstance() == null
                                    ? null
                                    : dungeonPlayer.getInstance().asPlayInstance();
                    if (instance == null) {
                        event.setCancelled(true);
                    } else if (!(dungeonPlayer.getActiveFunction()
                            instanceof RevivePlayerFunction reviver)) {
                        event.setCancelled(true);
                    } else {
                        GuiInventory inv = gui.getInventoryFor(player);

                        var living = instance.getLivingPlayers();
                        var targets =
                                instance.getPlayers().stream()
                                        .filter(member -> !living.contains(member))
                                        .limit(27)
                                        .toList();
                        // Small groups use the center row. Large groups can use the full inventory.
                        if (targets.size() > 9) {
                            for (int i = 0; i < 27; i++) inv.removeButton(i);
                        }
                        if (targets.isEmpty()) {
                            inv.setButton(
                                    13,
                                    new Button(
                                            "ready",
                                            Material.TOTEM_OF_UNDYING,
                                            ComponentUtils.serialize(
                                                    LangUtils.component(
                                                            "experience.ui-revive-empty",
                                                            player,
                                                            false))));
                        }
                        for (int i = 0; i < targets.size(); i++) {
                            var playerSession = targets.get(i);
                            Player instancePlayer = playerSession.getPlayer();
                            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                            SkullMeta meta = (SkullMeta) skull.getItemMeta();

                            assert meta != null;

                            meta.setOwningPlayer(instancePlayer);
                            skull.setItemMeta(meta);
                            Button button = new Button("revive_" + instancePlayer.getName(), skull);
                            button.setDisplayName(
                                    ComponentUtils.serialize(
                                            Component.text(
                                                            instancePlayer.getName(),
                                                            NamedTextColor.AQUA)
                                                    .decorate(TextDecoration.BOLD)));
                            button.addLore(
                                    ComponentUtils.serialize(
                                            LangUtils.component(
                                                            "experience.ui-revive-action",
                                                            player,
                                                            false)
                                                    .color(NamedTextColor.GREEN)));
                            button.addAction(
                                    "revive",
                                    clickEvent -> {
                                        reviver.revivePlayer(playerSession, dungeonPlayer);
                                        player.closeInventory();
                                    });
                            int count = Math.min(9, targets.size() - i / 9 * 9);
                            int row = targets.size() <= 9 ? 1 : i / 9;
                            inv.setButton(row * 9 + (9 - count) / 2 + i % 9, button);
                        }
                    }
                });
        gui.addCloseAction(
                "close",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession dungeonPlayer =
                            RuntimeContext.playerSessions().get(player);
                    if (dungeonPlayer != null) dungeonPlayer.setActiveFunction(null);
                });
    }
}
