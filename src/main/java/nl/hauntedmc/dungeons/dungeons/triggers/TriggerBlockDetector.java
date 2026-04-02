package nl.hauntedmc.dungeons.dungeons.triggers;

import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredTrigger;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.parents.TriggerCategory;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

@DeclaredTrigger
public class TriggerBlockDetector extends DungeonTrigger {
   @SavedField
   private boolean onBreak = false;
   @SavedField
   private String material = "ANY";

   public TriggerBlockDetector(Map<String, Object> config) {
      super("Block Detector", config);
      this.setCategory(TriggerCategory.PLAYER);
   }

   public TriggerBlockDetector() {
      super("Block Detector");
      this.setCategory(TriggerCategory.PLAYER);
   }

   @Override
   public void init() {
      super.init();
      this.setDisplayName(this.material + " Block Detector");
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onBlockPlace(BlockPlaceEvent event) {
      Player player = event.getPlayer();
      World world = player.getWorld();
      if (world == this.instance.getInstanceWorld()) {
         Block block = event.getBlock();
         Location targetLoc = block.getLocation();
         if (targetLoc.equals(this.location)) {
            event.setCancelled(false);
            if (this.material.equals("ANY") || block.getType() == Material.valueOf(this.material)) {
               if (!this.onBreak) {
                  this.trigger(Dungeons.inst().getDungeonPlayer(player));
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onBlockBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      World world = player.getWorld();
      if (world == this.instance.getInstanceWorld()) {
         Block block = event.getBlock();
         Location targetLoc = block.getLocation();
         if (targetLoc.equals(this.location)) {
            event.setCancelled(false);
            if (this.material.equals("ANY") || block.getType() == Material.valueOf(this.material)) {
               if (this.onBreak) {
                  this.trigger(Dungeons.inst().getDungeonPlayer(player));
               }
            }
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.OBSERVER);
      button.setDisplayName("&aBlock Detector");
      button.addLore("&eTriggered when a block is placed");
      button.addLore("&eor broken at this location.");
      button.addLore("");
      button.addLore("&7Also allows placing or breaking");
      button.addLore("&7blocks at this location.");
      return button;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.OBSERVER);
            this.button.setDisplayName("&d&lBreak or Place");
            this.button.setEnchanted(TriggerBlockDetector.this.onBreak);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerBlockDetector.this.onBreak) {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet detector to '&6trigger on &bbreak&a'"));
            } else {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet detector to '&6trigger on &bplace&a'"));
            }

            TriggerBlockDetector.this.onBreak = !TriggerBlockDetector.this.onBreak;
         }
      });
      this.menu
         .addMenuItem(
            new ChatMenuItem() {
               @Override
               public void buildButton() {
                  Material mat;
                  if (TriggerBlockDetector.this.material.equals("ANY")) {
                     mat = Material.STONE;
                  } else {
                     mat = Material.valueOf(TriggerBlockDetector.this.material);
                  }

                  this.button = new MenuButton(mat);
                  this.button.setDisplayName("&d&lSet Block");
               }

               @Override
               public void onSelect(Player player) {
                  player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eWhat block needs to be placed here? ('ANY' for any block.)"));
                  player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eCurrent block: &6" + TriggerBlockDetector.this.material));
               }

               @Override
               public void onInput(Player player, String message) {
                  String matString = message.toUpperCase();

                  try {
                     if (matString.equals("ANY")) {
                        TriggerBlockDetector.this.material = matString;
                        return;
                     }

                     Material mat = Material.valueOf(matString);
                     if (!mat.isBlock()) {
                        throw new IllegalArgumentException("Invalid block");
                     }

                     TriggerBlockDetector.this.material = matString;
                     TriggerBlockDetector.this.setDisplayName(TriggerBlockDetector.this.material + " Block Detector");
                     player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet block needed to '&6" + TriggerBlockDetector.this.material + "&a'"));
                  } catch (IllegalArgumentException var5) {
                     player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cYou must specify a valid block material!"));
                     StringUtils.sendClickableLink(
                        player,
                        Dungeons.logPrefix + HelperUtils.colorize("&bClick here to view a list of valid materials."),
                        "https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html"
                     );
                  }
               }
            }
         );
   }
}
