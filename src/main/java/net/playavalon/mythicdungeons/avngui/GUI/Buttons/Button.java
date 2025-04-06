package net.playavalon.mythicdungeons.avngui.GUI.Buttons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import net.playavalon.mythicdungeons.avngui.GUI.Window;
import net.playavalon.mythicdungeons.avngui.GUI.Actions.Action;
import net.playavalon.mythicdungeons.avngui.Utility.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public class Button {
   private String id;
   private ItemStack item;
   private ArrayList<String> commands;
   private HashMap<String, Action<InventoryClickEvent>> actions;

   public Button(@NotNull String id, @NotNull Material mat, @NotNull String display) {
      this.id = id;
      this.item = new ItemStack(mat);
      ItemMeta meta = this.item.getItemMeta();
      meta.setDisplayName(StringUtils.fullColor(display));
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
      this.item.setItemMeta(meta);
      this.commands = new ArrayList<>();
      this.actions = new HashMap<>();
      ButtonManager.put(this);
   }

   public Button(@NotNull String id, @NotNull ItemStack item) {
      this.id = id;
      this.item = item.clone();
      ItemMeta meta = this.item.getItemMeta();
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
      this.item.setItemMeta(meta);
      this.commands = new ArrayList<>();
      this.actions = new HashMap<>();
      ButtonManager.put(this);
   }

   public Button(Button button) {
      this.id = button.getId();
      this.item = button.item.clone();
      ItemMeta meta = this.item.getItemMeta();
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
      this.item.setItemMeta(meta);
      this.commands = button.getCommands();
      this.actions = button.getActions();
   }

   public final String getId() {
      return this.id;
   }

   public final ItemStack getItem() {
      return this.item;
   }

   public final void setItem(ItemStack item) {
      this.item = item;
   }

   public final void setAmount(int amount) {
      this.item.setAmount(amount);
   }

   public final int getAmount() {
      return this.item.getAmount();
   }

   public final void setEnchanted(boolean enchanted) {
      if (enchanted) {
         this.item.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
      } else {
         this.item.removeEnchantment(Enchantment.AQUA_AFFINITY);
      }
   }

   public final boolean isEnchanted() {
      return this.item.getEnchantments().containsKey(Enchantment.AQUA_AFFINITY);
   }

   public final void setDisplayName(String display) {
      ItemMeta meta = this.item.getItemMeta();
      meta.setDisplayName(StringUtils.fullColor(display));
      this.item.setItemMeta(meta);
   }

   public final String getDisplayName() {
      ItemMeta meta = this.item.getItemMeta();
      return meta.getDisplayName();
   }

   public final void setLore(List<String> lore) {
      ItemMeta meta = this.item.getItemMeta();
      meta.setLore(lore);
      this.item.setItemMeta(meta);
   }

   public final void addLore(List<String> lines) {
      ItemMeta meta = this.item.getItemMeta();
      ArrayList<String> lore;
      if (meta.getLore() == null) {
         lore = new ArrayList<>();
      } else {
         lore = new ArrayList<>(meta.getLore());
      }

      lore.addAll(lines);
      meta.setLore(lore);
      this.item.setItemMeta(meta);
   }

   public final void addLore(String line) {
      ItemMeta meta = this.item.getItemMeta();
      ArrayList<String> lore;
      if (meta.getLore() == null) {
         lore = new ArrayList<>();
      } else {
         lore = new ArrayList<>(meta.getLore());
      }

      lore.add(line);
      meta.setLore(lore);
      this.item.setItemMeta(meta);
   }

   public final List<String> getLore() {
      ItemMeta meta = this.item.getItemMeta();
      return meta.getLore();
   }

   public final void clearLore() {
      ItemMeta meta = this.item.getItemMeta();
      meta.setLore(new ArrayList());
      this.item.setItemMeta(meta);
   }

   public final void addCommand(String command) {
      this.commands.add(command);
   }

   public final void addCommands(List<String> commands) {
      this.commands.addAll(commands);
   }

   public final ArrayList<String> getCommands() {
      return this.commands;
   }

   public final void runCommands() {
      for (String command : this.commands) {
         Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
      }
   }

   public final void addAction(String id, Action<InventoryClickEvent> action) {
      this.actions.put(id, action);
   }

   public final void removeAction(String id) {
      this.actions.remove(id);
   }

   public final HashMap<String, Action<InventoryClickEvent>> getActions() {
      return this.actions;
   }

   public final void runActions(InventoryClickEvent event) {
      for (Entry<String, Action<InventoryClickEvent>> pair : this.actions.entrySet()) {
         pair.getValue().run(event);
      }
   }

   public void click(InventoryClickEvent event, Window window) {
      this.runCommands();
      this.runActions(event);
   }

   public Button clone() {
      Button button = new Button(this.id, this.item);
      button.addCommands(button.getCommands());
      return button;
   }
}
