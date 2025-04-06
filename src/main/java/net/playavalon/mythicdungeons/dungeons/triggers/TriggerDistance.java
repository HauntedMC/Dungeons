package net.playavalon.mythicdungeons.dungeons.triggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredTrigger;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceContinuous;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceProcedural;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.MathUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

@DeclaredTrigger
public class TriggerDistance extends DungeonTrigger {
   private BukkitRunnable playerScan;
   @SavedField
   private double radius = 3.0;
   @SavedField
   private int count = 1;
   @SavedField
   private boolean forEachPlayer = false;
   private int finalCount;

   public TriggerDistance(Map<String, Object> config) {
      super("Player Detector", config);
      this.setCategory(TriggerCategory.PLAYER);
      this.setHasTarget(true);
   }

   public TriggerDistance() {
      super("Player Detector");
      this.setCategory(TriggerCategory.PLAYER);
      this.setHasTarget(true);
   }

   @Override
   public void initLegacyFields(Map<String, Object> config) {
      if (config.containsKey("Radius")) {
         this.radius = (Double)config.get("Radius");
      }

      if (config.containsKey("PlayerCount")) {
         this.count = (Integer)config.get("PlayerCount");
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.DETECTOR_RAIL);
      functionButton.setDisplayName("&aPlayer Detector");
      functionButton.addLore("&eTriggered when a player or players");
      functionButton.addLore("&eare within range of this location.");
      return functionButton;
   }

   @Override
   public void onEnable() {
      final InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         if (this.count == -1) {
            this.finalCount = Math.max(instance.getLivingPlayers().size(), 1);
         } else {
            this.finalCount = this.count;
         }

         final TriggerFireEvent event = new TriggerFireEvent(instance, this);
         this.playerScan = new BukkitRunnable() {
            public void run() {
               if (TriggerDistance.this.count == -1) {
                  TriggerDistance.this.finalCount = Math.max(instance.getLivingPlayers().size(), 1);
               }

               if (!TriggerDistance.this.location.isWorldLoaded()) {
                  this.cancel();
               } else if (TriggerDistance.this.checkConditions(event)) {
                  List<Player> players = Util.getPlayersWithin(
                     TriggerDistance.this.location, TriggerDistance.this.radius, GameMode.SURVIVAL, GameMode.ADVENTURE
                  );
                  players.removeIf(playerxx -> !TriggerDistance.this.matchesRoom(playerxx.getLocation()));

                  for (UUID uuid : TriggerDistance.this.playersTriggered) {
                     Player triggeredPlayer = Bukkit.getPlayer(uuid);
                     players.remove(triggeredPlayer);
                  }

                  List<UUID> playersFoundSnapshot = new ArrayList<>(TriggerDistance.this.playersFound);
                  if (TriggerDistance.this.allowRetrigger) {
                     for (UUID uuid : playersFoundSnapshot) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (!players.contains(player)) {
                           TriggerDistance.this.playersFound.remove(uuid);
                        }
                     }
                  }

                  if (players.size() >= TriggerDistance.this.finalCount) {
                     if (TriggerDistance.this.forEachPlayer) {
                        for (Player player : players) {
                           if (!TriggerDistance.this.playersFound.contains(player.getUniqueId())) {
                              TriggerDistance.this.playersFound.add(player.getUniqueId());
                              TriggerDistance.this.trigger(MythicDungeons.inst().getMythicPlayer(player), false);
                           }
                        }

                        return;
                     }

                     for (Player playerx : players) {
                        if (!TriggerDistance.this.playersFound.contains(playerx.getUniqueId())) {
                           TriggerDistance.this.playersFound.add(playerx.getUniqueId());
                        }
                     }

                     if (TriggerDistance.this.playersFound.size() <= playersFoundSnapshot.size()) {
                        return;
                     }

                     MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(players.get(0));
                     TriggerDistance.this.trigger(mPlayer);
                  }
               }
            }
         };
         this.playerScan.runTaskTimer(MythicDungeons.inst(), 0L, 10L);
      }
   }

   private boolean matchesRoom(Location origin) {
      InstanceProcedural inst = this.instance.as(InstanceProcedural.class);
      if (inst == null) {
         return true;
      } else if (!this.limitToRoom) {
         return true;
      } else {
         InstanceRoom originRoom = inst.getRoom(origin);
         InstanceRoom thisRoom = inst.getRoom(this.location);
         return thisRoom == originRoom;
      }
   }

   @Override
   public void onDisable() {
      if (this.playerScan != null && !this.playerScan.isCancelled()) {
         this.playerScan.cancel();
         this.playerScan = null;
      }
   }

   @Override
   public void onTrigger(TriggerFireEvent event) {
      if (!this.allowRetrigger && !(this.instance instanceof InstanceContinuous) && this.playerScan != null && !this.playerScan.isCancelled()) {
         this.playerScan.cancel();
      }
   }

   @Override
   public void buildHotbarMenu() {
      this.menu
         .addMenuItem(
            new ToggleMenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.REDSTONE_TORCH);
                  this.button.setDisplayName("&d&lAllow Retrigger");
                  this.button.setEnchanted(TriggerDistance.this.allowRetrigger);
               }

               @Override
               public void onSelect(Player player) {
                  if (!TriggerDistance.this.allowRetrigger) {
                     player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
                     player.sendMessage(
                        Util.colorize(MythicDungeons.debugPrefix + "&bThis means it will only trigger every time the minimum required players are detected.")
                     );
                  } else {
                     player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
                  }

                  TriggerDistance.this.allowRetrigger = !TriggerDistance.this.allowRetrigger;
               }
            }
         );
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.DETECTOR_RAIL);
            this.button.setDisplayName("&d&lDistance");
            this.button.setAmount((int)MathUtils.round(TriggerDistance.this.radius, 0));
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat is the range of the trigger in blocks?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            TriggerDistance.this.radius = value.orElse(TriggerDistance.this.radius);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet trigger radius to '&6" + TriggerDistance.this.radius + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PLAYER_HEAD);
            this.button.setDisplayName("&d&lMinimum Players");
            this.button.setAmount(TriggerDistance.this.count);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many players must be in range of the trigger?"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eEnter '&6all&e' for all living players in the dungeon."));
         }

         @Override
         public void onInput(Player player, String message) {
            if (message.equalsIgnoreCase("all")) {
               TriggerDistance.this.count = -1;
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet number of players needed to '&6all present&a'"));
            } else {
               Optional<Integer> value = StringUtils.readIntegerInput(player, message);
               TriggerDistance.this.count = value.orElse(TriggerDistance.this.count);
               if (value.isPresent()) {
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet number of players needed to '&6" + TriggerDistance.this.count + "&a'"));
               }
            }
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.TARGET);
            this.button.setDisplayName("&d&lFor Each Player");
            this.button.setEnchanted(TriggerDistance.this.forEachPlayer);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerDistance.this.forEachPlayer) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet behaviour to '&6trigger for every player in range&a'"));
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet behaviour to '&6trigger for the nearest player&a'"));
            }

            TriggerDistance.this.forEachPlayer = !TriggerDistance.this.forEachPlayer;
         }
      });
      this.addRoomLimitToggleButton();
   }

   public void setRadius(double radius) {
      this.radius = radius;
   }

   public void setCount(int count) {
      this.count = count;
   }

   public void setForEachPlayer(boolean forEachPlayer) {
      this.forEachPlayer = forEachPlayer;
   }
}
