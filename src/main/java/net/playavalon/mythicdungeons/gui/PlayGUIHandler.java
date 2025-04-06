package net.playavalon.mythicdungeons.gui;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.parents.DungeonDifficulty;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.avngui.GUI.GUIInventory;
import net.playavalon.mythicdungeons.avngui.GUI.Window;
import net.playavalon.mythicdungeons.avngui.GUI.Buttons.Button;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionRevivePlayer;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
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

         Window gui = new Window("difficulty_" + dungeon.getWorldName(), guiSize, LangUtils.getMessage("instance.menu.difficulty-select", false));
         if (difficulties.size() != 0) {
            for (int i = 0; i < difficulties.size(); i++) {
               DungeonDifficulty difficulty = difficulties.get(i);
               Button button = new Button(dungeon.getWorldName() + "_" + difficulty.getNamespace(), difficulty.getIcon());
               button.addAction("click", click -> {
                  Player player = (Player)click.getWhoClicked();
                  MythicDungeons.inst().sendToDungeon(player, dungeon.getWorldName(), difficulty.getNamespace());
                  player.closeInventory();
               });
               gui.addButton(i, button);
            }
         }
      }
   }

   public static void initRewardMenu() {
      rewardPages = new HashMap<>();
      Window gui = new Window("rewards", 54, "&8Rewards");
      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
         GUIInventory guiInv = gui.getPlayersGui(player);
         rewardPages.putIfAbsent(player.getUniqueId(), 0);
         List<ItemStack> rewards = mythicPlayer.getRewardsInv();
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

   private static void loadRewardPage(Window gui, Player player, int page) {
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
      GUIInventory guiInv = gui.getPlayersGui(player);
      List<ItemStack> rewards = mythicPlayer.getRewardsInv();
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
                     if (mythicPlayer.getInstance() == null) {
                        mythicPlayer.removeReward(reward);
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
      Window gui = new Window("revivalmenu", 27, "&8Choose who to revive");
      gui.addOpenAction("populate", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
         InstancePlayable instance = mythicPlayer.getInstance().asPlayInstance();
         if (instance == null) {
            event.setCancelled(true);
         } else if (!(mythicPlayer.getActiveFunction() instanceof FunctionRevivePlayer reviver)) {
            event.setCancelled(true);
         } else {
            GUIInventory inv = gui.getPlayersGui(player);

            for (int i = 0; i < 27; i++) {
               inv.removeButton(i);
            }

            int slot = 0;

            for (MythicPlayer aPlayer : instance.getPlayers()) {
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
                     reviver.revivePlayer(aPlayer, mythicPlayer);
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
         MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
         mythicPlayer.setActiveFunction(null);
      });
   }
}
