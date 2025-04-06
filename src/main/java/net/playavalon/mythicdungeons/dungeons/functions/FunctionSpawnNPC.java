package net.playavalon.mythicdungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

public class FunctionSpawnNPC extends DungeonFunction {
   @SavedField
   private int npcId = -1;
   @SavedField
   private double yaw = 0.0;

   public FunctionSpawnNPC() {
      super("spawnnpc");
      this.setCategory(FunctionCategory.LOCATION);
      this.setAllowChangingTargetType(false);
   }

   public FunctionSpawnNPC(Map<String, Object> config) {
      super("spawnnpc", config);
      this.setCategory(FunctionCategory.LOCATION);
      this.setAllowChangingTargetType(false);
   }

   @Override
   public void init() {
      super.init();
      this.setDisplayName("NPC " + this.npcId + " Spawner");
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      NPC npc = CitizensAPI.getNPCRegistry().getById(this.npcId);
      if (npc == null) {
         MythicDungeons.inst()
            .getLogger()
            .info(Util.colorize("&cWARNING :: No NPC found with id '" + this.npcId + "' in dungeon '" + this.instance.getDungeon().getWorldName() + "'!"));
      } else {
         NPC clone = MythicDungeons.inst().getMythicNPCRegistry().cloneNPC(this.npcId);
         if (clone.isSpawned()) {
            clone.despawn();
         }

         Location spawnPoint = this.location.clone();
         spawnPoint.setX(spawnPoint.getX() + 0.5);
         spawnPoint.setY(spawnPoint.getY() + 0.1);
         spawnPoint.setZ(spawnPoint.getZ() + 0.5);
         spawnPoint.setYaw((float)this.yaw);
         clone.spawn(spawnPoint, SpawnReason.PLUGIN);
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.PLAYER_HEAD);
      functionButton.setDisplayName("&dNPC Spawner");
      functionButton.addLore("&eSpawns a Citizens NPC at this");
      functionButton.addLore("&elocation.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PLAYER_HEAD);
            this.button.setDisplayName("&d&lNPC ID");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat's the NPC ID?"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent NPC is: &6" + FunctionSpawnNPC.this.npcId));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionSpawnNPC.this.npcId = value.orElse(FunctionSpawnNPC.this.npcId);
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet NPC ID to '&6" + message + "&a'"));
            FunctionSpawnNPC.this.setDisplayName("NPC " + FunctionSpawnNPC.this.npcId + " Spawner");
         }
      });
      this.menu
         .addMenuItem(
            new MenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.COMPASS);
                  this.button.setDisplayName("&d&lNPC Direction");
               }

               @Override
               public void onSelect(PlayerEvent event) {
                  Player player = event.getPlayer();
                  FunctionSpawnNPC.this.yaw = player.getLocation().getYaw();
                  player.sendMessage(
                     Util.colorize(
                        MythicDungeons.debugPrefix + "&aSet NPC spawn direction to '&6" + FunctionSpawnNPC.this.yaw + "&a' degrees. (Where you're looking.)"
                     )
                  );
               }
            }
         );
   }

   public void setNpcId(int npcId) {
      this.npcId = npcId;
   }

   public void setYaw(double yaw) {
      this.yaw = yaw;
   }
}
