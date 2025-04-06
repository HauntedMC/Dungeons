package net.playavalon.mythicdungeons.dungeons.functions.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@DeclaredFunction
public class FunctionSequence extends FunctionMulti {
   @SavedField
   private boolean loop = true;
   private int index = 0;

   public FunctionSequence(Map<String, Object> config) {
      super("Function Sequence", config);
      this.setAllowRetriggerByDefault(true);
   }

   public FunctionSequence() {
      super("Function Sequence");
      this.setAllowRetriggerByDefault(true);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      if (!this.functions.isEmpty()) {
         DungeonFunction function = this.functions.get(this.index);
         List<MythicPlayer> functionTargets = new ArrayList<>();
         switch (this.targetType) {
            case PLAYER:
               if (triggerEvent.getDPlayer() != null) {
                  functionTargets.add(triggerEvent.getDPlayer());
               }
               break;
            case PARTY:
               functionTargets.addAll(this.instance.getPlayers());
         }

         function.runFunction(triggerEvent, functionTargets);
         this.increment();
      }
   }

   private void increment() {
      this.index++;
      if (this.index >= this.functions.size()) {
         if (!this.loop) {
            this.disable();
         }

         this.index = 0;
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
      button.setDisplayName("&bFunction Sequencer");
      button.addLore("&eRuns a list of functions");
      button.addLore("&eone at a time in order");
      button.addLore("&eeach time it's triggered.");
      return button;
   }

   @Override
   public void buildHotbarMenu() {
      super.buildHotbarMenu();
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REPEATER);
            this.button.setDisplayName("&d&lLoop Sequence");
            this.button.setEnchanted(FunctionSequence.this.loop);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionSequence.this.loop) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6sequence loops when finished&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6sequence does &cNOT &6loop when finished&a'"));
            }

            FunctionSequence.this.loop = !FunctionSequence.this.loop;
         }
      });
   }
}
