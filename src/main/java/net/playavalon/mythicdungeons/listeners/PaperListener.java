package net.playavalon.mythicdungeons.listeners;

import io.papermc.paper.event.player.AsyncChatDecorateEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.generation.rooms.Connector;
import net.playavalon.mythicdungeons.api.generation.rooms.DungeonRoomContainer;
import net.playavalon.mythicdungeons.api.generation.rooms.WhitelistEntry;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonProcedural;
import net.playavalon.mythicdungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.util.BoundingBox;

public class PaperListener extends AvalonListener {
   @EventHandler(
      priority = EventPriority.LOWEST
   )
   @Override
   public void onRoomNameInput(AsyncPlayerChatEvent event) {
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onRoomNameInput(AsyncChatDecorateEvent event) {
      Player player = event.player();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         if (aPlayer.getInstance() != null) {
            InstanceEditableProcedural instance = aPlayer.getInstance().as(InstanceEditableProcedural.class);
            if (instance != null) {
               DungeonProcedural dungeon = instance.getDungeon();
               if (aPlayer.isAwaitingRoomName()) {
                  event.result(Component.text(""));
                  event.setCancelled(true);
                  String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
                  Pattern pat = Pattern.compile("[^a-z0-9]", 2);
                  Matcher matcher = pat.matcher(message);
                  if (matcher.find()) {
                     LangUtils.sendMessage(player, "instance.editmode.room-name-invalid");
                  } else {
                     aPlayer.setAwaitingRoomName(false);
                     DungeonRoomContainer room;
                     Connector connector = aPlayer.getActiveConnector();
                     if (aPlayer.isAddingWhitelistEntry()) {
                         room = aPlayer.getActiveRoom();
                         DungeonRoomContainer targetRoom = dungeon.getRoom(message);
                        if (targetRoom != null) {
                           player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                           if (connector != null) {
                              connector.getRoomWhitelist().add(new WhitelistEntry(targetRoom));
                              LangUtils.sendMessage(player, "instance.editmode.room-whitelist.add-success", message);
                           } else {
                              room.getRoomWhitelist().add(new WhitelistEntry(targetRoom));
                              LangUtils.sendMessage(player, "instance.editmode.room-whitelist.add-success", message);
                              aPlayer.setAddingWhitelistEntry(false);
                           }
                        }
                     } else if (aPlayer.isEditingWhitelistEntry()) {
                         room = aPlayer.getActiveRoom();
                         DungeonRoomContainer targetRoom = dungeon.getRoom(message);
                        if (targetRoom != null) {
                           player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                           player.sendMessage(Util.colorize("&cNOT IMPLEMENTED!"));
                           aPlayer.setEditingWhitelistEntry(false);
                        }
                     } else if (aPlayer.isRemovingWhitelistEntry()) {
                         room = aPlayer.getActiveRoom();
                         DungeonRoomContainer targetRoom = dungeon.getRoom(message);
                        if (targetRoom != null) {
                           player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                           List<WhitelistEntry> snapshot = null;
                           if (connector != null) {
                              snapshot = new ArrayList<>(connector.getRoomWhitelist());
                           }

                           if (snapshot == null) {
                              snapshot = new ArrayList<>(room.getRoomWhitelist());
                           }

                           for (WhitelistEntry entry : snapshot) {
                              if (entry.getRoomName().equals(message)) {
                                 if (connector != null) {
                                    connector.getRoomWhitelist().remove(entry);
                                 } else {
                                    room.getRoomWhitelist().remove(entry);
                                 }
                              }
                           }

                           LangUtils.sendMessage(player, "instance.editmode.room-whitelist.remove-success", message);
                           aPlayer.setRemovingWhitelistEntry(false);
                        }
                     } else {
                        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                        BoundingBox bounds = Util.captureBoundingBox(aPlayer.getPos1(), aPlayer.getPos2());
                        room = dungeon.defineRoom(message, bounds);
                        if (room != null) {
                           Bukkit.getScheduler().runTask(MythicDungeons.inst(), () -> instance.setRoomLabel(room));
                           LangUtils.sendMessage(player, "instance.editmode.room-created", message);
                           aPlayer.setPos1(null);
                           aPlayer.setPos2(null);
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
