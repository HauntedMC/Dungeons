package nl.hauntedmc.dungeons.dungeons.rewards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.gui.window.GUIWindow;
import nl.hauntedmc.dungeons.api.gui.buttons.Button;
import nl.hauntedmc.dungeons.util.math.RandomCollection;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public class LootTable implements ConfigurationSerializable {
   private final String namespace;
   private int minItems = 1;
   private int maxItems = 3;
   private boolean allowDuplicates = true;
   private final Map<Integer, LootTableItem> lootItems;
   private Player editor;
   private ItemStack openSlotItem;
   private GUIWindow tableMenu;

   public LootTable(Map<String, Object> config) {
      this.namespace = (String)config.get("namespace");
      this.minItems = (Integer)config.getOrDefault("minItems", 1);
      this.maxItems = (Integer)config.getOrDefault("maxItems", 3);
      this.allowDuplicates = (Boolean)config.getOrDefault("allowDuplicates", true);
      this.lootItems = (Map<Integer, LootTableItem>)config.get("lootItems");
      this.initOpenSlotItem();
      this.initMenu();
   }

   public LootTable(String namespace) {
      this.namespace = namespace;
      this.lootItems = new HashMap<>();
      this.initOpenSlotItem();
      this.initMenu();
   }

   public List<ItemStack> randomizeItems(double... chances) {
      int bonusItems = 0;

      for (int i = 0; i < chances.length && i <= bonusItems; i++) {
         double chance = chances[i];
         if (MathUtils.getRandomBoolean(chance)) {
            bonusItems++;
         }
      }

      return this.randomizeItems(bonusItems);
   }

   public List<ItemStack> randomizeItems(int bonusItems) {
      List<ItemStack> items = new ArrayList<>();
      RandomCollection<LootTableItem> randomizer = new RandomCollection<>();

      for (LootTableItem lootItem : this.lootItems.values()) {
         randomizer.add(lootItem.getWeight(), lootItem);
      }

      int newMin = Math.min(this.minItems, this.maxItems);
      int amount = MathUtils.getRandomNumberInRange(newMin, this.maxItems) + bonusItems;
      if (!this.allowDuplicates) {
         amount = Math.min(this.lootItems.size(), amount);
      }

      List<LootTableItem> foundItems = new ArrayList<>();
      int i = 0;

      while (i < amount && foundItems.size() < this.lootItems.size()) {
         LootTableItem lootItem = randomizer.next();
         if (this.allowDuplicates || !foundItems.contains(lootItem)) {
            foundItems.add(lootItem);
            i++;
         }
      }

      for (LootTableItem lootItem : foundItems) {
         items.add(lootItem.getRandomizedItem());
      }

      return items;
   }

   private void initOpenSlotItem() {
      this.openSlotItem = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
      ItemMeta meta = this.openSlotItem.getItemMeta();

      assert meta != null;

      meta.displayName(HelperUtils.component("&7Empty Slot"));
      this.openSlotItem.setItemMeta(meta);
   }

   public void initMenu() {
      this.tableMenu = new GUIWindow("loottable_" + this.namespace, 54, "&8Edit Loot");
      this.tableMenu.setCancelClick(false);
      this.tableMenu.addCloseAction("save", event -> {
         Dungeons.inst().getLootTableManager().saveTablesConfig();
         this.setEditor(null);
      });
      this.tableMenu.addOpenAction("load", event -> {
         Player player = (Player)event.getPlayer();

         for (int slotx = 9; slotx < 54; slotx++) {
            LootTableItem lootItem = this.lootItems.get(slotx);
            if (lootItem != null) {
               Button buttonx = this.tableMenu.getButtons().get(slotx);
               buttonx.clearLore();
               buttonx.addLore(HelperUtils.colorize("&6" + lootItem.getMinItems() + "-" + lootItem.getMaxItems() + " &eitems"));
               buttonx.addLore(HelperUtils.colorize("&eWeight of &6" + lootItem.getWeight()));
               buttonx.addLore(HelperUtils.colorize(""));
               buttonx.addLore(HelperUtils.colorize("&8Click to edit."));
               buttonx.addLore(HelperUtils.colorize("&8Shift-Click to remove."));
            }
         }

         this.tableMenu.updateButtons(player);
      });
      ItemStack blockedSlot = ItemUtils.getBlockedMenuItem();
      this.tableMenu.addButton(0, new Button("blocked_0", blockedSlot));
      this.tableMenu.addButton(1, new Button("blocked_1", blockedSlot));
      this.tableMenu.addButton(2, new Button("blocked_2", blockedSlot));
      this.tableMenu.addButton(6, new Button("blocked_6", blockedSlot));
      this.tableMenu.addButton(7, new Button("blocked_7", blockedSlot));
      this.tableMenu.addButton(8, new Button("blocked_8", blockedSlot));
      Button minButton = new Button("min", Material.COAL, "&aMinimum Items");
      this.updateMinButton(minButton);
      minButton.addAction("left_click", event -> {
         event.setCancelled(true);
         if (event.getClick() == ClickType.LEFT) {
            Player player = (Player)event.getWhoClicked();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
            this.minItems = Math.min(this.maxItems, this.minItems + 1);
            this.updateMinButton(minButton);
            this.tableMenu.updateButtons(player);
         }
      });
      minButton.addAction("right_click", event -> {
         event.setCancelled(true);
         if (event.getClick() == ClickType.RIGHT) {
            Player player = (Player)event.getWhoClicked();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
            this.minItems = Math.max(0, this.minItems - 1);
            this.updateMinButton(minButton);
            this.tableMenu.updateButtons(player);
         }
      });
      this.tableMenu.addButton(3, minButton);
      Button maxButton = new Button("max", Material.GOLD_INGOT, "&aMaximum Items");
      this.updateMaxButton(maxButton);
      maxButton.addAction("left_click", event -> {
         event.setCancelled(true);
         if (event.getClick() == ClickType.LEFT) {
            Player player = (Player)event.getWhoClicked();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
            this.maxItems++;
            this.updateMaxButton(maxButton);
            this.tableMenu.updateButtons(player);
         }
      });
      maxButton.addAction("right_click", event -> {
         event.setCancelled(true);
         if (event.getClick() == ClickType.RIGHT) {
            Player player = (Player)event.getWhoClicked();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
            this.maxItems = Math.max(this.minItems, this.maxItems - 1);
            this.updateMaxButton(maxButton);
            this.tableMenu.updateButtons(player);
         }
      });
      this.tableMenu.addButton(4, maxButton);
      Button allowDupesButton = new Button("dupes", Material.DIAMOND, "&aDuplicate Items");
      this.updateAllowDuplicatesButton(allowDupesButton);
      allowDupesButton.addAction("click", event -> {
         event.setCancelled(true);
         Player player = (Player)event.getWhoClicked();
         this.allowDuplicates = !this.allowDuplicates;
         if (this.allowDuplicates) {
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
         } else {
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
         }

         this.updateAllowDuplicatesButton(allowDupesButton);
         this.tableMenu.updateButtons(player);
      });
      this.tableMenu.addButton(5, allowDupesButton);

      for (int slot = 9; slot < 54; slot++) {
         ItemStack item = this.openSlotItem.clone();
         LootTableItem tableItem = this.lootItems.get(slot);
         if (tableItem != null) {
            item = tableItem.getItem().clone();
         }

         Button button = new Button("item_" + slot, item);
         int finalSlot = slot;
         button.addAction("click", event -> {
            event.setCancelled(true);
            ClickType clickType = event.getClick();
            if (clickType == ClickType.LEFT || clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
               LootTableItem lootData = this.lootItems.get(finalSlot);
               Player player = (Player)event.getWhoClicked();
               ItemStack cursorItem = event.getCursor();
               if (lootData != null) {
                  if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
                     button.setItem(this.openSlotItem);
                     ItemUtils.giveOrDrop(player, lootData.getItem());
                     this.lootItems.remove(finalSlot);
                     this.tableMenu.updateButtons(player);
                     return;
                  }

                  this.editLootItem(lootData, player);
               }

               if (cursorItem.getType() != Material.AIR) {
                  LootTableItem lootItem = new LootTableItem(cursorItem.clone());
                  this.lootItems.put(finalSlot, lootItem);
                  ItemStack buttonItem = cursorItem.clone();
                  button.setItem(buttonItem);
                  cursorItem.setAmount(0);
                  button.addLore(HelperUtils.colorize("&6" + lootItem.getMinItems() + "-" + lootItem.getMaxItems() + " &eitems"));
                  button.addLore(HelperUtils.colorize("&eWeight of &6" + lootItem.getWeight()));
                  button.addLore(HelperUtils.colorize(""));
                  button.addLore(HelperUtils.colorize("&8Click to edit."));
                  button.addLore(HelperUtils.colorize("&8Shift-Click to remove."));
                  this.tableMenu.updateButtons(player);
               }
            }
         });
         this.tableMenu.addButton(slot, button);
      }
   }

   private void editLootItem(LootTableItem lootItem, Player player) {
      lootItem.loadMenu(this);
      Dungeons.inst().getGuiApi().openGUI(player, "loottable_" + this.namespace + "_item");
   }

   private void updateMinButton(Button minButton) {
      minButton.clearLore();
      minButton.setAmount(Math.max(1, this.minItems));
      minButton.addLore(HelperUtils.colorize("&eAt least &6" + this.minItems + " &eitems"));
      minButton.addLore(HelperUtils.colorize(""));
      minButton.addLore(HelperUtils.colorize("&7Determines the minimum amount of"));
      minButton.addLore(HelperUtils.colorize("&7the below items this loot table can"));
      minButton.addLore(HelperUtils.colorize("&7generate."));
      minButton.addLore(HelperUtils.colorize(""));
      minButton.addLore(HelperUtils.colorize("&8Left click increases."));
      minButton.addLore(HelperUtils.colorize("&8Right click decreases."));
   }

   private void updateMaxButton(Button maxButton) {
      maxButton.clearLore();
      maxButton.setAmount(Math.max(1, this.maxItems));
      maxButton.addLore(HelperUtils.colorize("&eAt most &6" + this.maxItems + " &eitems"));
      maxButton.addLore(HelperUtils.colorize(""));
      maxButton.addLore(HelperUtils.colorize("&7Determines the maximum amount of"));
      maxButton.addLore(HelperUtils.colorize("&7the below items this loot table can"));
      maxButton.addLore(HelperUtils.colorize("&7generate."));
      maxButton.addLore(HelperUtils.colorize(""));
      maxButton.addLore(HelperUtils.colorize("&8Left click increases."));
      maxButton.addLore(HelperUtils.colorize("&8Right click decreases."));
   }

   private void updateAllowDuplicatesButton(Button allowDuplicatesButton) {
      String dupeInfo = this.allowDuplicates ? "&aAllow duplicate items" : "&cDo not allow duplicate items";
      String dupeLabel = this.allowDuplicates ? "&aDuplicate Items" : "&cDuplicate Items";
      allowDuplicatesButton.setDisplayName(dupeLabel);
      allowDuplicatesButton.clearLore();
      allowDuplicatesButton.addLore(HelperUtils.colorize(dupeInfo));
      allowDuplicatesButton.addLore("");
      allowDuplicatesButton.addLore(HelperUtils.colorize("&7Toggles whether or not multiple"));
      allowDuplicatesButton.addLore(HelperUtils.colorize("&7of the same item can generate in"));
      allowDuplicatesButton.addLore(HelperUtils.colorize("&7a loot table."));
   }

   @NotNull
   public Map<String, Object> serialize() {
      Map<String, Object> map = new HashMap<>();
      map.put("namespace", this.namespace);
      map.put("minItems", this.minItems);
      map.put("maxItems", this.maxItems);
      map.put("allowDuplicates", this.allowDuplicates);
      map.put("lootItems", this.lootItems);
      return map;
   }

   public String getNamespace() {
      return this.namespace;
   }

   public int getMinItems() {
      return this.minItems;
   }

   public int getMaxItems() {
      return this.maxItems;
   }

   public boolean isAllowDuplicates() {
      return this.allowDuplicates;
   }

   public Player getEditor() {
      return this.editor;
   }

   public void setEditor(Player editor) {
      this.editor = editor;
   }
}
