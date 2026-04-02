package nl.hauntedmc.dungeons.listeners.dungeonlisteners;

import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class InstanceListener {
   private final AbstractInstance instance;

   public InstanceListener(AbstractInstance instance) {
      this.instance = instance;
   }

   @EventHandler
   public void onPlayerLeaveDungeon(PlayerChangedWorldEvent event) {
      if (event.getFrom() == this.instance.getInstanceWorld()) {
         Player player = event.getPlayer();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         if (!aPlayer.isDisconnecting()) {
            this.instance.removePlayer(aPlayer);
            Bukkit.getScheduler().runTaskLater(Dungeons.inst(), this.instance::dispose, 1L);
         }
      }
   }

   @EventHandler
   public void onPlayerDisconnectDungeon(PlayerQuitEvent event) {
      final Player player = event.getPlayer();
      if (player.getWorld() == this.instance.getInstanceWorld()) {
         final DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         aPlayer.setDisconnecting(true);
         InstancePlayable play = this.instance.asPlayInstance();
         if (this.instance.isEditInstance() || play != null && !play.getLivingPlayers().contains(aPlayer)) {
            this.instance.removePlayer(aPlayer);
            this.instance.dispose();
            if (player.isDead()) {
               aPlayer.setDead(false);
            }

            aPlayer.setDisconnecting(false);
         } else {
            if (this.instance.getConfig().getBoolean("General.KickOfflinePlayers", true)) {
               BukkitRunnable runnable = new BukkitRunnable() {
                  public void run() {
                     if (!player.isOnline()) {
                        if (!HelperUtils.hasPermissionSilent(player, "dungeons.vanish")) {
                           InstanceListener.this.instance.messagePlayers(LangUtils.getMessage("instance.events.player-kicked", player.getName()));
                        }

                        InstanceListener.this.instance.removePlayer(aPlayer);
                        InstanceListener.this.instance.dispose();
                     }
                  }
               };
                if (play != null) {
                    play.getOfflineTrackers().put(player.getUniqueId(), runnable);
                }
                runnable.runTaskLater(Dungeons.inst(), this.instance.getConfig().getInt("General.KickOfflinePlayersDelay", 300) * 20L);
            }

            aPlayer.setDisconnecting(false);
         }
      }
   }

   @EventHandler
   public void onExplodeBlocks(EntityExplodeEvent event) {
      if (event.getLocation().getWorld() == this.instance.getInstanceWorld()) {
         if (this.instance.getConfig().getBoolean("Rules.PreventExplosionBlockDamage", true)) {
            event.blockList().clear();
         }
      }
   }

   @EventHandler
   public void onDurabilityDamage(PlayerItemDamageEvent event) {
      if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
         ItemStack item = event.getItem();
         Material mat = item.getType();
         if (this.instance.getConfig().getBoolean("Rules.PreventDurabilityLoss.Armor")
            && (mat.name().contains("HELMET") || mat.name().contains("CHESTPLATE") || mat.name().contains("LEGGINGS") || mat.name().contains("BOOTS"))) {
            event.setCancelled(true);
         }

         if (this.instance.getConfig().getBoolean("Rules.PreventDurabilityLoss.Weapons")
            && (
               mat.name().contains("SWORD")
                  || mat.name().contains("AXE")
                  || mat.name().contains("BOW")
                  || mat.name().contains("CROSSBOW")
                  || mat.name().contains("TRIDENT")
                  || mat.name().contains("MACE")
            )) {
            event.setCancelled(true);
         }

         if (this.instance.getConfig().getBoolean("Rules.PreventDurabilityLoss.Tools")
            && (
               mat.name().contains("PICKAXE")
                  || mat.name().contains("AXE")
                  || mat.name().contains("SHOVEL")
                  || mat.name().contains("HOE")
                  || mat.name().contains("FLINT_AND_STEEL")
            )) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onPlaceKey(BlockPlaceEvent event) {
      if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
          if (ItemUtils.verifyKeyItem(event.getItemInHand())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onDropKey(PlayerDropItemEvent event) {
      if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
          if (ItemUtils.verifyKeyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onKeyDespawn(ItemDespawnEvent event) {
      Item item = event.getEntity();
      ItemStack itemStack = item.getItemStack();
      if (ItemUtils.verifyKeyItem(itemStack)) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onKeyExplode(EntityDamageEvent event) {
      Entity ent = event.getEntity();
      if (ent instanceof Item item) {
         if (event.getCause() == DamageCause.ENTITY_EXPLOSION || event.getCause() == DamageCause.BLOCK_EXPLOSION) {
             ItemStack itemStack = item.getItemStack();
            if (ItemUtils.verifyKeyItem(itemStack)) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onTeleportToDungeon(PlayerTeleportEvent event) {
      if (!event.isCancelled()) {
         Player player = event.getPlayer();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         if (event.getTo().getWorld() == this.instance.getInstanceWorld()) {
            if (event.getFrom().getWorld() != this.instance.getInstanceWorld()) {
               if (!this.instance.getPlayers().contains(aPlayer)) {
                  if (this.instance.getConfig().getBoolean("Rules.PreventTeleportIn", false)
                     && !HelperUtils.hasPermissionSilent(player, "dungeons.bypassjoin")
                     && !HelperUtils.hasPermissionSilent(player, "dungeons.bypassjoin." + this.instance.getDungeon().getWorldName())) {
                     event.setCancelled(true);
                     LangUtils.sendMessage(player, "instance.prevent-teleport-in");
                  } else {
                     if (player.getGameMode() == GameMode.SPECTATOR && !this.instance.isEditInstance()) {
                        LangUtils.sendMessage(player, "instance.stealth-join");
                        aPlayer.setSavedPosition(event.getFrom());
                        aPlayer.setInstance(this.instance);
                     } else {
                        this.instance.addPlayer(aPlayer);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onTeleportFromDungeon(PlayerTeleportEvent event) {
      if (!event.isCancelled()) {
         Player player = event.getPlayer();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         if (aPlayer != null && !aPlayer.isDisconnecting()) {
            if (event.getFrom().getWorld() == this.instance.getInstanceWorld()) {
               if (event.getTo().getWorld() != this.instance.getInstanceWorld()) {
                  this.instance.removePlayer(aPlayer);
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onSpectator(PlayerInteractEvent event) {
      if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
         if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onSpectator(PlayerInteractEntityEvent event) {
      if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
         if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onSpectatorTeleport(PlayerTeleportEvent event) {
      Player player = event.getPlayer();
      if (player.getWorld() == this.instance.getInstanceWorld()) {
         if (player.getGameMode() == GameMode.SPECTATOR) {
            if (event.getCause() == TeleportCause.SPECTATE) {
               Location target = event.getTo();
               if (target.getWorld() != this.instance.getInstanceWorld()) {
                  event.setCancelled(true);
                  LangUtils.sendMessage(player, "instance.events.spectate-deny");
               }
            }
         }
      }
   }
}
