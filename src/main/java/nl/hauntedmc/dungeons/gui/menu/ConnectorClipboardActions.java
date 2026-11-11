package nl.hauntedmc.dungeons.gui.menu;

import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.ConnectorDoor;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Applies connector clipboard edits after a menu resolves the target room and location. */
final class ConnectorClipboardActions {
    private ConnectorClipboardActions() {}

    static void paste(
            Player player,
            DungeonPlayerSession session,
            BranchingRoomDefinition room,
            SimpleLocation target) {
        boolean copying = session.isConnectorCopying();
        if (!copying && !session.isConnectorCutting()) return;
        if (room.getConnector(target) != null) {
            LangUtils.sendMessage(player, "editor.layout.connector.already-here");
            return;
        }
        Connector source = session.getCopiedConnector();
        if (source == null) {
            session.clearConnectorClipboard();
            LangUtils.sendMessage(player, "editor.layout.connector.no-selection");
            return;
        }
        if (!room.applyDirectionAtEdge(target)) {
            LangUtils.sendMessage(player, "editor.layout.connector.must-be-on-edge");
            return;
        }
        if (copying) copy(player, session, room, source, target);
        else move(player, session, source, target);
    }

    private static void copy(
            Player player,
            DungeonPlayerSession session,
            BranchingRoomDefinition room,
            Connector source,
            SimpleLocation target) {
        Connector copy = source.copy(target);
        if (copy == null) {
            LangUtils.sendMessage(player, "editor.layout.connector.no-selection");
            return;
        }
        room.addConnector(copy);
        copy.setDoor(transformDoor(copy.getDoor(), source.getLocation(), target));
        session.setActiveConnector(copy);
        LangUtils.sendMessage(player, "editor.layout.connector.pasted");
    }

    private static void move(
            Player player, DungeonPlayerSession session, Connector source, SimpleLocation target) {
        SimpleLocation previous = source.getLocation();
        source.setLocation(target);
        source.setDoor(
                source.getDoor() == null
                        ? new ConnectorDoor(source)
                        : transformDoor(source.getDoor(), previous, target));
        session.setActiveConnector(source);
        LangUtils.sendMessage(player, "editor.layout.connector.cut-pasted");
        session.clearConnectorClipboard();
    }

    private static ConnectorDoor transformDoor(
            ConnectorDoor door, SimpleLocation originLocation, SimpleLocation target) {
        int rotation =
                target.getDirection().getDegrees() - originLocation.getDirection().getDegrees();
        Vector origin = originLocation.asVector();
        Vector offset = target.asVector().subtract(origin);
        return door.copy(offset, origin, rotation);
    }
}
