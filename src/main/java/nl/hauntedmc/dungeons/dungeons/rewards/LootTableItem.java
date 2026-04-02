package nl.hauntedmc.dungeons.dungeons.rewards;

import java.util.HashMap;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.gui.window.GUIWindow;
import nl.hauntedmc.dungeons.api.gui.buttons.Button;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class LootTableItem implements ConfigurationSerializable {
   private final ItemStack item;
   private int weight = 1;
   private int minItems = 1;
   private int maxItems = 1;
   private GUIWindow itemMenu;

   public LootTableItem(Map<String, Object> config) {
      this.item = (ItemStack)config.getOrDefault("item", new ItemStack(Material.RED_STAINED_GLASS_PANE));
      this.weight = (Integer)config.getOrDefault("weight", 1);
      this.minItems = (Integer)config.getOrDefault("minItems", 1);
      this.maxItems = (Integer)config.getOrDefault("maxItems", 1);
   }

   public LootTableItem(ItemStack item) {
      this.item = item;
   }

   public ItemStack getRandomizedItem() {
      ItemStack item = this.item.clone();
      item.setAmount(item.getAmount() * MathUtils.getRandomNumberInRange(this.minItems, this.maxItems));
      return item;
   }

   public void loadMenu(LootTable table) {
      this.itemMenu = new GUIWindow("loottable_" + table.getNamespace() + "_item", 27, "&8Edit Loot Item");
      this.itemMenu.addCloseAction("save", event -> {
         Dungeons.inst().getLootTableManager().saveTablesConfig();
         table.setEditor(null);
      });
      Button backButton = new Button("back", Material.RED_STAINED_GLASS_PANE, "&cBack");
      backButton.addAction("click", event -> {
         Player player = (Player)event.getWhoClicked();
         Dungeons.inst().getGuiApi().openGUI(player, "loottable_" + table.getNamespace());
      });
      this.itemMenu.addButton(4, backButton);
      Button minButton = new Button("min", Material.COAL, "&aMinimum Items");
      this.updateMinButton(minButton);
      minButton.addAction("left_click", event -> {
         if (event.getClick() == ClickType.LEFT) {
            Player player = (Player)event.getWhoClicked();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
            this.minItems = Math.min(this.maxItems, this.minItems + 1);
            this.updateMinButton(minButton);
            this.itemMenu.updateButtons(player);
         }
      });
      minButton.addAction("right_click", event -> {
         if (event.getClick() == ClickType.RIGHT) {
            Player player = (Player)event.getWhoClicked();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
            this.minItems = Math.max(0, this.minItems - 1);
            this.updateMinButton(minButton);
            this.itemMenu.updateButtons(player);
         }
      });
      this.itemMenu.addButton(12, minButton);
      Button maxButton = new Button("max", Material.GOLD_INGOT, "&aMaximum Items");
      this.updateMaxButton(maxButton);
      maxButton.addAction("left_click", event -> {
         if (event.getClick() == ClickType.LEFT) {
            Player player = (Player)event.getWhoClicked();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
            this.maxItems++;
            this.updateMaxButton(maxButton);
            this.itemMenu.updateButtons(player);
         }
      });
      maxButton.addAction("right_click", event -> {
         if (event.getClick() == ClickType.RIGHT) {
            Player player = (Player)event.getWhoClicked();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
            this.maxItems = Math.max(this.minItems, this.maxItems - 1);
            this.updateMaxButton(maxButton);
            this.itemMenu.updateButtons(player);
         }
      });
      this.itemMenu.addButton(13, maxButton);
      Button weightButton = new Button("max", Material.EMERALD, "&aItem Weight");
      this.updateWeightButton(weightButton);
      weightButton.addAction("left_click", event -> {
         if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT) {
            Player player = (Player)event.getWhoClicked();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
            int amount = 1;
            if (event.getClick() == ClickType.SHIFT_LEFT) {
               amount = 5;
            }

            this.weight += amount;
            this.updateWeightButton(weightButton);
            this.itemMenu.updateButtons(player);
         }
      });
      weightButton.addAction("right_click", event -> {
         if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
            Player player = (Player)event.getWhoClicked();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
            int amount = 1;
            if (event.getClick() == ClickType.SHIFT_RIGHT) {
               amount = 5;
            }

            this.weight = Math.max(0, this.weight - amount);
            this.updateWeightButton(weightButton);
            this.itemMenu.updateButtons(player);
         }
      });
      this.itemMenu.addButton(14, weightButton);
   }

   private void updateMinButton(Button minButton) {
      minButton.setAmount(Math.max(1, Math.min(this.minItems, 999)));
      minButton.clearLore();
      minButton.addLore(HelperUtils.colorize("&eAt least &6" + this.minItems + " &eitems"));
      minButton.addLore(HelperUtils.colorize(""));
      minButton.addLore(HelperUtils.colorize("&7Determines the min amount of this"));
      minButton.addLore(HelperUtils.colorize("&7item when it appears."));
      minButton.addLore(HelperUtils.colorize(""));
      minButton.addLore(HelperUtils.colorize("&8Left click increases."));
      minButton.addLore(HelperUtils.colorize("&8Right click decreases."));
   }

   private void updateMaxButton(Button maxButton) {
      maxButton.setAmount(Math.max(1, Math.min(this.maxItems, 999)));
      maxButton.clearLore();
      maxButton.addLore(HelperUtils.colorize("&eAt most &6" + this.maxItems + " &eitems"));
      maxButton.addLore(HelperUtils.colorize(""));
      maxButton.addLore(HelperUtils.colorize("&7Determines the max amount of this"));
      maxButton.addLore(HelperUtils.colorize("&7item when it appears."));
      maxButton.addLore(HelperUtils.colorize(""));
      maxButton.addLore(HelperUtils.colorize("&8Left click increases."));
      maxButton.addLore(HelperUtils.colorize("&8Right click decreases."));
   }

   private void updateWeightButton(Button weightButton) {
      weightButton.clearLore();
      weightButton.addLore(HelperUtils.colorize("&eWeight of &6" + this.weight));
      weightButton.addLore(HelperUtils.colorize(""));
      weightButton.addLore(HelperUtils.colorize("&7Determines the chance of this item"));
      weightButton.addLore(HelperUtils.colorize("&7compared to others. An item with a"));
      weightButton.addLore(HelperUtils.colorize("&7weight of 4 is twice as common as an"));
      weightButton.addLore(HelperUtils.colorize("&7item with a weight of 2."));
      weightButton.addLore(HelperUtils.colorize(""));
      weightButton.addLore(HelperUtils.colorize("&8Left and Shift-Left click increases."));
      weightButton.addLore(HelperUtils.colorize("&8Right and Shift-Right click decreases."));
   }

   @NotNull
   public Map<String, Object> serialize() {
      Map<String, Object> map = new HashMap<>();
      map.put("item", this.item);
      map.put("weight", this.weight);
      map.put("minItems", this.minItems);
      map.put("maxItems", this.maxItems);
      return map;
   }

   public ItemStack getItem() {
      return this.item;
   }

   public int getWeight() {
      return this.weight;
   }

   public int getMinItems() {
      return this.minItems;
   }

   public int getMaxItems() {
      return this.maxItems;
   }
}
