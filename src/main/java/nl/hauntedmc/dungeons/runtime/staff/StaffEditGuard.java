package nl.hauntedmc.dungeons.runtime.staff;

import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** All editor entry points recheck current ownership, scope, permission and drain state. */
public final class StaffEditGuard {
    private StaffEditGuard() {}

    public static void configuration(CommandSender actor, DungeonDefinition dungeon, String path) {
        if (dungeon.getRuntime() == null || dungeon.getRuntime().staffOperations() == null) return;
        StaffPermissions.require(actor, "edit", dungeon.getWorldName());
        if (path.equals("players.join_commands"))
            StaffPermissions.require(actor, "scripts", dungeon.getWorldName());
        dungeon.getRuntime().staffOperations().requireContentWritable(dungeon.getWorldName());
        if (path.equals("dungeon.display_name")) return;
        if (!dungeon.getRuntime().staffOperations().maintenance(dungeon.getWorldName()))
            throw new IllegalArgumentException(
                    "Enable maintenance before changing gameplay settings");
        if (dungeon.getRuntime().staffOperations().instances(dungeon.getWorldName()).stream()
                .anyMatch(i -> !i.isEditInstance()))
            throw new IllegalArgumentException("Wait for play instances to drain and unload first");
        if (dungeon.isSaving())
            throw new IllegalArgumentException("Wait for the editor save to finish");
    }

    public static boolean editor(Player player, DungeonPlayerSession session) {
        if (session == null || !session.isEditMode() || session.getInstance() == null) return false;
        DungeonInstance instance = session.getInstance();
        if (instance.getRuntime().staffOperations() == null) return true;
        try {
            if (instance.getDungeon().getEditSession() != instance)
                throw new IllegalArgumentException("Editor ownership has changed; reopen it");
            configuration(player, instance.getDungeon(), "editor");
            if (ContentReferences.containsScript(session.getActiveFunction()))
                StaffPermissions.require(player, "scripts", instance.getDungeon().getWorldName());
            return true;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            player.sendMessage("[Dungeons] " + failure.getMessage());
            return false;
        }
    }
}
