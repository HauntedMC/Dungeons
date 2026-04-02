package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.Arrays;
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
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

@DeclaredFunction
public class FunctionPlaySound extends DungeonFunction {
   @SavedField
   private String sound = "";
   @SavedField
   private String soundCategory = "MASTER";
   @SavedField
   private double pitch = 1.0;
   @SavedField
   private double volume = 1.0;
   @SavedField
   private boolean playAtLocation = false;

   public FunctionPlaySound(Map<String, Object> config) {
      super("Sound", config);
      this.setTargetType(FunctionTargetType.PARTY);
      this.setCategory(FunctionCategory.LOCATION);
   }

   public FunctionPlaySound() {
      super("Sound");
      this.setTargetType(FunctionTargetType.PARTY);
      this.setCategory(FunctionCategory.LOCATION);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      if (!this.sound.isEmpty()) {
         Location loc = this.location;

         for (DungeonPlayer aPlayer : targets) {
            Player player = aPlayer.getPlayer();
            if (!this.playAtLocation) {
               loc = player.getLocation();
            }

            SoundCategory category = SoundCategory.MASTER;

            try {
               category = SoundCategory.valueOf(this.soundCategory);
            } catch (IllegalArgumentException ignored) {
            }

            player.playSound(loc, this.sound, category, (float)this.volume, (float)this.pitch);
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.NOTE_BLOCK);
      button.setDisplayName("&dSound Player");
      button.addLore("&ePlays a sound to the player(s).");
      return button;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.JUKEBOX);
            this.button.setDisplayName("&d&lSet Sound");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eWhat sound should be played?"));
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eCurrent sound: &6" + FunctionPlaySound.this.sound));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionPlaySound.this.sound = message;
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet sound to: '&6" + FunctionPlaySound.this.sound + "&a'"));
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.KNOWLEDGE_BOOK);
            this.button.setDisplayName("&d&lSet Category");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eWhat should the sound's category be?"));
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eCurrent category: &6" + FunctionPlaySound.this.soundCategory));
         }

         @Override
         public void onInput(Player player, String message) {
            try {
               SoundCategory category = SoundCategory.valueOf(message.toUpperCase());
               FunctionPlaySound.this.soundCategory = category.toString();
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet sound category to '&6" + FunctionPlaySound.this.soundCategory + "&a'"));
            } catch (IllegalArgumentException var4) {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cInvalid category! Valid categories are:"));
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&c" + Arrays.toString(SoundCategory.values())));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NOTE_BLOCK);
            this.button.setDisplayName("&d&lSet Pitch");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eWhat should the pitch of the sound be?"));
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eCurrent pitch: &6" + FunctionPlaySound.this.pitch));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            FunctionPlaySound.this.pitch = (float)value.orElse(1.0).doubleValue();
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet pitch to: '&6" + FunctionPlaySound.this.pitch + "&a'"));
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REPEATER);
            this.button.setDisplayName("&d&lSet Volume");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eWhat should the volume of the sound be?"));
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eCurrent volume: &6" + FunctionPlaySound.this.volume));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            FunctionPlaySound.this.volume = value.orElse(1.0);
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet volume to: '&6" + FunctionPlaySound.this.volume + "&a'"));
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.COMPASS);
            this.button.setDisplayName("&d&lToggle Sound Location");
            this.button.setEnchanted(FunctionPlaySound.this.playAtLocation);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionPlaySound.this.playAtLocation) {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSwitched to '&6Play the sound at the &bfunction's &6location&a'!"));
            } else {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSwitched to '&6Play the sound at the &bplayer's &6location&a'!"));
            }

            FunctionPlaySound.this.playAtLocation = !FunctionPlaySound.this.playAtLocation;
         }
      });
   }

   public void setSound(String sound) {
      this.sound = sound;
   }

   public void setSoundCategory(String soundCategory) {
      this.soundCategory = soundCategory;
   }

   public void setPitch(double pitch) {
      this.pitch = pitch;
   }

   public void setVolume(double volume) {
      this.volume = volume;
   }

   public void setPlayAtLocation(boolean playAtLocation) {
      this.playAtLocation = playAtLocation;
   }
}
