package nl.hauntedmc.dungeons.listener.instance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.content.function.AllowBlockFunction;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
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
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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

/**
 * Primary gameplay listener for active playable instances.
 *
 * <p>This listener enforces the dungeon's runtime rules while players are inside an instance:
 * deaths, teleports, block interaction, entity protection, item handling, and similar
 * restrictions.</p>
 */
public class PlayListener extends InstanceListener {
    private final PlayableInstance instance;
    protected final List<Location> placedBlocks = new ArrayList<>();

    /**
     * Creates the listener for a single playable instance.
     */
    public PlayListener(PlayableInstance instance) {
        super(instance);
        this.instance = instance;
    }

    /**
     * Restores reconnecting players to the correct spectator or active state.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerConnectDungeon(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        if (playerSession == null || playerSession.getInstance() != this.instance) {
            return;
        }

        Integer livesRemaining = this.instance.getPlayerLives().get(player.getUniqueId());
        if (this.instance.isLivesEnabled() && livesRemaining != null && livesRemaining <= 0) {
            this.instance.removePlayer(playerSession);
            return;
        }

        if (this.instance.getConfig().getBoolean("players.offline_kick.enabled", true)) {
            BukkitRunnable tracker = this.instance.getOfflineTrackers().remove(player.getUniqueId());
            if (tracker != null) {
                tracker.cancel();
                if (!CommandUtils.hasPermissionSilent(player, "dungeons.vanish")) {
                    this.instance.messagePlayers(
                            LangUtils.getMessage(
                                    "instance.play.events.player-returned",
                                    LangUtils.placeholder("player", player.getName())));
                }
            }
        }

        if (!this.instance.getLivingPlayers().contains(playerSession)
                && player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    /**
     * Applies dungeon-specific death handling such as lives, spectating, and key inheritance.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!event.isCancelled()) {
            if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
                Player player = event.getEntity();
                DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                if (!playerSession.isDead()) {
                    playerSession.setDead(true);
                    if (this.instance.getConfig().getBoolean("rules.combat.hide_death_messages", false)) {
                        event.deathMessage(Component.empty());
                    }

                    if (this.instance.isLivesEnabled()) {
                        if (!this.instance.getPlayerLives().containsKey(player.getUniqueId())) {
                            return;
                        }

                        int lives = this.instance.getPlayerLives().get(player.getUniqueId());
                        this.instance.getPlayerLives().put(player.getUniqueId(), --lives);
                        if (lives <= 0) {
                            this.instance.messagePlayers(
                                    LangUtils.getMessage(
                                            "instance.play.events.all-lives-lost",
                                            LangUtils.placeholder("player", player.getName())));
                            this.instance.getLivingPlayers().remove(playerSession);
                            if (!this.instance
                                    .getDungeon()
                                    .getConfig()
                                    .getBoolean("players.spectate_on_death", true)) {
                                this.instance.removePlayer(playerSession);
                            } else {
                                PlayerInventory inv = player.getInventory();
                                Player target = null;
                                boolean foundKeys = false;
                                boolean foundDungeonItems = false;

                                // Keys and dungeon items are moved to a surviving player instead of dropping on the
                                // ground so progression-critical items cannot be lost to despawn or griefing.
                                for (ItemStack item : inv) {
                                    if (ItemUtils.verifyKeyItem(item)) {
                                        inv.remove(item);
                                        event.getDrops().remove(item);
                                        if (!this.instance.getLivingPlayers().isEmpty()) {
                                            foundKeys = true;
                                            target = this.instance.getLivingPlayers().getFirst().getPlayer();
                                            ItemUtils.giveOrDrop(target, item);
                                        }
                                    }

                                    if (ItemUtils.verifyDungeonItem(item)) {
                                        inv.remove(item);
                                        event.getDrops().remove(item);
                                        if (!this.instance.getLivingPlayers().isEmpty()) {
                                            foundDungeonItems = true;
                                            target = this.instance.getLivingPlayers().getFirst().getPlayer();
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
                                        target = this.instance.getLivingPlayers().getFirst().getPlayer();
                                        ItemUtils.giveOrDrop(target, offhand);
                                    }
                                }

                                if (ItemUtils.verifyDungeonItem(offhand)) {
                                    inv.setItemInOffHand(null);
                                    event.getDrops().remove(offhand);
                                    if (!this.instance.getLivingPlayers().isEmpty()) {
                                        foundDungeonItems = true;
                                        target = this.instance.getLivingPlayers().getFirst().getPlayer();
                                        ItemUtils.giveOrDrop(target, offhand);
                                    }
                                }

                                if (target != null) {
                                    if (foundKeys) {
                                        this.instance.messagePlayers(
                                                LangUtils.getMessage(
                                                        "instance.play.events.key-inheritance",
                                                        LangUtils.placeholder("player", target.getName())));
                                    }

                                    if (foundDungeonItems) {
                                        this.instance.messagePlayers(
                                                LangUtils.getMessage(
                                                        "instance.play.events.dungeon-item-inheritance",
                                                        LangUtils.placeholder("player", target.getName())));
                                    }
                                }
                            }

                            if (this.instance.getDungeon().isCooldownOnLoseLives()
                                    && this.instance
                                            .getDungeon()
                                            .shouldApplyAccessCooldown(this.instance, player.getUniqueId())) {
                                this.instance.getDungeon().addAccessCooldown(player);
                            }

                            return;
                        }

                        this.instance.messagePlayers(
                                LangUtils.getMessage(
                                        "instance.play.events.life-lost",
                                        LangUtils.placeholder("player", player.getName()),
                                        LangUtils.placeholder("lives", String.valueOf(lives))));
                    }
                }
            }
        }
    }

    /**
     * Restores respawn and spectator behavior after a death in the instance world.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
            Player player = event.getPlayer();
            DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
            playerSession.setDead(false);
            Location respawnLocation = playerSession.getDungeonRespawn();
            if (respawnLocation == null) {
                respawnLocation = playerSession.getSavedPosition();
            }

            if (respawnLocation != null) {
                event.setRespawnLocation(respawnLocation);
            }

            if (this.instance.isLivesEnabled()) {
                int lives = this.instance.getPlayerLives().getOrDefault(player.getUniqueId(), 0);
                if (lives <= 0) {
                    if (this.instance
                            .getDungeon()
                            .getConfig()
                            .getBoolean("players.spectate_on_death", true)) {
                        player.setGameMode(GameMode.SPECTATOR);
                        if (this.instance.getLivingPlayers().isEmpty()) {
                            if (this.instance
                                    .getConfig()
                                    .getBoolean("players.close_when_everyone_spectates", true)) {
                                this.instance.messagePlayers(
                                        LangUtils.getMessage(
                                                "instance.play.events.all-lives-lost-and-spectating-auto-close"));
                                Bukkit.getScheduler()
                                        .runTaskLater(
                                                RuntimeContext.plugin(),
                                                () -> {
                                                    List<DungeonPlayerSession> players =
                                                            new ArrayList<>(this.instance.getPlayers());
                                                    players.forEach(this.instance::removePlayer);
                                                },
                                                1L);
                            } else {
                                this.instance.messagePlayers(
                                        LangUtils.getMessage("instance.play.events.all-lives-lost-and-spectating"));
                            }
                        }
                    } else {
                        Bukkit.getScheduler()
                                .runTaskLater(
                                        RuntimeContext.plugin(),
                                        () -> this.instance.removePlayer(playerSession),
                                        1L);
                    }
                }
            }
        }
    }

    /**
     * Prevents external death respawns from anchoring players inside instance worlds.
     */
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

    /**
     * Cleans up stale dungeon hologram displays once per loaded chunk.
     */
    @EventHandler
    public void removeDisplayOnChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        if (chunk.getWorld() == this.instance.getInstanceWorld()) {
            NamespacedKey chunkKey = new NamespacedKey(RuntimeContext.plugin(), "cleaned");
            if (!chunk.getPersistentDataContainer().has(chunkKey)) {
                // Marking the chunk after cleanup prevents expensive full-world rescans on every
                // reload while still cleaning legacy holograms exactly once.
                Collection<TextDisplay> displays =
                        this.instance.getInstanceWorld().getEntitiesByClass(TextDisplay.class);
                NamespacedKey key = new NamespacedKey(RuntimeContext.plugin(), "dungeonhologram");

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

    /**
     * Applies configured natural mob spawning restrictions in the instance world.
     */
    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
            if (event.getSpawnReason() == SpawnReason.NATURAL
                    || event.getSpawnReason() == SpawnReason.REINFORCEMENTS) {
                if (!this.instance.getConfig().getBoolean("rules.spawning.natural_mobs")) {
                    event.setCancelled(true);
                }

                LivingEntity ent = event.getEntity();
                if (ent instanceof Animals
                        && !this.instance.getConfig().getBoolean("rules.spawning.animals")) {
                    event.setCancelled(true);
                }

                if ((ent instanceof Monster || ent instanceof Boss)
                        && !this.instance.getConfig().getBoolean("rules.spawning.monsters")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Tracks spawned entities so they can be cleaned up when the instance disposes.
     */
    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
            this.instance.getEntities().add(event.getEntity());
        }
    }

    /**
     * Enforces break rules, whitelists/blacklists, and function overrides.
     */
    @EventHandler
    public void onBreakBlock(BlockBreakEvent event) {
        if (event.getBlock().getWorld() == this.instance.getInstanceWorld()) {
            if (!this.instance.getConfig().getBoolean("rules.building.break_blocks", false)) {
                event.setCancelled(true);
            }

            Material mat = event.getBlock().getType();
            DungeonDefinition dungeon = this.instance.getDungeon();
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
            if (function instanceof AllowBlockFunction func && func.isActive()) {
                event.setCancelled(!func.isAllowBreak());
            }
        }
    }

    /**
     * Enforces place rules, whitelists/blacklists, and function overrides.
     */
    @EventHandler
    public void onPlaceBlock(BlockPlaceEvent event) {
        if (event.getBlock().getWorld() == this.instance.getInstanceWorld()) {
            if (!this.instance.getConfig().getBoolean("rules.building.place_blocks", false)) {
                event.setCancelled(true);
            }

            Material mat = event.getBlock().getType();
            DungeonDefinition dungeon = this.instance.getDungeon();
            Location blockLoc = event.getBlock().getLocation();
            if (dungeon.getPlaceWhitelist().contains(mat)) {
                event.setCancelled(false);
            }

            if (dungeon.getPlaceBlacklist().contains(mat)) {
                event.setCancelled(true);
            }

            DungeonFunction function = this.instance.getFunctions().get(blockLoc);
            if (function instanceof AllowBlockFunction func && func.isActive()) {
                event.setCancelled(!func.isAllowPlace());
            }

            if (dungeon.isBreakPlacedBlocks() && !event.isCancelled()) {
                this.placedBlocks.add(blockLoc);
            }
        }
    }

    /**
     * Enforces entity placement permissions in the instance world.
     */
    @EventHandler
    public void onPlaceEntity(EntityPlaceEvent event) {
        if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
            if (!this.instance.getConfig().getBoolean("rules.building.place_entities", false)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Protects configured entity types from damage inside the instance world.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageProtected(EntityDamageEvent event) {
        if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
            EntityType type = event.getEntityType();
            DungeonDefinition dungeon = this.instance.getDungeon();
            if (dungeon.getDamageProtectedEntities().contains(type)) {
                event.setDamage(0.0);
                event.setCancelled(true);
            }
        }
    }

    /**
     * Blocks interaction with configured protected entity types.
     */
    @EventHandler
    public void onInteractProtected(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getWorld() == this.instance.getInstanceWorld()) {
            EntityType type = event.getRightClicked().getType();
            DungeonDefinition dungeon = this.instance.getDungeon();
            event.setCancelled(dungeon.getInteractProtectedEntities().contains(type));
        }
    }

    /**
     * Armor stand manipulation variant of protected-entity interaction rules.
     */
    @EventHandler
    public void onInteractProtectedArmorstand(PlayerArmorStandManipulateEvent event) {
        if (event.getRightClicked().getWorld() == this.instance.getInstanceWorld()) {
            EntityType type = event.getRightClicked().getType();
            DungeonDefinition dungeon = this.instance.getDungeon();
            event.setCancelled(dungeon.getInteractProtectedEntities().contains(type));
        }
    }

    /**
     * Enforces movement-related teleport restrictions such as pearls and chorus fruit.
     */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
            Player player = event.getPlayer();
            if (event.getCause() == TeleportCause.ENDER_PEARL
                    && !this.instance.getConfig().getBoolean("rules.movement.ender_pearls", false)) {
                event.setCancelled(true);
                LangUtils.sendMessage(player, "instance.play.events.enderpearl-deny");
            }

            if (event.getCause() == TeleportCause.CONSUMABLE_EFFECT
                    && !this.instance.getConfig().getBoolean("rules.movement.chorus_fruit", false)) {
                event.setCancelled(true);
                LangUtils.sendMessage(player, "instance.play.events.chorus-fruit-deny");
            }
        }
    }

    /**
     * Enforces bucket-empty permissions and optional per-block function overrides.
     */
    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
            Player player = event.getPlayer();
            if (!this.instance.getConfig().getBoolean("rules.movement.buckets", false)) {
                event.setCancelled(true);
            }

            Location blockLoc = event.getBlock().getLocation();
            DungeonFunction function = this.instance.getFunctions().get(blockLoc);
            if (function instanceof AllowBlockFunction func && func.isActive() && func.isAllowBucket()) {
                event.setCancelled(!func.isAllowPlace());
            }

            if (event.isCancelled()) {
                LangUtils.sendMessage(player, "instance.play.events.bucket-deny");
            }
        }
    }

    /**
     * Enforces bucket-fill permissions and optional per-block function overrides.
     */
    @EventHandler
    public void onBucketEmpty(PlayerBucketFillEvent event) {
        if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
            Player player = event.getPlayer();
            if (!this.instance.getConfig().getBoolean("rules.movement.buckets", false)) {
                event.setCancelled(true);
            }

            Location blockLoc = event.getBlock().getLocation();
            DungeonFunction function = this.instance.getFunctions().get(blockLoc);
            if (function instanceof AllowBlockFunction func && func.isActive() && func.isAllowBucket()) {
                event.setCancelled(!func.isAllowBreak());
            }

            if (event.isCancelled()) {
                LangUtils.sendMessage(player, "instance.play.events.bucket-deny");
            }
        }
    }

    /**
     * Blocks crafting of banned items when the instance rules disallow it.
     */
    @EventHandler
    public void onCraftBannedItem(CraftItemEvent event) {
        Location loc = event.getInventory().getLocation();
        if (loc != null) {
            if (loc.getWorld() == this.instance.getInstanceWorld()) {
                if (!this.instance.getConfig().getBoolean("rules.items.allow_craft_banned", false)) {
                    ItemStack resultItem = event.getRecipe().getResult();
                    if (ItemUtils.isItemBanned(this.instance.getDungeon(), resultItem)) {
                        event.setCancelled(true);

                        for (HumanEntity ent : event.getInventory().getViewers()) {
                            LangUtils.sendMessage(ent, "instance.play.events.item-banned");
                        }
                    }
                }
            }
        }
    }

    /**
     * Blocks pickup of banned items when the instance rules disallow it.
     */
    @EventHandler
    public void onPickupBannedItem(EntityPickupItemEvent event) {
        if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
            if (event.getEntity() instanceof Player player) {
                if (!this.instance.getConfig().getBoolean("rules.items.allow_pickup_banned", false)) {
                    ItemStack item = event.getItem().getItemStack();
                    if (ItemUtils.isItemBanned(this.instance.getDungeon(), item)) {
                        event.setCancelled(true);
                        LangUtils.sendMessage(player, "instance.play.events.item-banned");
                        event.getItem().remove();
                    }
                }
            }
        }
    }

    /**
     * Blocks inventory interaction with banned items when storage is restricted.
     */
    @EventHandler
    public void onClickBannedItem(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (player.getWorld() == this.instance.getInstanceWorld()) {
            if (!this.instance.getConfig().getBoolean("rules.items.allow_storage_banned", false)) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null) {
                    if (ItemUtils.isItemBanned(this.instance.getDungeon(), clickedItem)) {
                        event.setCancelled(true);
                        LangUtils.sendMessage(player, "instance.play.events.item-banned");
                        clickedItem.setAmount(0);
                    }
                }
            }
        }
    }

    /**
     * Prevents crop growth when plant growth is disabled for the instance.
     */
    @EventHandler
    public void onGrowth(BlockGrowEvent event) {
        if (event.getNewState().getWorld() == this.instance.getInstanceWorld()) {
            if (this.instance.getConfig().getBoolean("rules.world.prevent_plant_growth", true)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevents block spread when plant growth is disabled for the instance.
     */
    @EventHandler
    public void onSpread(BlockSpreadEvent event) {
        if (event.getNewState().getWorld() == this.instance.getInstanceWorld()) {
            if (this.instance.getConfig().getBoolean("rules.world.prevent_plant_growth", true)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Enforces PVP rules for direct player-to-player damage.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getDamager() instanceof Player) {
                if (player.getWorld() == this.instance.getInstanceWorld()) {
                    event.setCancelled(!this.instance.getConfig().getBoolean("rules.combat.pvp", false));
                }
            }
        }
    }

    /**
     * Enforces PVP rules for projectile-based player damage.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectilePvP(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getDamager() instanceof Projectile projectile) {
                if (projectile.getShooter() instanceof Player) {
                    if (player.getWorld() == this.instance.getInstanceWorld()) {
                        event.setCancelled(!this.instance.getConfig().getBoolean("rules.combat.pvp", false));
                    }
                }
            }
        }
    }

    /**
     * Enforces command allow/deny lists while players are inside the instance world.
     */
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() == this.instance.getInstanceWorld()) {
            boolean commandAllowed = false;
            if (!this.instance.getConfig().getBoolean("rules.commands.allow_all")) {
                for (String cmd : this.instance.getConfig().getStringList("rules.commands.allow_list")) {
                    String trimmed = event.getMessage().replace("/", "");
                    if (trimmed.equals(cmd) || trimmed.startsWith(cmd + " ")) {
                        commandAllowed = true;
                        break;
                    }
                }
            } else {
                commandAllowed = true;

                for (String deniedCommand : this.instance.getConfig().getStringList("rules.commands.deny_list")) {
                    String trimmed = event.getMessage().replace("/", "");
                    if (trimmed.equals(deniedCommand) || trimmed.startsWith(deniedCommand + " ")) {
                        commandAllowed = false;
                        break;
                    }
                }
            }

            if (!commandAllowed && !CommandUtils.hasPermissionSilent(player, "dungeons.admin")) {
                event.setCancelled(true);
                LangUtils.sendMessage(player, "instance.play.events.command-deny");
            }
        }
    }
}
