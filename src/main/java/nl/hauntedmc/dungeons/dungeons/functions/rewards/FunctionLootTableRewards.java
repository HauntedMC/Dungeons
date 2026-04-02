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
import nl.hauntedmc.dungeons.dungeons.rewards.LootTable;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class FunctionLootTableRewards extends FunctionReward {
   @SavedField
   private String loottableName = "";

   public FunctionLootTableRewards(Map<String, Object> config) {
      super("Loot Table Rewards", config);
   }

   public FunctionLootTableRewards() {
      super("Loot Table Rewards");
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.SHULKER_BOX);
      functionButton.setDisplayName("&eLoot Table Rewards");
      functionButton.addLore("&eGenerates rewards from a loot");
      functionButton.addLore("&etable when opened.");
      functionButton.addLore("");
      functionButton.addLore("&7Use /dungeon loot [create/edit]");
      functionButton.addLore("&7to create or edit a loot table.");
      return functionButton;
   }

   @Override
   public void initRewardsGUI() {
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
      this.instancedGUI.addOpenAction("loaditems", event -> {
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
                  LootTable table = Dungeons.inst().getLootTableManager().get(this.loottableName);
                  if (table == null) {
                     LangUtils.sendMessage(player, "instance.rewards.invalid-loottable");
                  } else {
                     if (instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false)) {
                        LangUtils.sendMessage(player, "instance.rewards.rewards-inv-info");
                        LangUtils.sendMessage(player, "instance.rewards.view-rewards-info");
                     }

                     GUIInventory guiInv = this.instancedGUI.getPlayersGui(player);
                     Inventory inv = guiInv.getInv();
                     List<ItemStack> items;
                     if (instance.getDifficulty() != null) {
                        items = table.randomizeItems(instance.getDifficulty().getBonusLoot().randomizeAsInt());
                     } else {
                        items = table.randomizeItems();
                     }

                     List<Integer> occupied = new ArrayList<>();

                     for (ItemStack item : items) {
                        if (occupied.size() >= 54) {
                           break;
                        }

                        int i = MathUtils.getRandomNumberInRange(0, 53);

                        while (occupied.contains(i)) {
                           i = MathUtils.getRandomNumberInRange(0, 53);
                        }

                        occupied.add(i);
                        if (instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false)) {
                           if (item != null) {
                              Button button = new Button("item_" + i, item);
                              int finalSlot = i;
                              button.addAction("click", clickEvent -> {
                                 clickEvent.setCancelled(true);
                                 Player clicker = (Player)clickEvent.getWhoClicked();
                                 instance.addPlayerReward(clicker, item);
                                 guiInv.removeButton(finalSlot);
                              });
                              guiInv.setButton(i, button);
                           }
                        } else {
                           inv.setItem(i, item);
                        }
                     }
                  }
               }
            }
         }
      });
      this.instancedGUI
         .addCloseAction(
            "dropitems",
            event -> {
               Player player = (Player)event.getPlayer();
               if ((this.trigger == null || !this.trigger.isAllowRetrigger())
                  && (this.parentFunction == null || this.parentFunction.getTrigger() == null || !this.parentFunction.getTrigger().isAllowRetrigger())) {
                  if (!(this.instance instanceof InstanceEditable)) {
                     InstancePlayable instance = (InstancePlayable)this.instance;
                     Inventory inv = this.instancedGUI.getPlayersGui(player).getInv();
                     if (!inv.isEmpty()) {
                        if (instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false)) {
                           LangUtils.sendMessage(player, "instance.rewards.added-to-rewards-inv");
                           instance.addPlayerReward(player, inv.getContents());
                        } else {
                           LangUtils.sendMessage(player, "instance.rewards.added-to-inv");
                           ItemUtils.giveOrDrop(player, inv.getContents());
                        }

                        inv.clear();
                     }
                  }
               }
            }
         );
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CHEST);
            this.button.setDisplayName("&d&lSet Loot Table");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eWhat's the unique name of the loot table?"));
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eCurrent loot table: &6" + FunctionLootTableRewards.this.loottableName));
         }

         @Override
         public void onInput(Player player, String message) {
            if (message.contains(" ")) {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cWARNING: Loot tables cannot have spaces in their names!"));
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cAll spaces will be replaced with underscores."));
               message = message.replace(" ", "_");
            }

            if (!Dungeons.inst().getLootTableManager().contains(message)) {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cWARNING: No loot table by this name exists!"));
               StringUtils.sendClickableCommand(player, Dungeons.logPrefix + "&b&lClick here &bto create it!", "dungeon loot create " + message);
            }

            FunctionLootTableRewards.this.loottableName = message;
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet loot table to '&6" + FunctionLootTableRewards.this.loottableName + "&6'"));
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.EMERALD);
            this.button.setDisplayName("&d&lExp Reward");
            this.button.setAmount(FunctionLootTableRewards.this.xp);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow much EXP should this reward give?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionLootTableRewards.this.xp = value.orElse(FunctionLootTableRewards.this.xp);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet exp reward to '&6" + FunctionLootTableRewards.this.xp + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NETHER_STAR);
            this.button.setDisplayName("&d&lExp Levels Reward");
            this.button.setAmount(FunctionLootTableRewards.this.levels);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow many levels should this reward give?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionLootTableRewards.this.levels = value.orElse(FunctionLootTableRewards.this.levels);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet exp reward to '&6" + FunctionLootTableRewards.this.levels + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CLOCK);
            this.button.setDisplayName("&a&lCooldown Settings");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            aPlayer.setHotbar(FunctionLootTableRewards.this.cooldownMenu, true);
         }
      });
   }
}
