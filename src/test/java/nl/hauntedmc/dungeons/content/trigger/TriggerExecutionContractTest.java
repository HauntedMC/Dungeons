package nl.hauntedmc.dungeons.content.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.content.instance.play.StaticInstance;
import nl.hauntedmc.dungeons.event.*;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.item.ItemUtils;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.*;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

class TriggerExecutionContractTest {
    @TempDir Path directory;

    static <T extends DungeonTrigger> T bind(T trigger, RunFixture f) {
        trigger.initializeFields();
        trigger.setInstance(f.run);
        trigger.setLocation(new Location(f.world, 0, 70, 0));
        trigger.setAllowRetrigger(true);
        return trigger;
    }

    static DungeonPlayerSession source(RunFixture f, PlayerState state) {
        state.location = new Location(f.world, 0, 70, 0);
        var session = f.session(state);
        session.setInstance(f.run);
        return session;
    }

    @Test
    void startTriggerSchedulesOnlyItsOwnRunAndPreservesThePlayerSource() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var session = source(f, new PlayerState());
            var trigger = bind(new DungeonStartTrigger(), f);
            trigger.onDungeonStart(
                    new DungeonStartEvent(new StaticInstance(f.dungeon, null), List.of(session)));
            assertTrue(f.scheduler.jobs.isEmpty());
            trigger.onDungeonStart(new DungeonStartEvent(f.run, List.of(session)));
            assertEquals(1, f.scheduler.jobs.size());
            assertTrue(f.scheduler.immediate.isEmpty());
            f.scheduler.jobs.getFirst().run();
            assertEquals(1, f.scheduler.immediate.size());
            f.scheduler.immediate.getFirst().run();
            var event = ArgumentCaptor.forClass(TriggerFireEvent.class);
            verify(Bukkit.getPluginManager()).callEvent(event.capture());
            assertSame(session, event.getValue().getPlayerSession());
            assertSame(f.run, event.getValue().getInstance());
        }
    }

    @Test
    void leaveTriggersCannotCancelOrExecuteForAnotherRun() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var session = source(f, new PlayerState());
            var trigger = bind(new LeaveDungeonTrigger(Map.of("preventLeaving", true)), f);
            var other = new PlayerLeaveDungeonEvent(new StaticInstance(f.dungeon, null), session);
            trigger.onPlayerLeave(other);
            assertFalse(other.isCancelled());
            assertTrue(f.scheduler.immediate.isEmpty());
            var own = new PlayerLeaveDungeonEvent(f.run, session);
            trigger.onPlayerLeave(own);
            assertTrue(own.isCancelled());
            assertEquals(1, f.scheduler.immediate.size());
        }
    }

    @Test
    void remoteSignalsHonorCancellationNameRunAndInclusiveSpatialBounds() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var trigger = bind(new RemoteTrigger(Map.of("triggerName", "open")), f);
            trigger.onTriggerReceive(new RemoteTriggerEvent("other", null, f.run, 0, null, null));
            trigger.onTriggerReceive(
                    new RemoteTriggerEvent(
                            "open", null, f.run, 5, new Location(f.world, 6, 70, 0), null));
            trigger.onTriggerReceive(
                    new RemoteTriggerEvent(
                            "open", null, new StaticInstance(f.dungeon, null), 0, null, null));
            var cancelled = new RemoteTriggerEvent("open", null, f.run, 0, null, null);
            cancelled.setCancelled(true);
            trigger.onTriggerReceive(cancelled);
            assertTrue(f.scheduler.immediate.isEmpty());
            trigger.onTriggerReceive(
                    new RemoteTriggerEvent(
                            "open", null, f.run, 5, new Location(f.world, 3, 70, 4), null));
            assertEquals(1, f.scheduler.immediate.size());
        }
    }

    @Test
    void redstoneTriggersRequireARisingEdgeAndRespectTheirRateLimitAndDisableCleanup()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var trigger = bind(new RedstoneTrigger(), f);
            var block = mock(Block.class);
            when(block.getWorld()).thenReturn(f.world);
            when(block.getLocation()).thenReturn(trigger.getLocation());
            trigger.onRedstoneSignal(new BlockRedstoneEvent(block, 0, 15));
            trigger.onRedstoneSignal(new BlockRedstoneEvent(block, 15, 15));
            trigger.onRedstoneSignal(new BlockRedstoneEvent(block, 15, 0));
            trigger.onRedstoneSignal(new BlockRedstoneEvent(block, 0, 15));
            assertEquals(1, f.scheduler.immediate.size());
            assertEquals(2, f.scheduler.jobs.getFirst().delay);
            f.scheduler.jobs.getFirst().run();
            trigger.onRedstoneSignal(new BlockRedstoneEvent(block, 15, 0));
            trigger.onRedstoneSignal(new BlockRedstoneEvent(block, 0, 15));
            assertEquals(2, f.scheduler.immediate.size());
            trigger.onDisable();
            assertTrue(f.scheduler.jobs.getLast().cancelled);
        }
    }

    @Test
    void interactionTriggersIgnoreOffhandSpectatorsDeniedActionsAndDifferentBlocks()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            source(f, state);
            var trigger = bind(new InteractTrigger(), f);
            var block = mock(Block.class);
            when(block.getLocation()).thenReturn(trigger.getLocation());
            var event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(state.player);
            when(event.getClickedBlock()).thenReturn(block);
            when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
            when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
            trigger.onInteract(event);
            assertTrue(f.scheduler.immediate.isEmpty());
            when(event.getHand()).thenReturn(EquipmentSlot.HAND);
            state.gameMode = GameMode.SPECTATOR;
            trigger.onInteract(event);
            assertTrue(f.scheduler.immediate.isEmpty());
            state.gameMode = GameMode.SURVIVAL;
            when(event.useInteractedBlock()).thenReturn(Event.Result.DENY);
            trigger.onInteract(event);
            assertTrue(f.scheduler.immediate.isEmpty());
            when(event.useInteractedBlock()).thenReturn(Event.Result.DEFAULT);
            when(block.getLocation()).thenReturn(new Location(f.world, 1, 70, 0));
            trigger.onInteract(event);
            assertTrue(f.scheduler.immediate.isEmpty());
            when(block.getLocation()).thenReturn(trigger.getLocation());
            trigger.onInteract(event);
            assertEquals(1, f.scheduler.immediate.size());
            verify(event).setCancelled(true);
        }
    }

    @Test
    void deathTriggersIgnoreCancelledDeathsAndCountDistinctPlayersBeforeResetting()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var first = new PlayerState();
            source(f, first);
            var second = new PlayerState();
            source(f, second);
            var trigger =
                    bind(
                            new PlayerDeathTrigger(
                                    Map.of("deathsRequired", 2, "oneDeathPerPlayer", true)),
                            f);
            var event = mock(PlayerDeathEvent.class);
            when(event.getEntity()).thenReturn(first.player);
            when(event.isCancelled()).thenReturn(true);
            trigger.onPlayerDeath(event);
            when(event.isCancelled()).thenReturn(false);
            when(event.getEntity()).thenReturn(second.player);
            trigger.onPlayerDeath(event);
            assertTrue(f.scheduler.immediate.isEmpty());
            trigger.onPlayerDeath(event);
            assertTrue(f.scheduler.immediate.isEmpty());
            when(event.getEntity()).thenReturn(first.player);
            trigger.onPlayerDeath(event);
            assertEquals(1, f.scheduler.immediate.size());
            trigger.onPlayerDeath(event);
            assertEquals(1, f.scheduler.immediate.size());
            when(event.getEntity()).thenReturn(second.player);
            trigger.onPlayerDeath(event);
            assertEquals(2, f.scheduler.immediate.size());
        }
    }

    @Test
    void chatTriggersApplyTextRulesAndRequireThePlayersCurrentRunBeforeCancellingChat()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            var session = source(f, state);
            for (boolean exact : List.of(true, false))
                for (boolean sensitive : List.of(true, false)) {
                    var trigger =
                            bind(
                                    new ChatTrigger(
                                            Map.of(
                                                    "text",
                                                    "Open",
                                                    "exact",
                                                    exact,
                                                    "caseSensitive",
                                                    sensitive)),
                                    f);
                    for (String text : List.of("Open", "open", "Please Open", "unrelated")) {
                        var event = mock(AsyncChatEvent.class);
                        when(event.originalMessage()).thenReturn(Component.text(text));
                        when(event.getPlayer()).thenReturn(state.player);
                        int before = f.scheduler.immediate.size();
                        trigger.onChat(event);
                        String actual = sensitive ? text : text.toLowerCase(Locale.ROOT);
                        String expected = sensitive ? "Open" : "open";
                        boolean matches =
                                exact ? actual.equals(expected) : actual.contains(expected);
                        assertEquals(before + (matches ? 1 : 0), f.scheduler.immediate.size());
                        verify(event, matches ? times(1) : never()).setCancelled(true);
                    }
                }
            session.setInstance(null);
            var trigger = bind(new ChatTrigger(Map.of("text", "Open")), f);
            var event = mock(AsyncChatEvent.class);
            when(event.originalMessage()).thenReturn(Component.text("Open"));
            when(event.getPlayer()).thenReturn(state.player);
            int before = f.scheduler.immediate.size();
            trigger.onChat(event);
            assertEquals(before, f.scheduler.immediate.size());
            verify(event, never()).setCancelled(true);
        }
    }

    @Test
    void asynchronousChatUsesTheMainThreadBridgeAndBridgeFailureLeavesChatUnconsumed()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            source(f, state);
            var trigger = bind(new ChatTrigger(Map.of("text", "Open")), f);
            var event = mock(AsyncChatEvent.class);
            when(event.originalMessage()).thenReturn(Component.text("Open"));
            when(event.getPlayer()).thenReturn(state.player);
            when(event.isAsynchronous()).thenReturn(true);
            when(f.scheduler.scheduler.callSyncMethod(any(), any()))
                    .thenAnswer(
                            c ->
                                    CompletableFuture.completedFuture(
                                            ((Callable<?>) c.getArgument(1)).call()));
            trigger.onChat(event);
            verify(event).setCancelled(true);
            assertEquals(1, f.scheduler.immediate.size());
            clearInvocations(event);
            doReturn(CompletableFuture.failedFuture(new IllegalStateException("bridge failed")))
                    .when(f.scheduler.scheduler)
                    .callSyncMethod(any(), any());
            trigger.onChat(event);
            verify(event, never()).setCancelled(true);
            assertEquals(1, f.scheduler.immediate.size());
        }
    }

    @Test
    void keyItemTriggerConsumesOnlyMatchingMainHandItemsAtTheConfiguredBlock() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus();
                var items = mockStatic(ItemUtils.class)) {
            var state = new PlayerState();
            source(f, state);
            var template = mock(ItemStack.class);
            items.when(ItemUtils::getDefaultKeyItem).thenReturn(template);
            when(template.clone()).thenReturn(template);
            var held = PlayerState.stack(3);
            when(template.isSimilar(held)).thenReturn(true);
            var trigger =
                    bind(new KeyItemTrigger(Map.of("item", template, "consumeItem", true)), f);
            trigger.initialize();
            trigger.setAllowRetrigger(true);
            var block = mock(Block.class);
            when(block.getLocation()).thenReturn(trigger.getLocation());
            var event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(state.player);
            when(event.getClickedBlock()).thenReturn(block);
            when(event.getItem()).thenReturn(held);
            when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
            when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
            trigger.onUseItem(event);
            assertEquals(3, held.getAmount());
            when(event.getHand()).thenReturn(EquipmentSlot.HAND);
            when(template.isSimilar(held)).thenReturn(false);
            trigger.onUseItem(event);
            assertEquals(3, held.getAmount());
            when(template.isSimilar(held)).thenReturn(true);
            trigger.onUseItem(event);
            assertEquals(2, held.getAmount());
            assertEquals(1, f.scheduler.immediate.size());
            verify(event).setCancelled(true);
        }
    }
    @Test
    void clonedDeathCountersDoNotShareProgressWithEachOtherOrTheirTemplate() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration());var menus=new HeadlessMenus()) {
            var first=new PlayerState(); source(f,first); var second=new PlayerState(); source(f,second);
            var template=bind(new PlayerDeathTrigger(Map.of("deathsRequired",2,"oneDeathPerPlayer",true)),f);
            var left=(PlayerDeathTrigger)template.clone(); var right=(PlayerDeathTrigger)template.clone();
            assertNotNull(left); assertNotNull(right);
            var event=mock(PlayerDeathEvent.class); when(event.getEntity()).thenReturn(first.player); left.onPlayerDeath(event);
            when(event.getEntity()).thenReturn(second.player); right.onPlayerDeath(event);
            assertTrue(f.scheduler.immediate.isEmpty());
            left.onPlayerDeath(event); assertEquals(1,f.scheduler.immediate.size());
            when(event.getEntity()).thenReturn(first.player); right.onPlayerDeath(event);
            assertEquals(2,f.scheduler.immediate.size());
            template.onPlayerDeath(event); assertEquals(2,f.scheduler.immediate.size());
        }
    }

    @Test
    void disablingAClonedTriggerCannotCancelItsSourcesConditionWaitTask() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration());var menus=new HeadlessMenus()) {
            var trigger=bind(new RemoteTrigger(Map.of("waitForConditions",true)),f); trigger.setAllowRetrigger(false);
            var condition=new nl.hauntedmc.dungeons.content.condition.ChanceCondition(Map.of("chance",0.0));
            condition.initializeFields(); condition.setInstance(f.run); trigger.getConditions().add(condition);
            trigger.trigger(); var waiting=f.scheduler.jobs.getFirst();
            var clone=trigger.clone(); assertNotNull(clone); assertFalse(clone.isTriggered()); clone.disable();
            assertFalse(waiting.cancelled);
            trigger.getConditions().clear(); waiting.run();
            assertTrue(waiting.cancelled);
            verify(Bukkit.getPluginManager()).callEvent(any(TriggerFireEvent.class));
        }
    }

    @Test
    void proximityDetectionRequiresCurrentMembersAndRetriggersOnlyAfterTheyLeaveAndReturn() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var first=new PlayerState(); source(f,first); var outsider=new PlayerState(); outsider.location=new Location(f.world,0,70,0);
            java.util.List<org.bukkit.entity.Player> nearby=new java.util.ArrayList<>(List.of(first.player,outsider.player));
            f.paper.bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(c -> List.copyOf(nearby));
            f.paper.bukkit.when(() -> Bukkit.getPlayer(first.player.getUniqueId())).thenReturn(first.player);
            when(f.world.getNearbyEntities(any(Location.class),anyDouble(),anyDouble(),anyDouble(),any())).thenAnswer(c -> List.copyOf(nearby));
            var trigger=bind(new DistanceTrigger(Map.of("radius",3.0,"count",2)),f); trigger.onEnable(); var scan=f.scheduler.jobs.getFirst();
            scan.run(); assertTrue(f.scheduler.immediate.isEmpty());
            var second=new PlayerState(); source(f,second); f.paper.bukkit.when(() -> Bukkit.getPlayer(second.player.getUniqueId())).thenReturn(second.player);
            nearby.clear(); nearby.addAll(List.of(first.player,second.player));
            scan.run(); assertEquals(1,f.scheduler.immediate.size()); scan.run(); assertEquals(1,f.scheduler.immediate.size());
            nearby.clear(); scan.run(); nearby.addAll(List.of(first.player,second.player)); scan.run();
            assertEquals(2,f.scheduler.immediate.size()); trigger.onDisable(); assertTrue(scan.cancelled);
        }
    }

    @Test
    void mobDeathTriggersIgnoreCancelledWrongTypeAndOutOfRangeDeathsBeforeCountingAndResetting() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var mob=mock(org.bukkit.entity.LivingEntity.class); when(mob.getWorld()).thenReturn(f.world);
            when(mob.getType()).thenReturn(org.bukkit.entity.EntityType.ZOMBIE); when(mob.getLocation()).thenReturn(new Location(f.world,0,70,0));
            var event=mock(EntityDeathEvent.class); when(event.getEntity()).thenReturn(mob);
            var trigger=bind(new MobDeathTrigger(Map.of("mob","zombie","count",2,"radius",3.0)),f);
            when(event.isCancelled()).thenReturn(true); trigger.onMobDeath(event);
            when(event.isCancelled()).thenReturn(false); trigger.onMobDeath(event); assertTrue(f.scheduler.immediate.isEmpty());
            when(mob.getType()).thenReturn(org.bukkit.entity.EntityType.SKELETON); trigger.onMobDeath(event); assertTrue(f.scheduler.immediate.isEmpty());
            when(mob.getType()).thenReturn(org.bukkit.entity.EntityType.ZOMBIE); when(mob.getLocation()).thenReturn(new Location(f.world,4,70,0));
            trigger.onMobDeath(event); assertTrue(f.scheduler.immediate.isEmpty());
            when(mob.getLocation()).thenReturn(new Location(f.world,3,70,0)); trigger.onMobDeath(event); assertEquals(1,f.scheduler.immediate.size());
            trigger.onMobDeath(event); assertEquals(1,f.scheduler.immediate.size()); trigger.onMobDeath(event); assertEquals(2,f.scheduler.immediate.size());
        }
    }

}
