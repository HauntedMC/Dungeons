package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@DeclaredFunction
public class FunctionChunkLoad extends DungeonFunction {
   @SavedField
   protected int radius = 0;

   public FunctionChunkLoad(Map<String, Object> config) {
      super("Chunk Load", config);
      this.targetType = FunctionTargetType.NONE;
      this.setCategory(FunctionCategory.DUNGEON);
      this.setAllowRetriggerByDefault(true);
   }

   public FunctionChunkLoad() {
      super("Chunk Load");
      this.targetType = FunctionTargetType.NONE;
      this.setCategory(FunctionCategory.DUNGEON);
      this.setAllowRetriggerByDefault(true);
   }

   @Override
   public void onDisable() {
      Chunk chunk = this.location.getChunk();

      for (int x = -this.radius; x <= this.radius; x++) {
         for (int z = -this.radius; z <= this.radius; z++) {
            Chunk c = chunk.getWorld().getChunkAt(chunk.getX() + x, chunk.getZ() + z);
            if (c.isForceLoaded()) {
               c.setForceLoaded(false);
            }
         }
      }

      if (chunk.isForceLoaded()) {
         chunk.setForceLoaded(false);
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.STRUCTURE_BLOCK);
      functionButton.setDisplayName("&6Chunk Loader");
      functionButton.addLore("&eForce-loads the chunks in");
      functionButton.addLore("&ea radius around this function");
      functionButton.addLore("&eusing it as the center.");
      functionButton.addLore("Triggering again releases the chunk.");
      functionButton.addLore("&7Useful for keeping mobs and");
      functionButton.addLore("&7redstone responsive even when");
      functionButton.addLore("&7players are far away.");
      return functionButton;
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      List<Chunk> chunks = new ArrayList<>();
      Chunk chunk = this.location.getChunk();
      chunks.add(chunk);
      if (this.radius == 0) {
         chunk.setForceLoaded(!chunk.isForceLoaded());
      } else {
         for (int x = -this.radius; x <= this.radius; x++) {
            for (int z = -this.radius; z <= this.radius; z++) {
               chunks.add(chunk.getWorld().getChunkAt(chunk.getX() + x, chunk.getZ() + z));
            }
         }

         for (Chunk c : chunks) {
            c.setForceLoaded(!c.isForceLoaded());
         }
      }
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void onSelect(Player player) {
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aRadius of chunks to load around this function. (0 for default single chunk)"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> optRadius = StringUtils.readIntegerInput(player, message);
            FunctionChunkLoad.this.radius = Math.max(optRadius.orElse(FunctionChunkLoad.this.radius), 0);
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet radius to '&6" + FunctionChunkLoad.this.radius + "&a'"));
         }

         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE);
            this.button.setDisplayName("&d&lRadius");
            this.button.setAmount(FunctionChunkLoad.this.radius);
         }
      });
   }
}
