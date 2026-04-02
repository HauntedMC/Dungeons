package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.RemoteTriggerEvent;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.dungeons.triggers.TriggerRemote;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@DeclaredFunction
public class FunctionRemoteTrigger extends DungeonFunction {
   @SavedField
   private String triggerName = "trigger";
   @SavedField
   private double range = 0.0;

   public FunctionRemoteTrigger(Map<String, Object> config) {
      super("Signal Sender", config);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.DUNGEON);
   }

   public FunctionRemoteTrigger() {
      super("Signal Sender");
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.DUNGEON);
   }

   @Override
   public void init() {
      super.init();
      this.setDisplayName("Signal: " + this.triggerName);
   }

   @Override
   public void initLegacyFields(Map<String, Object> config) {
      if (config.containsKey("TriggerName")) {
         this.triggerName = (String)config.get("TriggerName");
      }
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         TriggerRemote remoteTrig = new TriggerRemote();
         remoteTrig.setTriggerName(this.triggerName);
         RemoteTriggerEvent event = new RemoteTriggerEvent(
            remoteTrig.getTriggerName(), remoteTrig, instance, this.range, this.location, triggerEvent.getDPlayer()
         );
         Bukkit.getPluginManager().callEvent(event);
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.REDSTONE_TORCH);
      functionButton.setDisplayName("&6Signal Sender");
      functionButton.addLore("&eSends a signal to any Signal");
      functionButton.addLore("&eReceiver triggers.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PAPER);
            this.button.setDisplayName("&d&lSignal Name");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat is the name of the signal we will send?"));
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent signal name is: &6" + FunctionRemoteTrigger.this.triggerName));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionRemoteTrigger.this.triggerName = message;
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet signal name to '&6" + message + "&a'"));
            FunctionRemoteTrigger.this.setDisplayName("Signal: " + FunctionRemoteTrigger.this.triggerName);
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REPEATER);
            this.button.setDisplayName("&d&lSignal Range");
            this.button.setAmount((int)MathUtils.round(FunctionRemoteTrigger.this.range, 0));
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow far does the signal reach? (0 for infinite.)"));
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent range: " + FunctionRemoteTrigger.this.range));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            FunctionRemoteTrigger.this.range = value.orElse(FunctionRemoteTrigger.this.range);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet signal range to '&6" + FunctionRemoteTrigger.this.range + "&a'"));
            }
         }
      });
   }

   public void setTriggerName(String triggerName) {
      this.triggerName = triggerName;
   }

   public double getRange() {
      return this.range;
   }

   public void setRange(double range) {
      this.range = range;
   }
}
