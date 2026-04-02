package nl.hauntedmc.dungeons.gui.inv;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.parents.DungeonDifficulty;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.api.gui.window.GUIInventory;
import nl.hauntedmc.dungeons.api.gui.window.GUIWindow;
import nl.hauntedmc.dungeons.api.gui.buttons.Button;
import nl.hauntedmc.dungeons.dungeons.functions.FunctionRevivePlayer;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class PlayGUIHandler {
   private static Map<UUID, Integer> rewardPages;

   public static void initDifficultySelector(AbstractDungeon dungeon) {
      if (dungeon.isUseDifficultyLevels()) {
         List<DungeonDifficulty> difficulties = new ArrayList<>(dungeon.getDifficultyLevels().values());
         int guiSize = 9;
         if (difficulties.size() > 9) {
            guiSize = 27;
         }

         GUIWindow gui = new GUIWindow("difficulty_" + dungeon.getWorldName(), guiSize, LangUtils.getMessage("instance.menu.difficulty-select", false));
         if (!difficulties.isEmpty()) {
            for (int i = 0; i < difficulties.size(); i++) {
               DungeonDifficulty difficulty = difficulties.get(i);
               Button button = new Button(dungeon.getWorldName() + "_" + difficulty.getNamespace(), difficulty.getIcon());
               button.addAction("click", click -> {
                  Player player = (Player)click.getWhoClicked();
                  Dungeons.inst().sendToDungeon(player, dungeon.getWorldName(), difficulty.getNamespace());
                  player.closeInventory();
               });
               gui.addButton(i, button);
            }
         }
      }
   }

   public static void initRewardMenu() {
      rewardPages = new HashMap<>();
      GUIWindow gui = new GUIWindow("rewards", 54, "&8Rewards");
      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer dungeonPlayer = Dungeons.inst().getDungeonPlayer(player);
         GUIInventory guiInv = gui.getPlayersGui(player);
         rewardPages.putIfAbsent(player.getUniqueId(), 0);
         List<ItemStack> rewards = dungeonPlayer.getRewardsInv();
         List<List<ItemStack>> pages = Lists.partition(rewards, 45);
         Button button = new Button("page_back", ItemUtils.skullFromName(new ItemStack(Material.PLAYER_HEAD), "MHF_arrowleft"));
         button.setDisplayName("&e&lPrevious Page");
         button.addAction("click", clickEvent -> {
            Player clicker = (Player)clickEvent.getWhoClicked();
            int page = rewardPages.get(clicker.getUniqueId()) - 1;
            if (page >= 0) {
               rewardPages.put(clicker.getUniqueId(), page);
               loadRewardPage(gui, clicker, page);
            }
         });
         guiInv.setButton(45, button);
         button = new Button("page_forward", ItemUtils.skullFromName(new ItemStack(Material.PLAYER_HEAD), "MHF_arrowright"));
         button.setDisplayName("&e&lNext Page");
         button.addAction("click", clickEvent -> {
            Player clicker = (Player)clickEvent.getWhoClicked();
            int page = rewardPages.get(clicker.getUniqueId()) + 1;
            if (page < pages.size()) {
               rewardPages.put(clicker.getUniqueId(), page);
               loadRewardPage(gui, clicker, page);
            }
         });
         guiInv.setButton(53, button);

         for (int slot = 46; slot < 53; slot++) {
            guiInv.setButton(slot, new Button("empty_" + slot, Material.BLACK_STAINED_GLASS_PANE, ""));
         }

         loadRewardPage(gui, player, rewardPages.get(player.getUniqueId()));
      });
   }

   private static void loadRewardPage(GUIWindow gui, Player player, int page) {
      DungeonPlayer dungeonPlayer = Dungeons.inst().getDungeonPlayer(player);
      GUIInventory guiInv = gui.getPlayersGui(player);
      List<ItemStack> rewards = dungeonPlayer.getRewardsInv();
      List<List<ItemStack>> pages = Lists.partition(rewards, 45);
      if (page < pages.size()) {
         List<ItemStack> pageItems = pages.get(page);

         for (int slot = 0; slot < 45; slot++) {
            guiInv.removeButton(slot);
            if (slot < pageItems.size()) {
               ItemStack reward = pageItems.get(slot);
               if (reward != null) {
                  Button rewardButton = new Button("reward_" + slot, reward);
                  rewardButton.addAction("click", event -> {
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
         for (int slotx = 0; slotx < 45; slotx++) {
            guiInv.removeButton(slotx);
         }
      }
   }

   public static void initRevivalMenu() {
      GUIWindow gui = new GUIWindow("revivalmenu", 27, "&8Choose who to revive");
      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer dungeonPlayer = Dungeons.inst().getDungeonPlayer(player);
         InstancePlayable instance = dungeonPlayer.getInstance().asPlayInstance();
         if (instance == null) {
            event.setCancelled(true);
         } else if (!(dungeonPlayer.getActiveFunction() instanceof FunctionRevivePlayer reviver)) {
            event.setCancelled(true);
         } else {
            GUIInventory inv = gui.getPlayersGui(player);

            for (int i = 0; i < 27; i++) {
               inv.removeButton(i);
            }

            int slot = 0;

            for (DungeonPlayer aPlayer : instance.getPlayers()) {
               if (!instance.getLivingPlayers().contains(aPlayer)) {
                  Player instancePlayer = aPlayer.getPlayer();
                  ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                  SkullMeta meta = (SkullMeta)skull.getItemMeta();

                  assert meta != null;

                  meta.setOwningPlayer(instancePlayer);
                  skull.setItemMeta(meta);
                  Button button = new Button("revive_" + instancePlayer.getName(), skull);
                  button.setDisplayName("&3&lRevive " + instancePlayer.getName());
                  button.addAction("revive", clickEvent -> {
                     reviver.revivePlayer(aPlayer, dungeonPlayer);
                     player.closeInventory();
                  });
                  inv.setButton(slot, button);
                  slot++;
               }
            }
         }
      });
      gui.addCloseAction("close", event -> {
         Player player = (Player)event.getPlayer();
         DungeonPlayer dungeonPlayer = Dungeons.inst().getDungeonPlayer(player);
         dungeonPlayer.setActiveFunction(null);
      });
   }
}
