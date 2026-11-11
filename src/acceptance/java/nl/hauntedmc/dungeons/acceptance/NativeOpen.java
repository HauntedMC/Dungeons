package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import nl.hauntedmc.dungeons.content.dungeon.OpenDungeon;

final class NativeOpen {
    private NativeOpen() {}

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        var dungeon = (OpenDungeon) WorldFixtures.definition(test, "acceptance_open", "open");
        dungeon.getConfig().set("open.preload_world", true);
        dungeon.preLoad();
        dungeon.preLoad();
        require(
                dungeon.getInstances().size() == 1,
                "Concurrent warm requests created duplicate worlds");
        return test.await(
                        "open warm world publication",
                        () -> !dungeon.getActiveInstances().isEmpty())
                .thenCompose(
                        ignored -> {
                            var first = dungeon.getActiveInstances().getFirst();
                            require(
                                    first.isReady() && first.getInstanceWorld() != null,
                                    "Open pool published an uninitialized world");
                            var firstOwner = UUID.randomUUID();
                            var secondOwner = UUID.randomUUID();
                            require(
                                    first.reserveTeamSlots(firstOwner, 1),
                                    "First team could not reserve a slot");
                            require(
                                    first.reserveTeamSlots(secondOwner, 1),
                                    "Second team could not reserve a slot");
                            require(
                                    !first.reserveTeamSlots(UUID.randomUUID(), 1),
                                    "Open pool overbooked its real world");
                            first.releaseReservedSlots(firstOwner);
                            require(
                                    first.hasCapacityFor(1) && first.hasOutstandingReservations(),
                                    "Releasing one reservation disturbed another owner");
                            first.releaseReservedSlots(secondOwner);
                            return test.await(
                                            "idle unload and warm-pool replacement",
                                            () ->
                                                    first.getInstanceWorld() == null
                                                            && dungeon.getActiveInstances().stream()
                                                                    .anyMatch(
                                                                            run ->
                                                                                    run != first
                                                                                            && run
                                                                                                    .isReady()))
                                    .thenCompose(
                                            replaced -> {
                                                require(
                                                        dungeon.getActiveInstances().size() == 1,
                                                        "Warm pool has duplicate replacement instances");
                                                var replacement =
                                                        dungeon.getActiveInstances().getFirst();
                                                require(
                                                        replacement != first
                                                                && replacement.hasCapacityFor(2),
                                                        "Replacement inherited the old run or reservations");
                                                dungeon.getConfig()
                                                        .set("open.preload_world", false);
                                                dungeon.refreshRuntimeSettings();
                                                return WorldFixtures.dispose(test, replacement);
                                            });
                        });
    }
}
