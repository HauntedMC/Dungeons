package nl.hauntedmc.dungeons.runtime.player;

import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Offline support uses the same validated journal and never rewrites native player files. */
public final class StaffPlayerRecords {
    private StaffPlayerRecords() {}

    private static PlayerRecoveryJournal journal(DungeonsRuntime runtime, UUID id) {
        return new PlayerRecoveryJournal(id);
    }

    public static String inspect(DungeonsRuntime runtime, UUID id) {
        var session = runtime.playerSessions().get(id);
        if (session != null) return session.recoveryStatus();
        var journal = journal(runtime, id);
        return "recovery="
                + journal.hasRecovery()
                + ", pending transaction="
                + journal.hasPendingTransaction()
                + ", key escrow="
                + journal.hasKeyReservation()
                + ", claimable stacks="
                + journal.rewards().size();
    }

    public static boolean compensate(
            DungeonsRuntime runtime, UUID id, UUID operation, ItemStack item) {
        var session = runtime.playerSessions().get(id);
        return session != null
                ? session.addStaffRewards(operation, item)
                : journal(runtime, id).addStaffRewards(operation, item);
    }
}
