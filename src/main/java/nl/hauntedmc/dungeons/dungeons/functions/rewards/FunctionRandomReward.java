package nl.hauntedmc.dungeons.dungeons.functions.rewards;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.parents.instances.InstanceEditable;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.api.gui.window.GUIInventory;
import nl.hauntedmc.dungeons.api.gui.window.GUIWindow;
import nl.hauntedmc.dungeons.api.gui.buttons.Button;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class FunctionRandomReward extends FunctionReward {
   @SavedField
   private int rewardMinCount = 1;
   @SavedField
   private int rewardMaxCount = 3;

   public FunctionRandomReward(Map<String, Object> config) {
      super("Random Rewards", config);
   }

   public FunctionRandomReward() {
      super("Random Rewards");
   }

   @Override
   public void initLegacyFields(Map<String, Object> config) {
      super.initLegacyFields(config);
      if (config.containsKey("MinCount")) {
         this.rewardMinCount = (Integer)config.get("MinCount");
      }

      if (config.containsKey("MaxCount")) {
         this.rewardMaxCount = (Integer)config.get("MaxCount");
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.ENDER_CHEST);
      functionButton.setDisplayName("&eRandom Rewards");
      functionButton.addLore("&eCreates a random rewards menu");
      functionButton.addLore("&eat this location.");
      return functionButton;
   }

   @Override
   public void onEnable() {
      this.playerRewards = new ArrayList<>();
      this.instancedGUIName = "viewrewards_"
         + this.instance.getInstanceWorld().getName()
         + "_"
         + this.location.getBlockX()
         + "_"
         + this.location.getBlockY()
         + "_"
         + this.location.getBlockZ();
      this.instancedGUI = new GUIWindow(this.instancedGUIName, 54, LangUtils.getMessage("instance.rewards.gui-name", false));
      this.instancedGUI.setCancelClick(false);
      this.instancedGUI
         .addOpenAction(
            "loaditems",
            event -> {
               Player player = (Player)event.getPlayer();
               if (!this.playerRewards.contains(player.getUniqueId())) {
                  if (!(this.instance instanceof InstanceEditable)) {
                     InstancePlayable instance = (InstancePlayable)this.instance;
                     this.playerRewards.add(player.getUniqueId());
                     if (!this.cooldownDisabled && instance.getDungeon().hasLootCooldown(player, this)) {
                        LangUtils.sendMessage(player, "instance.rewards.already-received");
                        Date unlockTime = instance.getDungeon().getLootUnlockTime(player, this);
                        if (unlockTime != null) {
                           SimpleDateFormat format = new SimpleDateFormat("EEE, MMM d, hh:mm aaa z");
                           LangUtils.sendMessage(player, "instance.rewards.cooldown-time", format.format(unlockTime));
                        }

                        event.setCancelled(true);
                     } else {
                        if (instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false)
                           || !instance.getConfig().getBoolean("General.KeepInventoryOnEnter", true)) {
                           LangUtils.sendMessage(player, "instance.rewards.rewards-inv-info");
                           LangUtils.sendMessage(player, "instance.rewards.view-rewards-info");
                        }

                        GUIInventory guiInv = this.instancedGUI.getPlayersGui(player);
                        Inventory inv = guiInv.getInv();
                        List<ItemStack> validRewards = new ArrayList<>();

                        for (ItemStack item : this.rewards) {
                           if (item != null && item.getType() != Material.AIR) {
                              validRewards.add(item);
                           }
                        }

                        if (!validRewards.isEmpty()) {
                           int bonusItems = 0;
                           if (instance.getDifficulty() != null) {
                              bonusItems = instance.getDifficulty().getBonusLoot().randomizeAsInt();
                           }

                           int rewardCount = MathUtils.getRandomNumberInRange(this.rewardMinCount, this.rewardMaxCount) + bonusItems;

                           for (int r = 0; r < rewardCount; r++) {
                              ItemStack itemx = validRewards.get(MathUtils.getRandomNumberInRange(0, validRewards.size() - 1));
                              validRewards.remove(itemx);

                              if (!instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false)
                                 && instance.getConfig().getBoolean("General.KeepInventoryOnEnter", true)) {
                                 inv.setItem(r, itemx);
                              } else if (itemx != null) {
                                 Button button = new Button("item_" + r, itemx);
                                  int finalSlot = r;
                                 button.addAction("click", clickEvent -> {
                                    clickEvent.setCancelled(true);
                                    Player clicker = (Player)clickEvent.getWhoClicked();
                                    instance.addPlayerReward(clicker, itemx);
                                    guiInv.removeButton(finalSlot);
                                 });
                                 guiInv.setButton(r, button);
                              }
                           }
                        }
                     }
                  }
               }
            }
         );
      this.instancedGUI
         .addCloseAction(
            "dropitems",
            event -> {
               if ((this.trigger == null || !this.trigger.isAllowRetrigger())
                  && (this.parentFunction == null || this.parentFunction.getTrigger() == null || !this.parentFunction.getTrigger().isAllowRetrigger())) {
                  Player player = (Player)event.getPlayer();
                  Inventory inv = this.instancedGUI.getPlayersGui(player).getInv();
                  if (!inv.isEmpty()) {
                     if (!this.instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false)
                        && this.instance.getConfig().getBoolean("General.KeepInventoryOnEnter", true)) {
                        LangUtils.sendMessage(player, "instance.rewards.added-to-inv");
                        ItemUtils.giveOrDrop(player, inv.getContents());
                     } else {
                        LangUtils.sendMessage(player, "instance.rewards.added-to-rewards-inv");
                     }

                     inv.clear();
                  }
               }
            }
         );
   }

   @Override
   public void buildHotbarMenu() {
      super.buildHotbarMenu();
      this.menu
         .addMenuItem(
            new ChatMenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.COAL);
                  this.button.setDisplayName("&d&lMin Rewards");
                  this.button.setAmount(FunctionRandomReward.this.rewardMinCount);
               }

               @Override
               public void onSelect(Player player) {
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat's the minimum number of rewards?"));
               }

               @Override
               public void onInput(Player player, String message) {
                  Optional<Integer> value = StringUtils.readIntegerInput(player, message);
                  FunctionRandomReward.this.rewardMinCount = value.orElse(FunctionRandomReward.this.rewardMinCount);
                  if (value.isPresent()) {
                     player.sendMessage(
                        HelperUtils.colorize(Dungeons.logPrefix + "&aSet minimum reward count to '&6" + FunctionRandomReward.this.rewardMinCount + "&a'")
                     );
                  }
               }
            }
         );
      this.menu
         .addMenuItem(
            new ChatMenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.GOLD_INGOT);
                  this.button.setDisplayName("&d&lMax Rewards");
                  this.button.setAmount(FunctionRandomReward.this.rewardMaxCount);
               }

               @Override
               public void onSelect(Player player) {
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat's the maximum number of rewards?"));
               }

               @Override
               public void onInput(Player player, String message) {
                  Optional<Integer> value = StringUtils.readIntegerInput(player, message);
                  FunctionRandomReward.this.rewardMaxCount = value.orElse(FunctionRandomReward.this.rewardMaxCount);
                  player.sendMessage(
                     HelperUtils.colorize(Dungeons.logPrefix + "&aSet maximum reward count to '&6" + FunctionRandomReward.this.rewardMaxCount + "&a'")
                  );
               }
            }
         );
   }
}
