package net.playavalon.mythicdungeons.listeners.dungeonlisteners;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionAllowBlock;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Boss;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayListener extends InstanceListener {
   private final InstancePlayable instance;
   protected List<Location> placedBlocks = new ArrayList<>();

   public PlayListener(InstancePlayable instance) {
      super(instance);
      this.instance = instance;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPlayerConnectDungeon(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer.getInstance() == this.instance) {
         if (this.instance.getPlayerLives().get(player.getUniqueId()) == 0) {
            this.instance.removePlayer(aPlayer);
         } else {
            if (this.instance.getConfig().getBoolean("General.KickOfflinePlayers", true)) {
               BukkitRunnable tracker = this.instance.getOfflineTrackers().get(player.getUniqueId());
               if (tracker != null) {
                  tracker.cancel();
                  if (!Util.hasPermissionSilent(player, "dungeons.vanish")) {
                     this.instance.messagePlayers(LangUtils.getMessage("instance.events.player-returned", player.getName()));
                  }
               }

               if (!this.instance.getLivingPlayers().contains(aPlayer) && player.getGameMode() != GameMode.SPECTATOR) {
                  player.setGameMode(GameMode.SPECTATOR);
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPlayerDeath(PlayerDeathEvent event) {
      if (!event.isCancelled()) {
         if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
            Player player = event.getEntity();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            if (!aPlayer.isDead()) {
               aPlayer.setDead(true);
               if (this.instance.getConfig().getBoolean("Rules.HideDeathMessages", false)) {
                  event.setDeathMessage("");
               }

               if (this.instance.isLivesEnabled()) {
                  if (!this.instance.getPlayerLives().containsKey(player.getUniqueId())) {
                     return;
                  }

                  int lives = this.instance.getPlayerLives().get(player.getUniqueId());
                  this.instance.getPlayerLives().put(player.getUniqueId(), --lives);
                  if (lives <= 0) {
                     this.instance.messagePlayers(LangUtils.getMessage("instance.events.all-lives-lost", player.getName()));
                     this.instance.getLivingPlayers().remove(aPlayer);
                     if (!this.instance.getDungeon().getConfig().getBoolean("General.DeadPlayersSpectate", true)) {
                        this.instance.removePlayer(aPlayer);
                     } else {
                        PlayerInventory inv = player.getInventory();
                        Player target = null;
                        boolean foundKeys = false;
                        boolean foundDungeonItems = false;

                        for (ItemStack item : inv) {
                           if (ItemUtils.verifyKeyItem(item)) {
                              inv.remove(item);
                              event.getDrops().remove(item);
                              if (!this.instance.getLivingPlayers().isEmpty()) {
                                 foundKeys = true;
                                 target = this.instance.getLivingPlayers().get(0).getPlayer();
                                 ItemUtils.giveOrDrop(target, item);
                              }
                           }

                           if (ItemUtils.verifyDungeonItem(item)) {
                              inv.remove(item);
                              event.getDrops().remove(item);
                              if (!this.instance.getLivingPlayers().isEmpty()) {
                                 foundDungeonItems = true;
                                 target = this.instance.getLivingPlayers().get(0).getPlayer();
                                 ItemUtils.giveOrDrop(target, item);
                              }
                           }
                        }

                        ItemStack offhand = inv.getItemInOffHand();
                        if (ItemUtils.verifyKeyItem(offhand)) {
                           inv.setItemInOffHand(null);
                           event.getDrops().remove(offhand);
                           if (!this.instance.getLivingPlayers().isEmpty()) {
                              foundKeys = true;
                              target = this.instance.getLivingPlayers().get(0).getPlayer();
                              ItemUtils.giveOrDrop(target, offhand);
                           }
                        }

                        if (ItemUtils.verifyDungeonItem(offhand)) {
                           inv.setItemInOffHand(null);
                           event.getDrops().remove(offhand);
                           if (!this.instance.getLivingPlayers().isEmpty()) {
                              foundDungeonItems = true;
                              target = this.instance.getLivingPlayers().get(0).getPlayer();
                              ItemUtils.giveOrDrop(target, offhand);
                           }
                        }

                        if (target != null) {
                           if (foundKeys) {
                              this.instance.messagePlayers(LangUtils.getMessage("instance.events.key-inheritance", target.getName()));
                           }

                           if (foundDungeonItems) {
                              this.instance.messagePlayers(LangUtils.getMessage("instance.events.dungeon-item-inheritance", target.getName()));
                           }
                        }
                     }

                     if (this.instance.getDungeon().isCooldownOnLoseLives()) {
                        this.instance.getDungeon().addAccessCooldown(player);
                     }

                     return;
                  }

                  this.instance.messagePlayers(LangUtils.getMessage("instance.events.life-lost", player.getName(), String.valueOf(lives)));
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPlayerRespawn(PlayerRespawnEvent event) {
      if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
         Player player = event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         aPlayer.setDead(false);
         Location respawnLocation = aPlayer.getDungeonRespawn();
         if (respawnLocation == null) {
            respawnLocation = aPlayer.getSavedPosition();
         }

         if (respawnLocation != null) {
            event.setRespawnLocation(respawnLocation);
         }

         if (this.instance.isLivesEnabled()) {
            int lives = this.instance.getPlayerLives().getOrDefault(player.getUniqueId(), 0);
            if (lives <= 0) {
               if (this.instance.getDungeon().getConfig().getBoolean("General.DeadPlayersSpectate", true)) {
                  player.setGameMode(GameMode.SPECTATOR);
                  if (this.instance.getLivingPlayers().isEmpty()) {
                     if (this.instance.getConfig().getBoolean("General.CloseDungeonWhenAllSpectating", true)) {
                        this.instance.messagePlayers(LangUtils.getMessage("instance.events.all-lives-lost-and-spectating-auto-close"));
                        Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> {
                           List<MythicPlayer> players = new ArrayList<>(this.instance.getPlayers());
                           players.forEach(this.instance::removePlayer);
                        }, 1L);
                     } else {
                        this.instance.messagePlayers(LangUtils.getMessage("instance.events.all-lives-lost-and-spectating"));
                     }
                  }
               } else {
                  Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> this.instance.removePlayer(aPlayer), 1L);
               }
            }
         }
      }
   }

   @EventHandler
   public void onRespawnFromOutside(PlayerRespawnEvent event) {
      World deathWorld = event.getPlayer().getLocation().getWorld();
      if (deathWorld != this.instance.getInstanceWorld()) {
         if (event.getRespawnLocation().getWorld() == this.instance.getInstanceWorld()) {
            if (event.isBedSpawn() || event.isAnchorSpawn()) {
               event.setRespawnLocation(deathWorld.getSpawnLocation());
            }
         }
      }
   }

   @EventHandler
   public void removeDisplayOnChunkLoad(ChunkLoadEvent event) {
      Chunk chunk = event.getChunk();
      if (chunk.getWorld() == this.instance.getInstanceWorld()) {
         NamespacedKey chunkKey = new NamespacedKey(MythicDungeons.inst(), "cleaned");
         if (!chunk.getPersistentDataContainer().has(chunkKey)) {
            Collection<TextDisplay> displays = this.instance.getInstanceWorld().getEntitiesByClass(TextDisplay.class);
            NamespacedKey key = new NamespacedKey(MythicDungeons.inst(), "dungeonhologram");

            for (TextDisplay display : displays) {
               PersistentDataContainer data = display.getPersistentDataContainer();
               if (data.has(key, PersistentDataType.BOOLEAN)) {
                  display.remove();
               }
            }

            chunk.getPersistentDataContainer().set(chunkKey, PersistentDataType.BOOLEAN, true);
         }
      }
   }

   @EventHandler
   public void onMobSpawn(CreatureSpawnEvent event) {
      if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
         if (event.getSpawnReason() == SpawnReason.NATURAL || event.getSpawnReason() == SpawnReason.REINFORCEMENTS) {
            if (!this.instance.getConfig().getBoolean("Rules.SpawnMobs")) {
               event.setCancelled(true);
            }

            LivingEntity ent = event.getEntity();
            if (ent instanceof Animals && !this.instance.getConfig().getBoolean("Rules.SpawnAnimals")) {
               event.setCancelled(true);
            }

            if ((ent instanceof Monster || ent instanceof Boss) && !this.instance.getConfig().getBoolean("Rules.SpawnMonsters")) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler
   public void onEntitySpawn(EntitySpawnEvent event) {
      if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
         this.instance.getEntities().add(event.getEntity());
      }
   }

   @EventHandler
   public void onBreakBlock(BlockBreakEvent event) {
      if (event.getBlock().getWorld() == this.instance.getInstanceWorld()) {
         if (!this.instance.getConfig().getBoolean("Rules.AllowBreakBlocks", false)) {
            event.setCancelled(true);
         }

         Material mat = event.getBlock().getType();
         AbstractDungeon dungeon = this.instance.getDungeon();
         Location blockLoc = event.getBlock().getLocation();
         if (dungeon.getBreakWhitelist().contains(mat)) {
            event.setCancelled(false);
         }

         if (dungeon.getBreakBlacklist().contains(mat)) {
            event.setCancelled(true);
         }

         if (dungeon.isBreakPlacedBlocks() && this.placedBlocks.contains(blockLoc)) {
            event.setCancelled(false);
            this.placedBlocks.remove(blockLoc);
         }

         DungeonFunction function = this.instance.getFunctions().get(blockLoc);
         if (function instanceof FunctionAllowBlock func && func.isActive()) {
            event.setCancelled(!func.isAllowBreak());
         }
      }
   }

   @EventHandler
   public void onPlaceBlock(BlockPlaceEvent event) {
      if (event.getBlock().getWorld() == this.instance.getInstanceWorld()) {
         if (!this.instance.getConfig().getBoolean("Rules.AllowPlaceBlocks", false)) {
            event.setCancelled(true);
         }

         Material mat = event.getBlock().getType();
         AbstractDungeon dungeon = this.instance.getDungeon();
         Location blockLoc = event.getBlock().getLocation();
         if (dungeon.getPlaceWhitelist().contains(mat)) {
            event.setCancelled(false);
         }

         if (dungeon.getPlaceBlacklist().contains(mat)) {
            event.setCancelled(true);
         }

         DungeonFunction function = this.instance.getFunctions().get(blockLoc);
         if (function instanceof FunctionAllowBlock func && func.isActive()) {
            event.setCancelled(!func.isAllowPlace());
         }

         if (dungeon.isBreakPlacedBlocks() && !event.isCancelled()) {
            this.placedBlocks.add(blockLoc);
         }
      }
   }

   @EventHandler
   public void onPlaceEntity(EntityPlaceEvent event) {
      if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
         if (!this.instance.getConfig().getBoolean("Rules.AllowPlaceEntities", false)) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onDamageProtected(EntityDamageEvent event) {
      if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
         EntityType type = event.getEntityType();
         AbstractDungeon dungeon = this.instance.getDungeon();
         if (dungeon.getDamageProtectedEntities().contains(type)) {
            event.setDamage(0.0);
            event.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onInteractProtected(PlayerInteractEntityEvent event) {
      if (event.getRightClicked().getWorld() == this.instance.getInstanceWorld()) {
         EntityType type = event.getRightClicked().getType();
         AbstractDungeon dungeon = this.instance.getDungeon();
         event.setCancelled(dungeon.getInteractProtectedEntities().contains(type));
      }
   }

   @EventHandler
   public void onInteractProtectedArmorstand(PlayerArmorStandManipulateEvent event) {
      if (event.getRightClicked().getWorld() == this.instance.getInstanceWorld()) {
         EntityType type = event.getRightClicked().getType();
         AbstractDungeon dungeon = this.instance.getDungeon();
         event.setCancelled(dungeon.getInteractProtectedEntities().contains(type));
      }
   }

   @EventHandler
   public void onTeleport(PlayerTeleportEvent event) {
      if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
         Player player = event.getPlayer();
         if (event.getCause() == TeleportCause.ENDER_PEARL && !this.instance.getConfig().getBoolean("Rules.AllowEnderpearl", false)) {
            event.setCancelled(true);
            LangUtils.sendMessage(player, "instance.events.enderpearl-deny");
         }

         if (event.getCause() == TeleportCause.CHORUS_FRUIT && !this.instance.getConfig().getBoolean("Rules.AllowChorusFruit", false)) {
            event.setCancelled(true);
            LangUtils.sendMessage(player, "instance.events.chorus-fruit-deny");
         }
      }
   }

   @EventHandler
   public void onBucketEmpty(PlayerBucketEmptyEvent event) {
      if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
         Player player = event.getPlayer();
         if (!this.instance.getConfig().getBoolean("Rules.AllowBucket", false)) {
            event.setCancelled(true);
         }

         Location blockLoc = event.getBlock().getLocation();
         DungeonFunction function = this.instance.getFunctions().get(blockLoc);
         if (function instanceof FunctionAllowBlock func && func.isActive() && func.isAllowBucket()) {
            event.setCancelled(!func.isAllowPlace());
         }

         if (event.isCancelled()) {
            LangUtils.sendMessage(player, "instance.events.bucket-deny");
         }
      }
   }

   @EventHandler
   public void onBucketEmpty(PlayerBucketFillEvent event) {
      if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
         Player player = event.getPlayer();
         if (!this.instance.getConfig().getBoolean("Rules.AllowBucket", false)) {
            event.setCancelled(true);
         }

         Location blockLoc = event.getBlock().getLocation();
         DungeonFunction function = this.instance.getFunctions().get(blockLoc);
         if (function instanceof FunctionAllowBlock func && func.isActive() && func.isAllowBucket()) {
            event.setCancelled(!func.isAllowBreak());
         }

         if (event.isCancelled()) {
            LangUtils.sendMessage(player, "instance.events.bucket-deny");
         }
      }
   }

   @EventHandler
   public void onCraftBannedItem(CraftItemEvent event) {
      Location loc = event.getInventory().getLocation();
      if (loc != null) {
         if (loc.getWorld() == this.instance.getInstanceWorld()) {
            if (!this.instance.getConfig().getBoolean("Rules.AllowCraftBannedItems", false)) {
               ItemStack resultItem = event.getRecipe().getResult();
               if (ItemUtils.isItemBanned(this.instance.getDungeon(), resultItem)) {
                  event.setCancelled(true);

                  for (HumanEntity ent : event.getInventory().getViewers()) {
                     LangUtils.sendMessage(ent, "instance.events.item-banned");
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onPickupBannedItem(EntityPickupItemEvent event) {
      if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
         if (event.getEntity() instanceof Player) {
            Player player = (Player)event.getEntity();
            if (!this.instance.getConfig().getBoolean("Rules.AllowPickupBannedItems", false)) {
               ItemStack item = event.getItem().getItemStack();
               if (ItemUtils.isItemBanned(this.instance.getDungeon(), item)) {
                  event.setCancelled(true);
                  LangUtils.sendMessage(player, "instance.events.item-banned");
                  event.getItem().remove();
               }
            }
         }
      }
   }

   @EventHandler
   public void onClickBannedItem(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      if (player.getWorld() == this.instance.getInstanceWorld()) {
         if (!this.instance.getConfig().getBoolean("Rules.AllowStorageBannedItems", false)) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null) {
               if (ItemUtils.isItemBanned(this.instance.getDungeon(), clickedItem)) {
                  event.setCancelled(true);
                  LangUtils.sendMessage(player, "instance.events.item-banned");
                  clickedItem.setAmount(0);
               }
            }
         }
      }
   }

   @EventHandler
   public void onGrowth(BlockGrowEvent event) {
      if (event.getNewState().getWorld() == this.instance.getInstanceWorld()) {
         if (this.instance.getConfig().getBoolean("Rules.PreventPlantGrowth", true)) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler
   public void onSpread(BlockSpreadEvent event) {
      if (event.getNewState().getWorld() == this.instance.getInstanceWorld()) {
         if (this.instance.getConfig().getBoolean("Rules.PreventPlantGrowth", true)) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onPvP(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player) {
         if (event.getDamager() instanceof Player) {
            Player player = (Player)event.getEntity();
            if (player.getWorld() == this.instance.getInstanceWorld()) {
               event.setCancelled(!this.instance.getConfig().getBoolean("Rules.PvP", false));
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onProjectilePvP(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof Player) {
         if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile)event.getDamager();
            if (projectile.getShooter() instanceof Player) {
               Player player = (Player)event.getEntity();
               if (player.getWorld() == this.instance.getInstanceWorld()) {
                  event.setCancelled(!this.instance.getConfig().getBoolean("Rules.PvP", false));
               }
            }
         }
      }
   }

   @EventHandler
   public void onCommand(PlayerCommandPreprocessEvent event) {
      Player player = event.getPlayer();
      if (player.getWorld() == this.instance.getInstanceWorld()) {
         boolean commandAllowed = false;
         if (!this.instance.getConfig().getBoolean("Rules.AllowCommands")) {
            for (String cmd : this.instance.getConfig().getStringList("Rules.AllowedCommands")) {
               String trimmed = event.getMessage().replace("/", "");
               if (trimmed.equals(cmd) || trimmed.startsWith(cmd + " ")) {
                  commandAllowed = true;
                  break;
               }
            }
         } else {
            commandAllowed = true;

            for (String cmdx : this.instance.getConfig().getStringList("Rules.DisallowedCommands")) {
               String trimmed = event.getMessage().replace("/", "");
               if (trimmed.equals(cmdx) || trimmed.startsWith(cmdx + " ")) {
                  commandAllowed = false;
                  break;
               }
            }
         }

         if (!commandAllowed && !Util.hasPermissionSilent(player, "dungeons.admin")) {
            event.setCancelled(true);
            LangUtils.sendMessage(player, "instance.events.command-deny");
         }
      }
   }
}
