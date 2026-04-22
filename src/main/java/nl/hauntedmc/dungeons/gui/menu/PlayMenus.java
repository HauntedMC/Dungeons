package nl.hauntedmc.dungeons.gui.menu;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.hauntedmc.dungeons.content.function.RevivePlayerFunction;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDifficulty;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Inventory menu builders used during live dungeon play.
 */
public class PlayMenus {
    private static Map<UUID, Integer> rewardPages;

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
                                    dungeon.getWorldName() + "_" + difficulty.getNamespace(), difficulty.getIcon());
                    button.addAction(
                            "click",
                            click -> {
                                Player player = (Player) click.getWhoClicked();
                                RuntimeContext.dungeonQueueCoordinator()
                                        .sendToDungeon(player, dungeon.getWorldName(), difficulty.getNamespace());
                                player.closeInventory();
                            });
                    gui.addButton(i, button);
                }
            }
        }
    }

    /** Builds and registers the reward-claim GUI. */
    public static void initializeRewardMenu() {
        rewardPages = new HashMap<>();
        GuiWindow gui = new GuiWindow("rewards", 54, "&8Rewards");
        gui.addOpenAction(
                "populate",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession dungeonPlayer = RuntimeContext.playerSessions().get(player);
                    GuiInventory guiInv = gui.getInventoryFor(player);
                    rewardPages.putIfAbsent(player.getUniqueId(), 0);
                    List<ItemStack> rewards = dungeonPlayer.getRewardItems();
                    List<List<ItemStack>> pages = Lists.partition(rewards, 45);
                    Button button =
                                                        new Button(
                                    "page_back",
                                    ItemUtils.skullFromName(new ItemStack(Material.PLAYER_HEAD), "MHF_arrowleft"));
                    button.setDisplayName("&e&lPrevious Page");
                    button.addAction(
                            "click",
                            clickEvent -> {
                                Player clicker = (Player) clickEvent.getWhoClicked();
                                int page = rewardPages.get(clicker.getUniqueId()) - 1;
                                if (page >= 0) {
                                    rewardPages.put(clicker.getUniqueId(), page);
                                    loadRewardPage(gui, clicker, page);
                                }
                            });
                    guiInv.setButton(45, button);
                    button =
                                                        new Button(
                                    "page_forward",
                                    ItemUtils.skullFromName(new ItemStack(Material.PLAYER_HEAD), "MHF_arrowright"));
                    button.setDisplayName("&e&lNext Page");
                    button.addAction(
                            "click",
                            clickEvent -> {
                                Player clicker = (Player) clickEvent.getWhoClicked();
                                int page = rewardPages.get(clicker.getUniqueId()) + 1;
                                if (page < pages.size()) {
                                    rewardPages.put(clicker.getUniqueId(), page);
                                    loadRewardPage(gui, clicker, page);
                                }
                            });
                    guiInv.setButton(53, button);

                    for (int slot = 46; slot < 53; slot++) {
                        guiInv.setButton(
                                                                slot, new Button("empty_" + slot, Material.BLACK_STAINED_GLASS_PANE, ""));
                    }

                    loadRewardPage(gui, player, rewardPages.get(player.getUniqueId()));
                });
    }

    /** Loads one paginated reward page into the reward GUI for a player. */
    private static void loadRewardPage(GuiWindow gui, Player player, int page) {
        DungeonPlayerSession dungeonPlayer = RuntimeContext.playerSessions().get(player);
        GuiInventory guiInv = gui.getInventoryFor(player);
        List<ItemStack> rewards = dungeonPlayer.getRewardItems();
        List<List<ItemStack>> pages = Lists.partition(rewards, 45);
        if (page < pages.size()) {
            List<ItemStack> pageItems = pages.get(page);

            for (int slot = 0; slot < 45; slot++) {
                guiInv.removeButton(slot);
                if (slot < pageItems.size()) {
                    ItemStack reward = pageItems.get(slot);
                    if (reward != null) {
                        // Reward buttons are rebuilt per page so claimed rewards disappear
                        // immediately without reopening the menu.
                        Button rewardButton = new Button("reward_" + slot, reward);
                        rewardButton.addAction(
                                "click",
                                event -> {
                                    if (dungeonPlayer.getInstance() == null) {
                                        dungeonPlayer.removeReward(reward);
                                        ItemUtils.giveOrDrop(player, reward);
                                        loadRewardPage(gui, player, page);
                                    }
                                });
                        guiInv.setButton(slot, rewardButton);
                    }
                }
            }

            gui.updateButtons(player);
        } else {
            for (int slotIndex = 0; slotIndex < 45; slotIndex++) {
                guiInv.removeButton(slotIndex);
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
                    DungeonPlayerSession dungeonPlayer = RuntimeContext.playerSessions().get(player);
                    PlayableInstance instance = dungeonPlayer.getInstance().asPlayInstance();
                    if (instance == null) {
                        event.setCancelled(true);
                    } else if (!(dungeonPlayer.getActiveFunction() instanceof RevivePlayerFunction reviver)) {
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
                                Button button = new Button("revive_" + instancePlayer.getName(), skull);
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
                    DungeonPlayerSession dungeonPlayer = RuntimeContext.playerSessions().get(player);
                    dungeonPlayer.setActiveFunction(null);
                });
    }
}
