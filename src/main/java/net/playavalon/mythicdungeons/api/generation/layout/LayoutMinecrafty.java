package net.playavalon.mythicdungeons.api.generation.layout;

import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.generation.rooms.Connector;
import net.playavalon.mythicdungeons.api.generation.rooms.DungeonRoomContainer;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.generation.rooms.WhitelistEntry;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonProcedural;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.RandomCollection;
import net.playavalon.mythicdungeons.utility.helpers.MathUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class LayoutMinecrafty extends Layout {
   public LayoutMinecrafty(DungeonProcedural dungeon, YamlConfiguration config) {
      super(dungeon, config);
   }

   @Override
   protected boolean tryConnectors(InstanceRoom from) {
      if (from.getAvailableConnectors().isEmpty()) {
         return false;
      } else {
         for (Connector connector : from.getAvailableConnectors()) {
            if (MathUtils.getRandomBoolean(connector.getSuccessChance())) {
               if (this.queuedRoomCount >= this.maxRooms) {
                  from.setEndRoom(true);
                  return false;
               }

               if (this.tryConnector(connector, from)) {
                  from.getUsedConnectors().add(connector);
               }
            }
         }

         return true;
      }
   }

   @Override
   public RandomCollection<DungeonRoomContainer> filterRooms(Connector connector, InstanceRoom from) {
      RandomCollection<DungeonRoomContainer> weightedRooms = new RandomCollection<>();

      for (WhitelistEntry entry : connector.getValidRooms(this.dungeon, from.getSource().getOrigin())) {
         DungeonRoomContainer room = entry.getRoom(this.dungeon);
         if (!(from.getDepth() < room.getDepth().getMin())
            && (room.getDepth().getMax() == -1.0 || !(from.getDepth() > room.getDepth().getMax()))
            && !this.isRoomMaxedOut(room)) {
            double weight = entry.getWeight();
            int count = this.getRoomCount(room);
            if (count < room.getOccurrences().getMin()) {
               weight *= room.getOccurrences().getMin() - count + 1.0;
            }

            weightedRooms.add(weight, room);
         }
      }

      return weightedRooms;
   }

   @Override
   protected boolean verifyLayout() {
      if (this.DEBUG && this.roomCount < this.minRooms) {
         MythicDungeons.inst().getLogger().warning(Util.colorize("&c-- Not enough rooms!! " + this.roomCount));
      }

      return super.verifyLayout() && this.roomCount >= this.minRooms && this.roomCount <= this.maxRooms;
   }

   @Override
   public void initConnectorEditMenu() {
      super.initConnectorEditMenu();
      if (this.dungeon.getLayout() instanceof LayoutMinecrafty) {
         this.connectorEditMenu
            .addMenuItem(
               new ChatMenuItem() {
                  @Override
                  public void buildButton() {
                     this.button = new MenuButton(Material.ENDER_EYE);
                     this.button.setDisplayName("&d&lConnector Use Chance");
                     this.button.addLore("&eHow likely a room will generate");
                     this.button.addLore("&eat this connector.");
                  }

                  @Override
                  public void onSelect(Player player) {
                     MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
                     Connector connector = mPlayer.getActiveConnector();
                     if (connector != null) {
                        double chance = connector.getSuccessChance();
                        player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow likely is a room to generate at this connector? (0.0-100.00%)"));
                        player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent chance: &6" + MathUtils.round(chance, 2) + "%"));
                     }
                  }

                  @Override
                  public void onInput(Player player, String message) {
                     MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
                     Connector connector = mPlayer.getActiveConnector();
                     if (connector != null) {
                        Optional<Double> value = StringUtils.readDoubleInput(player, message);
                        connector.setSuccessChance(value.orElse(connector.getSuccessChance()) / 100.0);
                        if (value.isPresent()) {
                           double chance = value.get();
                           player.sendMessage(
                              Util.colorize(MythicDungeons.debugPrefix + "&aSet generation chance to '&6" + MathUtils.round(chance, 2) + "%&a'")
                           );
                           if (chance >= 1.0) {
                              player.sendMessage(
                                 Util.colorize(MythicDungeons.debugPrefix + "&bNOTE: 100% is not a guarantee! Rooms still require enough space to generate.")
                              );
                           }
                        }
                     }
                  }
               }
            );
      }
   }
}
