package nl.hauntedmc.dungeons.runtime.instance.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import nl.hauntedmc.dungeons.content.function.AllowBlockFunction;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameplayRulesContractTest {
    @TempDir Path directory;

    static Block block(RunFixture f) {
        var block = mock(Block.class);
        when(block.getWorld()).thenReturn(f.world);
        when(block.getLocation()).thenReturn(new Location(f.world, 1, 70, 1));
        when(block.getType()).thenReturn(Material.STONE);
        return block;
    }

    @Test
    void breakRulesHonorWhitelistBlacklistAndActivatedLocationOverridesWithoutLeakingToOtherWorlds()
            throws Exception {
        var settings = new YamlConfiguration();
        settings.set("rules.building.break_blocks", false);
        settings.set("rules.building.break_whitelist", List.of("STONE"));
        settings.set("rules.building.break_blacklist", List.of("STONE"));
        try (var f = new RunFixture(directory, settings)) {
            var listener = (PlayListener) f.run.getListener();
            var state = new PlayerState();
            var block = block(f);
            var event = new BlockBreakEvent(block, state.player);
            listener.onBreakBlock(event);
            assertTrue(event.isCancelled());
            f.dungeon.getBreakBlacklist().clear();
            listener.onBreakBlock(event);
            assertFalse(event.isCancelled());
            f.dungeon.getBreakWhitelist().clear();
            listener.onBreakBlock(event);
            assertTrue(event.isCancelled());
            var override = new AllowBlockFunction(Map.of("allowBreak", true));
            override.initializeFields();
            f.run.getFunctions().put(block.getLocation(), override);
            listener.onBreakBlock(event);
            assertTrue(event.isCancelled());
            override.runFunction(null, List.of());
            listener.onBreakBlock(event);
            assertFalse(event.isCancelled());
            override.onDisable();
            listener.onBreakBlock(event);
            assertTrue(event.isCancelled());
            when(block.getWorld()).thenReturn(state.originWorld);
            event.setCancelled(false);
            listener.onBreakBlock(event);
            assertFalse(event.isCancelled());
        }
    }

    @Test
    void pvpRulesApplyToDirectAndPlayerShotProjectileDamageOnlyInsideTheRun() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            state.location = new Location(f.world, 0, 70, 0);
            var attacker = new PlayerState();
            var projectile = mock(Projectile.class);
            when(projectile.getShooter()).thenReturn(attacker.player);
            var listener = (PlayListener) f.run.getListener();
            for (boolean allowed : List.of(false, true))
                for (Entity damager : List.of(attacker.player, projectile)) {
                    f.run.getConfig().set("rules.combat.pvp", allowed);
                    var event = TestEvents.cancellable(EntityDamageByEntityEvent.class);
                    when(event.getEntity()).thenReturn(state.player);
                    when(event.getDamager()).thenReturn(damager);
                    listener.onPvP(event);
                    listener.onProjectilePvP(event);
                    assertEquals(!allowed, event.isCancelled());
                }
            state.location = new Location(state.originWorld, 0, 70, 0);
            f.run.getConfig().set("rules.combat.pvp", false);
            var event = TestEvents.cancellable(EntityDamageByEntityEvent.class);
            when(event.getEntity()).thenReturn(state.player);
            when(event.getDamager()).thenReturn(attacker.player);
            listener.onPvP(event);
            assertFalse(event.isCancelled());
        }
    }

    @Test
    void movementRulesBlockRestrictedPearlsAndChorusWhileAllowingPluginTeleports()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            state.location = new Location(f.world, 0, 70, 0);
            var listener = (PlayListener) f.run.getListener();
            for (boolean allowed : List.of(false, true))
                for (var cause :
                        List.of(
                                PlayerTeleportEvent.TeleportCause.ENDER_PEARL,
                                PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT,
                                PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                    f.run.getConfig().set("rules.movement.ender_pearls", allowed);
                    f.run.getConfig().set("rules.movement.chorus_fruit", allowed);
                    var event = TestEvents.cancellable(PlayerTeleportEvent.class);
                    when(event.getPlayer()).thenReturn(state.player);
                    when(event.getCause()).thenReturn(cause);
                    listener.onTeleport(event);
                    assertEquals(
                            !allowed && cause != PlayerTeleportEvent.TeleportCause.PLUGIN,
                            event.isCancelled());
                }
        }
    }

    @Test
    void naturalSpawnRestrictionsDoNotCancelMechanicSpawnsAndTrackingIgnoresOtherWorlds()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            f.run.getConfig().set("rules.spawning.natural_mobs", false);
            f.run.getConfig().set("rules.spawning.animals", false);
            f.run.getConfig().set("rules.spawning.monsters", false);
            var listener = (PlayListener) f.run.getListener();
            var mob = mock(Monster.class);
            when(mob.getWorld()).thenReturn(f.world);
            for (var reason :
                    List.of(
                            CreatureSpawnEvent.SpawnReason.NATURAL,
                            CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
                            CreatureSpawnEvent.SpawnReason.CUSTOM,
                            CreatureSpawnEvent.SpawnReason.SPAWNER)) {
                var event = TestEvents.cancellable(CreatureSpawnEvent.class);
                when(event.getEntity()).thenReturn(mob);
                when(event.getSpawnReason()).thenReturn(reason);
                listener.onMobSpawn(event);
                assertEquals(
                        reason == CreatureSpawnEvent.SpawnReason.NATURAL
                                || reason == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
                        event.isCancelled());
            }
            var spawn = mock(EntitySpawnEvent.class);
            when(spawn.getEntity()).thenReturn(mob);
            listener.onEntitySpawn(spawn);
            assertTrue(f.run.getEntities().contains(mob));
            var other = mock(Entity.class);
            when(spawn.getEntity()).thenReturn(other);
            listener.onEntitySpawn(spawn);
            assertFalse(f.run.getEntities().contains(other));
        }
    }

    @Test
    void durabilityProtectionKeepsArmorWeaponAndToolCategoriesIndependent() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            state.location = new Location(f.world, 0, 70, 0);
            var listener = f.run.getListener();
            for (String category : List.of("armor", "weapons", "tools")) {
                for (String key : List.of("armor", "weapons", "tools"))
                    f.run.getConfig()
                            .set(
                                    "rules.combat.prevent_durability_loss." + key,
                                    key.equals(category));
                for (var material :
                        List.of(
                                Material.DIAMOND_BOOTS,
                                Material.DIAMOND_SWORD,
                                Material.DIAMOND_PICKAXE,
                                Material.DIAMOND_AXE,
                                Material.STICK)) {
                    var item = mock(ItemStack.class);
                    when(item.getType()).thenReturn(material);
                    var event = TestEvents.cancellable(PlayerItemDamageEvent.class);
                    when(event.getPlayer()).thenReturn(state.player);
                    when(event.getItem()).thenReturn(item);
                    listener.onDurabilityDamage(event);
                    boolean protectedItem =
                            switch (category) {
                                case "armor" -> material == Material.DIAMOND_BOOTS;
                                case "weapons" ->
                                        material == Material.DIAMOND_SWORD
                                                || material == Material.DIAMOND_AXE;
                                default ->
                                        material == Material.DIAMOND_PICKAXE
                                                || material == Material.DIAMOND_AXE;
                            };
                    assertEquals(protectedItem, event.isCancelled(), category + " " + material);
                }
            }
        }
    }

    @Test
    void commandRulesMatchCaseInsensitiveBoundariesAndRequireAnExplicitGameplayBypass()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            state.location = new Location(f.world, 0, 70, 0);
            var listener = (PlayListener) f.run.getListener();
            f.run.getConfig().set("rules.commands.allow_all", true);
            f.run.getConfig().set("rules.commands.deny_list", List.of("kill"));
            for (String message : List.of("/kill", "/KILL someone", "/killall")) {
                var event = TestEvents.cancellable(PlayerCommandPreprocessEvent.class);
                when(event.getPlayer()).thenReturn(state.player);
                when(event.getMessage()).thenReturn(message);
                listener.onCommand(event);
                assertEquals(!message.equals("/killall"), event.isCancelled(), message);
            }
            f.run.getConfig().set("rules.commands.allow_all", false);
            f.run.getConfig().set("rules.commands.allow_list", List.of("help"));
            for (String message : List.of("/help", "/HELP page", "/helpful")) {
                var event = TestEvents.cancellable(PlayerCommandPreprocessEvent.class);
                when(event.getPlayer()).thenReturn(state.player);
                when(event.getMessage()).thenReturn(message);
                listener.onCommand(event);
                assertEquals(message.equals("/helpful"), event.isCancelled(), message);
            }
            when(state.player.hasPermission("dungeons.bypass.commands")).thenReturn(true);
            var bypass = TestEvents.cancellable(PlayerCommandPreprocessEvent.class);
            when(bypass.getPlayer()).thenReturn(state.player);
            when(bypass.getMessage()).thenReturn("/blocked");
            listener.onCommand(bypass);
            assertFalse(bypass.isCancelled());
        }
    }

    @Test
    void placementRulesTrackOnlyAcceptedPlacementsForTheBreakPlacedBlocksException()
            throws Exception {
        var settings = new YamlConfiguration();
        settings.set("rules.building.place_blocks", false);
        settings.set("rules.building.break_blocks", false);
        settings.set("rules.building.break_placed_blocks", true);
        try (var f = new RunFixture(directory, settings)) {
            var listener = (PlayListener) f.run.getListener();
            var state = new PlayerState();
            var block = block(f);
            var denied = TestEvents.cancellable(BlockPlaceEvent.class);
            when(denied.getBlock()).thenReturn(block);
            listener.onPlaceBlock(denied);
            assertTrue(denied.isCancelled());
            assertTrue(listener.placedBlocks.isEmpty());
            f.dungeon.getPlaceWhitelist().add(Material.STONE);
            var accepted = TestEvents.cancellable(BlockPlaceEvent.class);
            when(accepted.getBlock()).thenReturn(block);
            listener.onPlaceBlock(accepted);
            assertFalse(accepted.isCancelled());
            assertEquals(List.of(block.getLocation()), listener.placedBlocks);
            var breaking = new BlockBreakEvent(block, state.player);
            listener.onBreakBlock(breaking);
            assertFalse(breaking.isCancelled());
            f.dungeon.getPlaceBlacklist().add(Material.STONE);
            var blacklisted = TestEvents.cancellable(BlockPlaceEvent.class);
            when(blacklisted.getBlock()).thenReturn(block);
            listener.onPlaceBlock(blacklisted);
            assertTrue(blacklisted.isCancelled());
            assertTrue(listener.placedBlocks.isEmpty());
        }
    }

    @Test
    void bucketOverridesUsePlacePermissionForEmptyingAndBreakPermissionForFilling()
            throws Exception {
        var settings = new YamlConfiguration();
        settings.set("rules.movement.buckets", false);
        try (var f = new RunFixture(directory, settings)) {
            var state = new PlayerState();
            state.location = new Location(f.world, 0, 70, 0);
            var listener = (PlayListener) f.run.getListener();
            var block = block(f);
            var empty = TestEvents.cancellable(PlayerBucketEmptyEvent.class);
            var fill = TestEvents.cancellable(PlayerBucketFillEvent.class);
            when(empty.getPlayer()).thenReturn(state.player);
            when(empty.getBlock()).thenReturn(block);
            when(fill.getPlayer()).thenReturn(state.player);
            when(fill.getBlock()).thenReturn(block);
            listener.onBucketEmpty(empty);
            listener.onBucketEmpty(fill);
            assertTrue(empty.isCancelled());
            assertTrue(fill.isCancelled());
            var override =
                    new AllowBlockFunction(
                            Map.of("allowBucket", true, "allowPlace", false, "allowBreak", true));
            override.initializeFields();
            override.runFunction(null, List.of());
            f.run.getFunctions().put(block.getLocation(), override);
            listener.onBucketEmpty(empty);
            listener.onBucketEmpty(fill);
            assertTrue(empty.isCancelled());
            assertFalse(fill.isCancelled());
            override.onDisable();
            fill.setCancelled(false);
            listener.onBucketEmpty(fill);
            assertTrue(fill.isCancelled());
            state.location = new Location(state.originWorld, 0, 70, 0);
            empty.setCancelled(false);
            fill.setCancelled(false);
            listener.onBucketEmpty(empty);
            listener.onBucketEmpty(fill);
            assertFalse(empty.isCancelled());
            assertFalse(fill.isCancelled());
        }
    }

    @Test
    void bannedItemsCannotEnterThroughCraftingPickupOrStorageUnlessThatRuleIsEnabled()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            f.dungeon.getBannedItems().add("DIAMOND");
            var listener = (PlayListener) f.run.getListener();
            var state = new PlayerState();
            state.location = new Location(f.world, 0, 70, 0);
            for (boolean allowed : List.of(false, true)) {
                f.run.getConfig().set("rules.items.allow_craft_banned", allowed);
                f.run.getConfig().set("rules.items.allow_pickup_banned", allowed);
                f.run.getConfig().set("rules.items.allow_storage_banned", allowed);
                var item = PlayerState.stack(2);
                when(item.getType()).thenReturn(Material.DIAMOND);
                var recipe = mock(Recipe.class);
                when(recipe.getResult()).thenReturn(item);
                var inventory = mock(CraftingInventory.class);
                when(inventory.getLocation()).thenReturn(new Location(f.world, 0, 70, 0));
                when(inventory.getViewers()).thenReturn(List.of(state.player));
                var craft = TestEvents.cancellable(CraftItemEvent.class);
                when(craft.getInventory()).thenReturn(inventory);
                when(craft.getRecipe()).thenReturn(recipe);
                listener.onCraftBannedItem(craft);
                assertEquals(!allowed, craft.isCancelled());
                var entity = mock(Item.class);
                when(entity.getItemStack()).thenReturn(item);
                var pickup = TestEvents.cancellable(EntityPickupItemEvent.class);
                when(pickup.getEntity()).thenReturn(state.player);
                when(pickup.getItem()).thenReturn(entity);
                listener.onPickupBannedItem(pickup);
                assertEquals(!allowed, pickup.isCancelled());
                verify(entity, allowed ? never() : times(1)).remove();
                var click = TestEvents.cancellable(InventoryClickEvent.class);
                when(click.getWhoClicked()).thenReturn(state.player);
                when(click.getCurrentItem()).thenReturn(item);
                listener.onClickBannedItem(click);
                assertEquals(!allowed, click.isCancelled());
                assertEquals(allowed ? 2 : 0, item.getAmount());
            }
        }
    }

    @Test
    void protectedEntitiesGrowthAndExplosionRulesApplyOnlyInsideTheirOwningWorld()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var listener = (PlayListener) f.run.getListener();
            var outsider = new PlayerState();
            f.dungeon.getDamageProtectedEntities().add(EntityType.ARMOR_STAND);
            f.dungeon.getInteractProtectedEntities().add(EntityType.ARMOR_STAND);
            for (var world : List.of(f.world, outsider.originWorld)) {
                boolean inside = world == f.world;
                var entity = mock(Entity.class);
                when(entity.getWorld()).thenReturn(world);
                when(entity.getType()).thenReturn(EntityType.ARMOR_STAND);
                var damage = TestEvents.cancellable(EntityDamageEvent.class);
                when(damage.getEntity()).thenReturn(entity);
                when(damage.getEntityType()).thenReturn(EntityType.ARMOR_STAND);
                listener.onDamageProtected(damage);
                assertEquals(inside, damage.isCancelled());
                var interact = TestEvents.cancellable(PlayerInteractEntityEvent.class);
                when(interact.getRightClicked()).thenReturn(entity);
                listener.onInteractProtected(interact);
                assertEquals(inside, interact.isCancelled());
                for (boolean prevent : List.of(false, true)) {
                    f.run.getConfig().set("rules.world.prevent_plant_growth", prevent);
                    f.run.getConfig().set("rules.world.prevent_explosion_block_damage", prevent);
                    var state = mock(BlockState.class);
                    when(state.getWorld()).thenReturn(world);
                    var growth = TestEvents.cancellable(BlockGrowEvent.class);
                    when(growth.getNewState()).thenReturn(state);
                    var spread = TestEvents.cancellable(BlockSpreadEvent.class);
                    when(spread.getNewState()).thenReturn(state);
                    listener.onGrowth(growth);
                    listener.onSpread(spread);
                    assertEquals(inside && prevent, growth.isCancelled());
                    assertEquals(inside && prevent, spread.isCancelled());
                    var blocks = new ArrayList<>(List.of(block(f)));
                    var explosion = mock(EntityExplodeEvent.class);
                    when(explosion.getLocation()).thenReturn(new Location(world, 0, 70, 0));
                    when(explosion.blockList()).thenReturn(blocks);
                    listener.onExplodeBlocks(explosion);
                    assertEquals(inside && prevent ? 0 : 1, blocks.size());
                }
            }
        }
    }
}
