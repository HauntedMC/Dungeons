package nl.hauntedmc.dungeons.listener.instance;

import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
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
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Base listener for one live dungeon instance world.
 *
 * <p>This class enforces shared world-entry, membership, spectator, and key-item rules for both
 * playable and editable instance types.</p>
 */
public class InstanceListener {
    private final DungeonInstance instance;

    /**
     * Creates the listener for a single instance.
     */
    public InstanceListener(DungeonInstance instance) {
        this.instance = instance;
    }

    /**
     * Removes tracked players when they leave the instance world by world change.
     */
    @EventHandler
    public void onPlayerLeaveDungeon(PlayerChangedWorldEvent event) {
        if (event.getFrom() == this.instance.getInstanceWorld()) {
            Player player = event.getPlayer();
            DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
            if (this.isTrackedInstancePresence(playerSession) && !playerSession.isDisconnecting()) {
                this.instance.removePlayer(playerSession);
                Bukkit.getScheduler()
                        .runTaskLater(RuntimeContext.plugin(), this.instance::dispose, 1L);
            }
        }
    }

    /**
     * Rejects unauthorized players that enter the instance world.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerEnterDungeonWorld(PlayerChangedWorldEvent event) {
        if (event.getFrom() == this.instance.getInstanceWorld()
                || event.getPlayer().getWorld() != this.instance.getInstanceWorld()) {
            return;
        }

        this.ejectIfUnauthorized(
                event.getPlayer(), RuntimeContext.playerSessions().get(event.getPlayer()));
    }

    /**
     * Rejects unauthorized players who log in directly inside the instance world.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoinInDungeonWorld(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() != this.instance.getInstanceWorld()) {
            return;
        }

        this.ejectIfUnauthorized(player, RuntimeContext.playerSessions().get(player));
    }

    /**
     * Handles disconnect behavior, including delayed offline-kick logic for active runs.
     */
    @EventHandler
    public void onPlayerDisconnectDungeon(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        if (player.getWorld() == this.instance.getInstanceWorld()) {
            final DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
            if (!this.isTrackedInstancePresence(playerSession)) {
                return;
            }

            playerSession.setDisconnecting(true);
            PlayableInstance play = this.instance.asPlayInstance();
            if (this.instance.isEditInstance()
                    || play != null && !play.getLivingPlayers().contains(playerSession)) {
                this.instance.removePlayer(playerSession);
                this.instance.dispose();
                if (player.isDead()) {
                    playerSession.setDead(false);
                }

                playerSession.setDisconnecting(false);
            } else {
                if (this.instance.getConfig().getBoolean("players.offline_kick.enabled", true)) {
                    BukkitRunnable runnable =
                            new BukkitRunnable() {
                                public void run() {
                                    if (!player.isOnline()) {
                                        if (!CommandUtils.hasPermissionSilent(player, "dungeons.vanish")) {
                                            InstanceListener.this.instance.messagePlayers(
                                                    LangUtils.getMessage(
                                                            "instance.play.events.player-kicked",
                                                            LangUtils.placeholder("player", player.getName())));
                                        }

                                        InstanceListener.this.instance.removePlayer(playerSession);
                                        InstanceListener.this.instance.dispose();
                                    }
                                }
                            };
                    if (play != null) {
                        play.getOfflineTrackers().put(player.getUniqueId(), runnable);
                    }
                    runnable.runTaskLater(
                            RuntimeContext.plugin(),
                            this.instance.getConfig().getInt("players.offline_kick.delay_seconds", 300) * 20L);
                }

                playerSession.setDisconnecting(false);
            }
        }
    }

    /**
     * Optionally prevents explosion events from damaging blocks in instance worlds.
     */
    @EventHandler
    public void onExplodeBlocks(EntityExplodeEvent event) {
        if (event.getLocation().getWorld() == this.instance.getInstanceWorld()) {
            if (this.instance
                    .getConfig()
                    .getBoolean("rules.world.prevent_explosion_block_damage", true)) {
                event.blockList().clear();
            }
        }
    }

    /**
     * Applies durability-loss prevention rules configured for the instance.
     */
    @EventHandler
    public void onDurabilityDamage(PlayerItemDamageEvent event) {
        if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
            ItemStack item = event.getItem();
            Material mat = item.getType();
            if (this.instance.getConfig().getBoolean("rules.combat.prevent_durability_loss.armor", true)
                    && (mat.name().contains("HELMET")
                            || mat.name().contains("CHESTPLATE")
                            || mat.name().contains("LEGGINGS")
                            || mat.name().contains("BOOTS"))) {
                event.setCancelled(true);
            }

            if (this.instance.getConfig().getBoolean("rules.combat.prevent_durability_loss.weapons", true)
                    && (mat.name().contains("SWORD")
                            || mat.name().contains("AXE")
                            || mat.name().contains("BOW")
                            || mat.name().contains("CROSSBOW")
                            || mat.name().contains("TRIDENT")
                            || mat.name().contains("MACE"))) {
                event.setCancelled(true);
            }

            if (this.instance.getConfig().getBoolean("rules.combat.prevent_durability_loss.tools", true)
                    && (mat.name().contains("PICKAXE")
                            || mat.name().contains("AXE")
                            || mat.name().contains("SHOVEL")
                            || mat.name().contains("HOE")
                            || mat.name().contains("FLINT_AND_STEEL"))) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevents placing dungeon key items in the instance world.
     */
    @EventHandler
    public void onPlaceKey(BlockPlaceEvent event) {
        if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
            if (ItemUtils.verifyKeyItem(event.getItemInHand())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevents dropping dungeon key items in the instance world.
     */
    @EventHandler
    public void onDropKey(PlayerDropItemEvent event) {
        if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
            if (ItemUtils.verifyKeyItem(event.getItemDrop().getItemStack())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevents dungeon key items from despawning.
     */
    @EventHandler
    public void onKeyDespawn(ItemDespawnEvent event) {
        Item item = event.getEntity();
        ItemStack itemStack = item.getItemStack();
        if (ItemUtils.verifyKeyItem(itemStack)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents key items from being destroyed by explosions.
     */
    @EventHandler
    public void onKeyExplode(EntityDamageEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof Item item) {
            if (event.getCause() == DamageCause.ENTITY_EXPLOSION
                    || event.getCause() == DamageCause.BLOCK_EXPLOSION) {
                ItemStack itemStack = item.getItemStack();
                if (ItemUtils.verifyKeyItem(itemStack)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Guards teleports into the instance world for unauthorized players.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTeleportToDungeon(PlayerTeleportEvent event) {
        if (event.isCancelled() || event.getTo() == null) {
            return;
        }

        if (event.getTo().getWorld() != this.instance.getInstanceWorld()
                || event.getFrom().getWorld() == this.instance.getInstanceWorld()) {
            return;
        }

        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        if (playerSession == null) {
            event.setCancelled(true);
            return;
        }

        if (this.isCurrentInstanceMember(playerSession)) {
            return;
        }

        boolean bypassJoinCheck = this.hasBypassJoinPermission(player);
        if (!bypassJoinCheck) {
            event.setCancelled(true);
            LangUtils.sendMessage(player, "instance.lifecycle.entry-denied");
            return;
        }

        if (player.getGameMode() == GameMode.SPECTATOR && !this.instance.isEditInstance()) {
            LangUtils.sendMessage(player, "instance.lifecycle.stealth-join");
        }
    }

    /**
     * Removes tracked players when they teleport out of the instance world.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTeleportFromDungeon(PlayerTeleportEvent event) {
        if (!event.isCancelled() && event.getTo() != null) {
            Player player = event.getPlayer();
            DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
            if (this.isTrackedInstancePresence(playerSession) && !playerSession.isDisconnecting()) {
                if (event.getFrom().getWorld() == this.instance.getInstanceWorld()) {
                    if (event.getTo().getWorld() != this.instance.getInstanceWorld()) {
                        this.instance.removePlayer(playerSession);
                    }
                }
            }
        }
    }

    /**
     * Prevents spectator block interaction inside instance worlds.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpectator(PlayerInteractEvent event) {
        if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
            if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevents spectator entity interaction inside instance worlds.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpectator(PlayerInteractEntityEvent event) {
        if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
            if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevents spectator teleporting to entities outside the instance world.
     */
    @EventHandler
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() == this.instance.getInstanceWorld()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                if (event.getCause() == TeleportCause.SPECTATE) {
                    Location target = event.getTo();
                    if (target == null) {
                        event.setCancelled(true);
                        return;
                    }

                    if (target.getWorld() != this.instance.getInstanceWorld()) {
                        event.setCancelled(true);
                        LangUtils.sendMessage(player, "instance.play.events.spectate-deny");
                    }
                }
            }
        }
    }

    /**
     * Returns whether the session is currently part of this instance.
     */
    private boolean isCurrentInstanceMember(DungeonPlayerSession playerSession) {
        return this.instance.getPlayers().contains(playerSession);
    }

    /**
     * Returns whether this session should be treated as belonging to the instance lifecycle.
     */
    private boolean isTrackedInstancePresence(DungeonPlayerSession playerSession) {
        return playerSession != null
                && (this.isCurrentInstanceMember(playerSession)
                        || playerSession.getInstance() == this.instance);
    }

    /**
     * Teleports unauthorized players out of the instance world.
     */
    private void ejectIfUnauthorized(Player player, DungeonPlayerSession playerSession) {
        if (this.hasAuthorizedWorldAccess(player, playerSession)) {
            return;
        }

        if (playerSession != null && playerSession.getInstance() == this.instance) {
            playerSession.setInstance(null);
        }

        EntityUtils.forceTeleport(player, this.resolveUnauthorizedExit(player, playerSession));
        LangUtils.sendMessage(player, "instance.lifecycle.entry-denied");
    }

    /**
     * Returns whether a player can bypass normal join authorization checks.
     */
    private boolean hasBypassJoinPermission(Player player) {
        return CommandUtils.hasPermissionSilent(player, "dungeons.bypassjoin")
                || CommandUtils.hasPermissionSilent(
                        player, "dungeons.bypassjoin." + this.instance.getDungeon().getWorldName());
    }

    /**
     * Returns whether a player currently has valid access to remain in this world.
     */
    private boolean hasAuthorizedWorldAccess(Player player, DungeonPlayerSession playerSession) {
        return this.hasBypassJoinPermission(player)
                || playerSession != null && this.isCurrentInstanceMember(playerSession);
    }

    /**
     * Resolves a safe fallback location outside the current instance world.
     */
    private Location resolveUnauthorizedExit(Player player, DungeonPlayerSession playerSession) {
        Location savedPosition = playerSession == null ? null : playerSession.getSavedPosition();
        if (savedPosition != null && savedPosition.getWorld() != this.instance.getInstanceWorld()) {
            return savedPosition.clone();
        }

        Location respawnLocation = player.getRespawnLocation();
        if (respawnLocation != null && respawnLocation.getWorld() != this.instance.getInstanceWorld()) {
            return respawnLocation.clone();
        }

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            if (world != this.instance.getInstanceWorld()) {
                return world.getSpawnLocation().clone();
            }
        }

        return player.getLocation().clone();
    }
}
