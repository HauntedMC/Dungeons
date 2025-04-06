package net.playavalon.mythicdungeons.dungeons.functions.rewards;

import io.lumine.mythic.api.drops.IItemDrop;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.drops.Drop;
import io.lumine.mythic.core.drops.DropMetadataImpl;
import io.lumine.mythic.core.drops.DropTable;
import io.lumine.mythic.core.drops.droppables.CustomDrop;
import io.lumine.mythic.core.items.MythicItem;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.parents.instances.InstanceEditable;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.avngui.GUI.GUIInventory;
import net.playavalon.mythicdungeons.avngui.GUI.Window;
import net.playavalon.mythicdungeons.avngui.GUI.Buttons.Button;
import net.playavalon.mythicdungeons.dungeons.rewards.LootTable;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.MathUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class FunctionMythicLootTableRewards extends FunctionReward {
   @SavedField
   private String loottableName = "";
   @SavedField
   private boolean isMythic = false;

   public FunctionMythicLootTableRewards(Map<String, Object> config) {
      super("Loot Table Rewards", config);
   }

   public FunctionMythicLootTableRewards() {
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
      functionButton.addLore("");
      functionButton.addLore("&dSupports Mythic Mobs drop tables!");
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
      this.instancedGUI = new Window(this.instancedGUIName, 54, LangUtils.getMessage("instance.rewards.gui-name", false));
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
               } else if (MythicDungeons.inst().getMythicApi() != null && this.isMythic) {
                  Optional<DropTable> optTable = MythicDungeons.inst().getMythicApi().getDropManager().getDropTable(this.loottableName);
                  if (!optTable.isPresent()) {
                     LangUtils.sendMessage(player, "instance.rewards.invalid-loottable");
                  } else {
                     DropTable table = optTable.get();
                     GUIInventory guiInv = this.instancedGUI.getPlayersGui(player);
                     Inventory inv = guiInv.getInv();
                     if (instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false)) {
                        LangUtils.sendMessage(player, "instance.rewards.rewards-inv-info");
                        LangUtils.sendMessage(player, "instance.rewards.view-rewards-info");
                     }

                     Collection<Drop> items = table.generate().getDrops();
                     List<Integer> occupied = new ArrayList<>();

                     for (Drop drop : items) {
                        if (occupied.size() >= 54) {
                           break;
                        }

                        int i = MathUtils.getRandomNumberInRange(0, 53);

                        while (occupied.contains(i)) {
                           i = MathUtils.getRandomNumberInRange(0, 53);
                        }

                        IItemDrop iDrop = null;
                        if (drop instanceof IItemDrop) {
                           iDrop = (IItemDrop)drop;
                        } else if (drop instanceof CustomDrop cDrop && cDrop.getDrop().isPresent()) {
                           iDrop = (IItemDrop)cDrop.getDrop().get();
                        }

                        if (iDrop != null) {
                           ItemStack item = BukkitAdapter.adapt(iDrop.getDrop(new DropMetadataImpl(null, null), drop.getDropAmount().get()));
                           if (MythicDungeons.inst().getMythicApi() != null) {
                              String mythicItem = ItemUtils.getMythicItemType(item);
                              if (mythicItem != null) {
                                 Optional<MythicItem> mItem = MythicDungeons.inst().getMythicApi().getItemManager().getItem(mythicItem);
                                 if (mItem.isPresent()) {
                                    item = BukkitAdapter.adapt(mItem.get().generateItemStack(item.getAmount()));
                                 }
                              }
                           }

                           occupied.add(i);
                           if (instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false)) {
                              if (item != null) {
                                 Button button = new Button("item_" + i, item);
                                 ItemStack finalItem = item;
                                 int finalSlot = i;
                                 button.addAction("click", clickEvent -> {
                                    clickEvent.setCancelled(true);
                                    Player clicker = (Player)clickEvent.getWhoClicked();
                                    instance.addPlayerReward(clicker, finalItem);
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
               } else {
                  LootTable table = MythicDungeons.inst().getLootTableManager().get(this.loottableName);
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

                     for (ItemStack itemx : items) {
                        if (occupied.size() >= 54) {
                           break;
                        }

                        int i = MathUtils.getRandomNumberInRange(0, 53);

                        while (occupied.contains(i)) {
                           i = MathUtils.getRandomNumberInRange(0, 53);
                        }

                        ItemStack preItem = null;
                        if (MythicDungeons.inst().getMythicApi() != null) {
                           String mythicItem = ItemUtils.getMythicItemType(itemx);
                           if (mythicItem != null) {
                              Optional<MythicItem> mItem = MythicDungeons.inst().getMythicApi().getItemManager().getItem(mythicItem);
                              if (mItem.isPresent()) {
                                 preItem = BukkitAdapter.adapt(mItem.get().generateItemStack(itemx.getAmount()));
                              }
                           }
                        }

                        if (preItem != null) {
                           itemx = preItem;
                        }

                        occupied.add(i);
                        if (instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false)) {
                           if (itemx != null) {
                              Button button = new Button("item_" + i, itemx);
                              ItemStack finalItemx = itemx;
                              int finalI = i;
                              button.addAction("click", clickEvent -> {
                                 clickEvent.setCancelled(true);
                                 Player clicker = (Player)clickEvent.getWhoClicked();
                                 instance.addPlayerReward(clicker, finalItemx);
                                 guiInv.removeButton(finalI);
                              });
                              guiInv.setButton(i, button);
                           }
                        } else {
                           inv.setItem(i, itemx);
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
      this.menu
         .addMenuItem(
            new ChatMenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.CHEST);
                  this.button.setDisplayName("&d&lSet Loot Table");
               }

               @Override
               public void onSelect(Player player) {
                  player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eWhat's the unique name of the loot table?"));
                  player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eCurrent loot table: &6" + FunctionMythicLootTableRewards.this.loottableName));
               }

               @Override
               public void onInput(Player player, String message) {
                  if (message.contains(" ")) {
                     player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cWARNING: Loot tables cannot have spaces in their names!"));
                     player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cAll spaces will be replaced with underscores."));
                     message = message.replace(" ", "_");
                  }

                  if (!MythicDungeons.inst().getLootTableManager().contains(message)) {
                     if (MythicDungeons.inst().getMythicApi() != null) {
                        Optional<DropTable> mythicTable = MythicDungeons.inst().getMythicApi().getDropManager().getDropTable(message);
                        if (mythicTable.isPresent()) {
                           FunctionMythicLootTableRewards.this.isMythic = true;
                           FunctionMythicLootTableRewards.this.loottableName = message;
                           player.sendMessage(
                              Util.colorize(
                                 MythicDungeons.debugPrefix
                                    + "&aSet loot table to Mythic droptable '&6"
                                    + FunctionMythicLootTableRewards.this.loottableName
                                    + "&6'"
                              )
                           );
                           return;
                        }
                     }

                     player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cWARNING: No loot table by this name exists!"));
                     StringUtils.sendClickableCommand(player, MythicDungeons.debugPrefix + "&b&lClick here &bto create it!", "dungeon loot create " + message);
                  }

                  FunctionMythicLootTableRewards.this.isMythic = false;
                  FunctionMythicLootTableRewards.this.loottableName = message;
                  player.sendMessage(
                     Util.colorize(MythicDungeons.debugPrefix + "&aSet loot table to '&6" + FunctionMythicLootTableRewards.this.loottableName + "&6'")
                  );
               }
            }
         );
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.EMERALD);
            this.button.setDisplayName("&d&lExp Reward");
            this.button.setAmount(FunctionMythicLootTableRewards.this.xp);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow much EXP should this reward give?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionMythicLootTableRewards.this.xp = value.orElse(FunctionMythicLootTableRewards.this.xp);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet exp reward to '&6" + FunctionMythicLootTableRewards.this.xp + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NETHER_STAR);
            this.button.setDisplayName("&d&lExp Levels Reward");
            this.button.setAmount(FunctionMythicLootTableRewards.this.levels);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many levels should this reward give?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionMythicLootTableRewards.this.levels = value.orElse(FunctionMythicLootTableRewards.this.levels);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet exp reward to '&6" + FunctionMythicLootTableRewards.this.levels + "&a'"));
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
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            aPlayer.setHotbar(FunctionMythicLootTableRewards.this.cooldownMenu, true);
         }
      });
   }
}
