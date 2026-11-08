package nl.hauntedmc.dungeons.gui.staff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.command.StaffCommandHandler;
import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.*;

class StaffMenusContractTest {
    @TempDir Path directory;

    @Test
    void everyScreenRendersWithPaginationAndStableInstanceAndOperationTargets() throws Exception {
        try (var f = new Fixture(directory)) {
            f.base.runtime.activeInstances().registerActiveInstance(f.base.run.run);
            when(f.commands.describe(f.base.run.run)).thenReturn("Live instance");
            var op =
                    f.base.ops.drain(
                            f.base.actor,
                            "reload",
                            "reload",
                            "other",
                            "Review",
                            () -> CompletableFuture.completedFuture("done"));
            when(f.base.runtime.lootTableRepository().getTables())
                    .thenReturn(List.of(new LootTable("treasure")));
            for (var screen :
                    Map.of(
                                    "overview",
                                    "",
                                    "dungeons",
                                    "",
                                    "dungeon",
                                    "crypt",
                                    "settings",
                                    "crypt",
                                    "instances",
                                    "all",
                                    "instance",
                                    f.base.run.run.getInstanceId().toString(),
                                    "player",
                                    f.player.player.getUniqueId().toString(),
                                    "queues",
                                    "",
                                    "operations",
                                    "",
                                    "operation",
                                    op.shortId())
                            .entrySet()) {
                f.menus.open(f.player.player, screen.getKey(), screen.getValue(), "", 0);
                assertNotSame(f.gui.empty, f.gui.top, screen.getKey());
                assertEquals("Close", f.gui.label(53));
            }
            f.menus.open(f.player.player, "loot", "", "", 0);
            assertEquals("treasure", f.gui.label(0));
            f.menus.open(f.player.player, "settings", "crypt", "", 0);
            assertEquals("Next", f.gui.label(50));
            f.click(50, ClickType.LEFT);
            f.base.flush();
            assertEquals("Previous", f.gui.label(48));
            f.menus.open(f.player.player, "dungeons", "", "no-match", 0);
            assertEquals("", f.gui.label(0));
        }
    }

    @Test
    void refreshDoesNotDiscardAQueuedClickAndOnlyManagedLeftClicksExecute() throws Exception {
        try (var f = new Fixture(directory)) {
            f.menus.open(f.player.player, "overview", "", "", 0);
            var original = f.gui.top;
            for (ClickType type :
                    List.of(
                            ClickType.SHIFT_LEFT,
                            ClickType.NUMBER_KEY,
                            ClickType.DOUBLE_CLICK,
                            ClickType.DROP)) assertTrue(f.click(2, type).isCancelled());
            assertTrue(f.click(-999, ClickType.LEFT).isCancelled());
            f.click(2, ClickType.LEFT);
            f.base.tick(); // A refresh changes the view model before the scheduled click runs.
            f.base.flush();
            assertSame(f.gui.empty, f.gui.top); // Player-search prompt opened.
            f.chat("Target");
            f.base.flush();
            verify(f.commands)
                    .executeChecked(
                            eq(f.player.player), aryEq(new String[] {"admin", "player", "Target"}));
            var drag = TestEvents.cancellable(InventoryDragEvent.class);
            when(drag.getWhoClicked()).thenReturn(f.player.player);
            var view = mock(InventoryView.class);
            when(view.getTopInventory()).thenReturn(original);
            when(drag.getView()).thenReturn(view);
            f.menus.drag(drag);
            assertFalse(drag.isCancelled()); // Prompt closed the old managed inventory.
        }
    }

    @Test
    void staleViewsRevokedPermissionsAndShiftDragCannotMutateOrStealMenuItems() throws Exception {
        try (var f = new Fixture(directory)) {
            f.menus.open(f.player.player, "overview", "", "", 0);
            var drag = TestEvents.cancellable(InventoryDragEvent.class);
            when(drag.getWhoClicked()).thenReturn(f.player.player);
            var currentView = f.player.player.getOpenInventory();
            when(drag.getView()).thenReturn(currentView);
            f.menus.drag(drag);
            assertTrue(drag.isCancelled());
            f.click(2, ClickType.LEFT);
            when(f.player.player.hasPermission("dungeons.staff.inspect")).thenReturn(false);
            f.base.flush();
            f.chat("Target");
            f.base.flush();
            verify(f.commands, never()).executeChecked(any(), any());
            when(f.player.player.hasPermission("dungeons.staff.inspect")).thenReturn(true);
            f.menus.open(f.player.player, "overview", "", "", 0);
            f.click(2, ClickType.LEFT);
            f.player.player.closeInventory();
            f.base.flush();
            f.chat("Target");
            f.base.flush();
            verify(f.commands, never()).executeChecked(any(), any());
        }
    }

    @Test
    void promptValidationCancelExpiryQuitAndShutdownReleaseAllCallbacks() throws Exception {
        try (var f = new Fixture(directory)) {
            var values = new ArrayList<String>();
            var cancelled = new AtomicInteger();
            f.menus.prompt(
                    f.player.player,
                    "Value",
                    value -> {
                        if (value.equals("bad"))
                            throw new IllegalArgumentException("Invalid value");
                        values.add(value);
                    },
                    cancelled::incrementAndGet);
            f.chat("bad");
            f.base.flush();
            f.chat("good");
            f.base.flush();
            assertEquals(List.of("good"), values);
            f.menus.prompt(f.player.player, "Value", values::add, cancelled::incrementAndGet);
            f.chat("cancel");
            f.base.flush();
            assertEquals(1, cancelled.get());
            f.menus.prompt(f.player.player, "Value", values::add, cancelled::incrementAndGet);
            f.clock.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(61));
            f.base.tick();
            assertEquals(2, cancelled.get());
            assertFalse(f.chat("ordinary chat").isCancelled());
            f.menus.prompt(f.player.player, "Value", values::add, cancelled::incrementAndGet);
            var quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(f.player.player);
            f.menus.quit(quit);
            assertFalse(f.chat("ordinary chat").isCancelled());
            f.menus.prompt(f.player.player, "Value", values::add, cancelled::incrementAndGet);
            f.menus.close();
            assertFalse(f.chat("ordinary chat").isCancelled());
            assertEquals(List.of("good"), values);
        }
    }

    @Test
    void confirmationClicksDispatchOnceAndConnectionReplacementCannotConsumeOldPrompts()
            throws Exception {
        try (var f = new Fixture(directory)) {
            f.menus.confirm(f.player.player, "/dungeon admin archive crypt reason --confirm token");
            f.click(11, ClickType.LEFT);
            f.click(11, ClickType.LEFT);
            f.base.flush();
            verify(f.commands, times(1)).handleCommand(any(), any());
            f.menus.prompt(
                    f.player.player, "Value", value -> fail("Stale prompt executed"), () -> {});
            var replacement = new PlayerState();
            UUID sameId = f.player.player.getUniqueId();
            when(replacement.player.getUniqueId()).thenReturn(sameId);
            var event = TestEvents.cancellable(AsyncChatEvent.class);
            when(event.getPlayer()).thenReturn(replacement.player);
            when(event.originalMessage()).thenReturn(Component.text("new connection"));
            f.menus.chat(event);
            assertFalse(event.isCancelled());
        }
    }

    @Test
    void supportAndMaintenanceWizardsDispatchTheValuesEnteredByTheStaffMember() throws Exception {
        try (var f = new Fixture(directory)) {
            String id = f.player.player.getUniqueId().toString();
            f.menus.open(f.player.player, "player", id, "", 0);
            f.click(1, ClickType.LEFT);
            f.base.flush();
            f.chat("Rescue reason");
            f.base.flush();
            verify(f.commands)
                    .executeChecked(
                            eq(f.player.player),
                            aryEq(new String[] {"admin", "player", id, "rescue", "Rescue reason"}));
            for (var wizard :
                    List.of(
                            new String[] {"6", "3", "Life correction"},
                            new String[] {"7", "crypt", "clear", "Cooldown reason"},
                            new String[] {"8", "crypt", "clear", "Loot reason"},
                            new String[] {"9", "crypt", "true", "Completion reason"},
                            new String[] {"10", "crypt", "7d", "Restriction reason"},
                            new String[] {"11", "DIAMOND", "3", "Compensation reason"})) {
                f.menus.open(f.player.player, "player", id, "", 0);
                f.click(Integer.parseInt(wizard[0]), ClickType.LEFT);
                f.base.flush();
                for (int i = 1; i < wizard.length; i++) {
                    f.chat(wizard[i]);
                    f.base.flush();
                }
            }
            verify(f.commands)
                    .executeChecked(
                            eq(f.player.player),
                            aryEq(
                                    new String[] {
                                        "admin",
                                        "compensate",
                                        id,
                                        "DIAMOND",
                                        "3",
                                        "Compensation reason"
                                    }));
            for (int slot : List.of(2, 3, 4, 7, 9)) {
                f.menus.open(f.player.player, "dungeon", "crypt", "", 0);
                f.click(slot, ClickType.LEFT);
                f.base.flush();
                f.chat("Maintenance reason");
                f.base.flush();
            }
            verify(f.commands)
                    .executeChecked(
                            eq(f.player.player),
                            aryEq(
                                    new String[] {
                                        "admin", "archive", "crypt", "Maintenance reason"
                                    }));
            f.menus.open(f.player.player, "dungeon", "crypt", "", 0);
            f.click(8, ClickType.LEFT);
            f.base.flush();
            f.chat("backup-id");
            f.base.flush();
            f.chat("Restore reason");
            f.base.flush();
            verify(f.commands)
                    .executeChecked(
                            eq(f.player.player),
                            aryEq(
                                    new String[] {
                                        "admin", "restore", "crypt", "backup-id", "Restore reason"
                                    }));
            f.menus.open(f.player.player, "dungeon", "crypt", "", 0);
            f.click(5, ClickType.LEFT);
            f.base.flush();
            verify(f.player.player).performCommand("dungeon edit open crypt");
        }
    }

    @Test
    void instanceActionsSearchAndScopedOperationNavigationUseStableTargets() throws Exception {
        try (var f = new Fixture(directory)) {
            f.base.runtime.activeInstances().registerActiveInstance(f.base.run.run);
            when(f.commands.describe(f.base.run.run)).thenReturn("Live instance");
            String id = f.base.run.run.getInstanceId().toString();
            for (int slot : List.of(1, 2, 3, 4)) {
                f.menus.open(f.player.player, "instance", id, "", 0);
                f.click(slot, ClickType.LEFT);
                f.base.flush();
                if (slot == 4) {
                    f.chat("60");
                    f.base.flush();
                }
                if (slot != 1) {
                    f.chat("Support reason");
                    f.base.flush();
                }
            }
            verify(f.commands)
                    .executeChecked(
                            eq(f.player.player),
                            aryEq(
                                    new String[] {
                                        "admin", "instance", id, "extend", "60", "Support reason"
                                    }));
            f.menus.open(f.player.player, "overview", "", "", 0);
            f.click(47, ClickType.LEFT);
            f.base.flush();
            f.chat("dungeons");
            f.base.flush();
            assertEquals("Dungeons", f.gui.label(0));
            f.menus.open(f.player.player, "settings", "crypt", "", 0);
            f.click(0, ClickType.LEFT);
            f.base.flush();
            f.chat("false");
            f.base.flush();
            verify(f.player.player).performCommand(startsWith("dungeon config crypt "));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final StaffFixture base;
        final PlayerState player;
        final StaffCommandHandler commands = mock(StaffCommandHandler.class);
        final StaffGuiBoundary gui;
        final AtomicLong clock = new AtomicLong();
        final StaffMenus menus;

        Fixture(Path directory) throws Exception {
            base = new StaffFixture(directory);
            player = base.player("Staff");
            gui = new StaffGuiBoundary(base, player);
            when(commands.sortedDungeons()).thenAnswer(c -> List.copyOf(base.catalog.values()));
            when(commands.dungeon("crypt")).thenReturn(base.run.dungeon);
            when(commands.describe(base.run.dungeon)).thenReturn("Dungeon crypt");
            when(commands.validation(any())).thenReturn(List.of("Ready"));
            when(commands.playerLines(any(), any())).thenReturn(List.of("Player details"));
            when(commands.queueLines()).thenReturn(List.of("No queue"));
            menus = new StaffMenus(base.runtime, commands, clock::get);
            gui.closing =
                    inventory -> {
                        var event = mock(InventoryCloseEvent.class);
                        when(event.getPlayer()).thenReturn(player.player);
                        when(event.getInventory()).thenReturn(inventory);
                        menus.close(event);
                    };
        }

        InventoryClickEvent click(int slot, ClickType type) {
            var event = TestEvents.cancellable(InventoryClickEvent.class);
            var view = mock(InventoryView.class);
            when(view.getTopInventory()).thenReturn(gui.top);
            when(event.getWhoClicked()).thenReturn(player.player);
            when(event.getView()).thenReturn(view);
            when(event.getRawSlot()).thenReturn(slot);
            when(event.getClick()).thenReturn(type);
            menus.click(event);
            return event;
        }

        AsyncChatEvent chat(String text) {
            var event = TestEvents.cancellable(AsyncChatEvent.class);
            when(event.getPlayer()).thenReturn(player.player);
            when(event.originalMessage()).thenReturn(Component.text(text));
            menus.chat(event);
            return event;
        }

        @Override
        public void close() {
            menus.close();
            base.close();
            gui.close();
        }
    }
}
