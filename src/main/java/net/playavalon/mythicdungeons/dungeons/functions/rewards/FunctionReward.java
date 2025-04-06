package net.playavalon.mythicdungeons.dungeons.functions.rewards;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.items.MythicItem;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.Hidden;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.api.parents.instances.InstanceEditable;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.avngui.GUI.GUIInventory;
import net.playavalon.mythicdungeons.avngui.GUI.Window;
import net.playavalon.mythicdungeons.avngui.GUI.Buttons.Button;
import net.playavalon.mythicdungeons.dungeons.rewards.CooldownPeriod;
import net.playavalon.mythicdungeons.menu.HotbarMenu;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class FunctionReward extends DungeonFunction {
   @SavedField
   @Hidden
   protected List<ItemStack> rewards;
   @SavedField
   protected int xp = 0;
   @SavedField
   protected int levels = 0;
   @SavedField
   protected boolean cooldownDisabled = false;
   @SavedField
   protected boolean overrideCooldown = false;
   @SavedField
   protected int period = 0;
   @SavedField
   protected int cooldownTime = 0;
   @SavedField
   protected int resetDay = 1;
   protected HotbarMenu cooldownMenu;
   protected Window rewardsGUI;
   protected Window instancedGUI;
   protected String rewardsGUIName;
   protected String instancedGUIName;
   protected List<UUID> playerRewards;

   public FunctionReward(String namespace, Map<String, Object> config) {
      super(namespace, config);
      this.targetType = FunctionTargetType.PLAYER;
      this.setCategory(FunctionCategory.PLAYER);
      this.setColour("#fceb03");
      this.setAllowRetriggerByDefault(true);
   }

   public FunctionReward(Map<String, Object> config) {
      super("Rewards", config);
      this.targetType = FunctionTargetType.PLAYER;
      this.setCategory(FunctionCategory.PLAYER);
      this.setColour("#fceb03");
      this.setAllowRetriggerByDefault(true);
   }

   public FunctionReward(String namespace) {
      super(namespace);
      this.rewards = new ArrayList<>();
      this.targetType = FunctionTargetType.PLAYER;
      this.setCategory(FunctionCategory.PLAYER);
      this.setColour("#fceb03");
   }

   public FunctionReward() {
      super("Rewards");
      this.rewards = new ArrayList<>();
      this.targetType = FunctionTargetType.PLAYER;
      this.setCategory(FunctionCategory.PLAYER);
      this.setColour("#fceb03");
   }

   @Override
   public void initLegacyFields(Map<String, Object> config) {
      if (config.containsKey("Xp")) {
         this.xp = (Integer)config.get("Xp");
      }

      if (config.containsKey("Levels")) {
         this.levels = (Integer)config.get("Levels");
      }

      if (config.containsKey("Rewards")) {
         this.rewards = (List<ItemStack>)config.get("Rewards");
      }

      if (config.containsKey("OverrideCooldown")) {
         this.overrideCooldown = (Boolean)config.get("OverrideCooldown");
      }

      if (config.containsKey("CooldownPeriod")) {
         this.period = (Integer)config.get("CooldownPeriod");
      }

      if (config.containsKey("CooldownTime")) {
         this.cooldownTime = (Integer)config.get("CooldownTime");
      }

      if (config.containsKey("ResetDay")) {
         this.resetDay = (Integer)config.get("ResetDay");
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.CHEST);
      functionButton.setDisplayName("&eRewards");
      functionButton.addLore("&eCreates a rewards menu at this");
      functionButton.addLore("&elocation.");
      return functionButton;
   }

   @Override
   public void setLocation(Location loc) {
      super.setLocation(loc);
      this.initRewardsGUI();
      this.initCooldownMenu();
   }

   public boolean overrideCooldown() {
      return this.overrideCooldown;
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      for (MythicPlayer aPlayer : targets) {
         Player player = aPlayer.getPlayer();
         if (!this.playerRewards.contains(player.getUniqueId()) && !this.instance.getDungeon().hasLootCooldown(player, this)) {
            if (this.xp > 0) {
               player.giveExp(this.xp);
               LangUtils.sendMessage(player, "instance.rewards.xp-received", String.valueOf(this.xp));
            }

            if (this.levels > 0) {
               player.giveExpLevels(this.levels);
               LangUtils.sendMessage(player, "instance.rewards.levels-received", String.valueOf(this.levels));
            }
         }

         MythicDungeons.inst().getAvnAPI().openGUI(player, this.instancedGUIName);
         if (!this.playerRewards.contains(player.getUniqueId())) {
            this.playerRewards.add(player.getUniqueId());
         }

         if (!this.instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false)) {
            this.processCooldown(player);
         }
      }
   }

   public void processCooldown(Player player) {
      if (!this.cooldownDisabled) {
         if (!this.instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false) || this.playerRewards.contains(player.getUniqueId())) {
            if (!(this.instance instanceof InstanceEditable)) {
               InstancePlayable instance = (InstancePlayable)this.instance;
               AbstractDungeon dungeon = instance.getDungeon();
               if (dungeon.isCooldownsPerReward()) {
                  if (!dungeon.hasLootCooldown(player, this)) {
                     if (!this.overrideCooldown) {
                        dungeon.addLootCooldown(player, this);
                     } else {
                        dungeon.addLootCooldown(player, this, this.getCooldownTime());
                     }

                     dungeon.saveCooldowns(player);
                  }
               } else if (!dungeon.hasLootCooldown(player, this)) {
                  instance.setReceivedRewards(player, true);
               }
            }
         }
      }
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
               } else {
                  if (instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false)) {
                     LangUtils.sendMessage(player, "instance.rewards.rewards-inv-info");
                     LangUtils.sendMessage(player, "instance.rewards.view-rewards-info");
                  }

                  GUIInventory guiInv = this.instancedGUI.getPlayersGui(player);
                  Inventory inv = guiInv.getInv();
                  int i = 0;

                  for (ItemStack item : this.rewards) {
                     ItemStack preItem = null;
                     if (MythicDungeons.inst().getMythicApi() != null) {
                        String mythicItem = ItemUtils.getMythicItemType(item);
                        if (mythicItem != null) {
                           Optional<MythicItem> mItem = MythicDungeons.inst().getMythicApi().getItemManager().getItem(mythicItem);
                           if (mItem.isPresent()) {
                              preItem = BukkitAdapter.adapt(mItem.get().generateItemStack(item.getAmount()));
                           }
                        }
                     }

                     if (preItem != null) {
                        if (ItemUtils.verifyDungeonItem(item)) {
                           ItemMeta preMeta = preItem.getItemMeta();
                           PersistentDataContainer data = preMeta.getPersistentDataContainer();
                           data.set(new NamespacedKey(MythicDungeons.inst(), "DungeonItem"), PersistentDataType.INTEGER, 1);
                           preItem.setItemMeta(preMeta);
                        }

                        preItem.addUnsafeEnchantments(item.getEnchantments());
                        item = preItem;
                     }

                     if (instance.getConfig().getBoolean("General.GiveLootAfterCompletion", false)) {
                        if (item == null) {
                           continue;
                        }

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
                     } else {
                        inv.setItem(i, item);
                     }

                     i++;
                  }
               }
            }
         }
      });
      this.instancedGUI
         .addCloseAction(
            "dropitems",
            event -> {
               if ((this.trigger == null || !this.trigger.isAllowRetrigger())
                  && (this.parentFunction == null || this.parentFunction.getTrigger() == null || !this.parentFunction.getTrigger().isAllowRetrigger())) {
                  if (!(this.instance instanceof InstanceEditable)) {
                     InstancePlayable instance = (InstancePlayable)this.instance;
                     Player player = (Player)event.getPlayer();
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

   public void initRewardsGUI() {
      this.rewardsGUIName = "editrewards_" + this.location.getBlockX() + "_" + this.location.getBlockY() + "_" + this.location.getBlockZ();
      this.rewardsGUI = new Window(this.rewardsGUIName, 45, "Modify Rewards");
      this.rewardsGUI.setCancelClick(false);
      this.rewardsGUI.addOpenAction("loaditems", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (aPlayer.isEditMode()) {
            Inventory inv = this.rewardsGUI.getPlayersGui(player).getInv();
            int i = 0;

            for (ItemStack item : this.rewards) {
               inv.setItem(i, item);
               i++;
            }
         }
      });
      this.rewardsGUI.addCloseAction("saveitems", event -> {
         Player player = (Player)event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (aPlayer.isEditMode()) {
            Inventory inv = this.rewardsGUI.getPlayersGui(player).getInv();
            this.rewards = new ArrayList<>();
            this.rewards.addAll(Arrays.asList(inv.getContents()));
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSaved rewards!"));
         }
      });
   }

   public void initCooldownMenu() {
      this.cooldownMenu = HotbarMenu.create();
      this.cooldownMenu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
            this.button.setDisplayName("&c&lBACK");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            aPlayer.previousHotbar();
         }
      });
      this.cooldownMenu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BEACON);
            this.button.setDisplayName("&d&lToggle Cooldown Enabled");
            this.button.setEnchanted(!FunctionReward.this.cooldownDisabled);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionReward.this.cooldownDisabled) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aDisabled cooldown."));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aEnabled cooldown!"));
            }

            FunctionReward.this.cooldownDisabled = !FunctionReward.this.cooldownDisabled;
         }
      });
      this.cooldownMenu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NETHER_STAR);
            this.button.setDisplayName("&e&lToggle Custom Cooldown");
            this.button.setEnchanted(FunctionReward.this.overrideCooldown);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionReward.this.overrideCooldown) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aEnabled custom reward cooldown!"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aDisabled custom reward cooldown."));
            }

            FunctionReward.this.overrideCooldown = !FunctionReward.this.overrideCooldown;
         }
      });
      this.cooldownMenu.addMenuItem(3, new MenuItem() {
         @Override
         public void buildButton() {
            if (!FunctionReward.this.overrideCooldown) {
               this.button = null;
            } else {
               this.button = new MenuButton(Material.CLOCK);
               this.button.setDisplayName("&d&lType: " + CooldownPeriod.intToPeriod(FunctionReward.this.period));
            }
         }

         @Override
         public void onSelect(PlayerEvent event) {
            if (FunctionReward.this.overrideCooldown) {
               Player player = event.getPlayer();
               MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
               FunctionReward.this.period++;
               if (FunctionReward.this.period >= CooldownPeriod.values().length) {
                  FunctionReward.this.period = 0;
               }

               CooldownPeriod period = CooldownPeriod.intToPeriod(FunctionReward.this.period);
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched cooldown type to '&6" + period.toString() + "&a'"));
               aPlayer.setHotbar(FunctionReward.this.cooldownMenu, false);
            }
         }
      });
      this.cooldownMenu.addMenuItem(4, new ChatMenuItem() {
         @Override
         public void buildButton() {
            if (!FunctionReward.this.overrideCooldown) {
               this.button = null;
            } else {
               CooldownPeriod period = CooldownPeriod.intToPeriod(FunctionReward.this.period);
               this.button = new MenuButton(Material.REPEATER);
               if (period == CooldownPeriod.TIMER) {
                  this.button.setDisplayName("&d&lCooldown in Minutes");
               } else {
                  this.button.setDisplayName("&d&lHour of Day");
               }

               this.button.setAmount(FunctionReward.this.cooldownTime);
            }
         }

         @Override
         public void onSelect(Player player) {
            if (FunctionReward.this.overrideCooldown) {
               CooldownPeriod period = CooldownPeriod.intToPeriod(FunctionReward.this.period);
               if (period == CooldownPeriod.TIMER) {
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow long in minutes should the cooldown be?"));
               } else {
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat hour of the day should the reward reset?"));
               }
            }
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionReward.this.cooldownTime = value.orElse(FunctionReward.this.cooldownTime);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet cooldown time to '&6" + FunctionReward.this.cooldownTime + "&a'"));
            }
         }
      });
      this.cooldownMenu.addMenuItem(5, new ChatMenuItem() {
         @Override
         public void buildButton() {
            if (!FunctionReward.this.overrideCooldown) {
               this.button = null;
            } else {
               CooldownPeriod period = CooldownPeriod.intToPeriod(FunctionReward.this.period);
               if (period == CooldownPeriod.WEEKLY) {
                  this.button = new MenuButton(Material.CLOCK);
                  this.button.setDisplayName("&d&lDay of Week");
                  this.button.setAmount(FunctionReward.this.resetDay);
               } else if (period == CooldownPeriod.MONTHLY) {
                  this.button = new MenuButton(Material.CLOCK);
                  this.button.setDisplayName("&d&lDay of Month");
                  this.button.setAmount(FunctionReward.this.resetDay);
               } else {
                  this.button = null;
               }
            }
         }

         @Override
         public void onSelect(Player player) {
            if (FunctionReward.this.overrideCooldown) {
               if (FunctionReward.this.period >= 3) {
                  CooldownPeriod cPeriod = CooldownPeriod.intToPeriod(FunctionReward.this.period);
                  if (cPeriod == CooldownPeriod.WEEKLY) {
                     player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat day of the week should the reward reset?"));
                  } else if (cPeriod == CooldownPeriod.MONTHLY) {
                     player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat day of the month should the reward reset?"));
                  }
               }
            }
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionReward.this.resetDay = value.orElse(FunctionReward.this.resetDay);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet reset day to '&6" + FunctionReward.this.resetDay + "&a'"));
            }
         }
      });
   }

   public Date getCooldownTime() {
      CooldownPeriod period = CooldownPeriod.intToPeriod(this.period);
      int cooldownTime = this.cooldownTime;
      if (period == CooldownPeriod.TIMER) {
         Date nextDate = period.fromNow(cooldownTime);
         return nextDate;
      } else {
         Calendar cal = Calendar.getInstance();
         if (cooldownTime > cal.get(11)) {
            cal.set(5, cal.get(5) - 1);
         }

         cal.set(11, cooldownTime);
         cal.set(12, 0);
         cal.set(13, 0);
         cal.set(14, 0);
         if (period == CooldownPeriod.WEEKLY) {
            int dayOfWeek = this.resetDay;
            if (dayOfWeek > cal.get(7)) {
               cal.set(4, cal.get(4) - 1);
            }

            cal.set(7, dayOfWeek);
         }

         if (period == CooldownPeriod.MONTHLY) {
            int dayOfMonth = this.resetDay;
            if (dayOfMonth > cal.get(7)) {
               cal.set(2, cal.get(2) - 1);
            }

            cal.set(5, dayOfMonth);
         }

         Date nextDate = period.fromDate(cal.getTime());
         return nextDate;
      }
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CHEST);
            this.button.setDisplayName("&d&lEdit Rewards");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicDungeons.inst().getAvnAPI().openGUI(player, FunctionReward.this.rewardsGUIName);
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.EMERALD);
            this.button.setDisplayName("&d&lExp Reward");
            this.button.setAmount(FunctionReward.this.xp);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow much EXP should this reward give?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionReward.this.xp = value.orElse(FunctionReward.this.xp);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet exp reward to '&6" + FunctionReward.this.xp + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NETHER_STAR);
            this.button.setDisplayName("&d&lExp Levels Reward");
            this.button.setAmount(FunctionReward.this.levels);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many levels should this reward give?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionReward.this.levels = value.orElse(FunctionReward.this.levels);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet exp reward to '&6" + FunctionReward.this.levels + "&a'"));
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
            aPlayer.setHotbar(FunctionReward.this.cooldownMenu, true);
         }
      });
   }

   public Map<String, Object> getRewardsMap() {
      Map<String, Object> map = new HashMap<>();
      int i = -1;

      for (ItemStack item : this.rewards) {
         i++;
         if (item != null) {
            map.put(String.valueOf(i), item);
         }
      }

      return map;
   }

   public List<ItemStack> getRewards() {
      return this.rewards;
   }

   public int getXp() {
      return this.xp;
   }

   public int getLevels() {
      return this.levels;
   }

   public boolean isCooldownDisabled() {
      return this.cooldownDisabled;
   }
}
