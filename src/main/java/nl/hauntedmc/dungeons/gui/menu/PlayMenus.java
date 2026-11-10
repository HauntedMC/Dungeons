package nl.hauntedmc.dungeons.gui.menu;

import nl.hauntedmc.dungeons.content.function.RevivePlayerFunction;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDifficulty;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory menu builders used during live dungeon play.
 */
public class PlayMenus {

    /** Builds and registers the dungeon difficulty selector GUI. */
    public static void initializeDifficultySelector(DungeonDefinition dungeon) {
        if (dungeon.isUseDifficultyLevels()) {
            List<DungeonDifficulty> difficulties =
                    new ArrayList<>(dungeon.getDifficultyLevels().values());
            int guiSize = 9;
            if (difficulties.size() > 9) {
                guiSize = 27;
            }

            GuiWindow gui =
                    new GuiWindow(
                            "difficulty_" + dungeon.getWorldName(),
                            guiSize,
                            LangUtils.getMessage("menus.difficulty-select.title", false));
            if (!difficulties.isEmpty()) {
                for (int i = 0; i < difficulties.size(); i++) {
                    DungeonDifficulty difficulty = difficulties.get(i);
                    Button button =
                            new Button(
                                    dungeon.getWorldName() + "_" + difficulty.getNamespace(),
                                    difficulty.getIcon());
                    button.addAction(
                            "click",
                            click -> {
                                Player player = (Player) click.getWhoClicked();
                                RuntimeContext.dungeonQueueCoordinator()
                                        .sendToDungeon(
                                                player,
                                                dungeon.getWorldName(),
                                                difficulty.getNamespace());
                                player.closeInventory();
                            });
                    gui.addButton(i, button);
                }
            }
        }
    }

    /** Builds and registers the player revival confirmation GUI. */
    public static void initializeRevivalMenu() {
        GuiWindow gui = new GuiWindow("revivalmenu", 27, "&8Choose who to revive");
        gui.addOpenAction(
                "populate",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession dungeonPlayer =
                            RuntimeContext.playerSessions().get(player);
                    PlayableInstance instance = dungeonPlayer.getInstance().asPlayInstance();
                    if (instance == null) {
                        event.setCancelled(true);
                    } else if (!(dungeonPlayer.getActiveFunction()
                            instanceof RevivePlayerFunction reviver)) {
                        event.setCancelled(true);
                    } else {
                        GuiInventory inv = gui.getInventoryFor(player);

                        for (int i = 0; i < 27; i++) {
                            inv.removeButton(i);
                        }

                        int slot = 0;

                        for (DungeonPlayerSession playerSession : instance.getPlayers()) {
                            if (!instance.getLivingPlayers().contains(playerSession)) {
                                Player instancePlayer = playerSession.getPlayer();
                                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                                SkullMeta meta = (SkullMeta) skull.getItemMeta();

                                assert meta != null;

                                meta.setOwningPlayer(instancePlayer);
                                skull.setItemMeta(meta);
                                Button button =
                                        new Button("revive_" + instancePlayer.getName(), skull);
                                button.setDisplayName("&3&lRevive " + instancePlayer.getName());
                                button.addAction(
                                        "revive",
                                        clickEvent -> {
                                            reviver.revivePlayer(playerSession, dungeonPlayer);
                                            player.closeInventory();
                                        });
                                inv.setButton(slot, button);
                                slot++;
                            }
                        }
                    }
                });
        gui.addCloseAction(
                "close",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession dungeonPlayer =
                            RuntimeContext.playerSessions().get(player);
                    dungeonPlayer.setActiveFunction(null);
                });
    }
}
