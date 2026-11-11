package nl.hauntedmc.dungeons.runtime.player;

import java.util.UUID;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import org.bukkit.inventory.ItemStack;

/** Offline support uses the same validated journal and never rewrites native player files. */
public final class StaffPlayerRecords {
    private StaffPlayerRecords() {}

    private static PlayerRecoveryJournal journal(DungeonsRuntime runtime, UUID id) {
        return new PlayerRecoveryJournal(runtime.stateStore(), id);
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
                + journal.hasKeyReservation();
    }

    public static boolean deliverKey(
            DungeonsRuntime runtime, UUID id, UUID operation, ItemStack item) {
        var session = runtime.playerSessions().get(id);
        if (session == null) return journal(runtime, id).addStaffRefunds(operation, item);
        boolean added = session.addStaffRefunds(operation, item);
        session.flushPendingAccessKeyRefund();
        return added;
    }
}
