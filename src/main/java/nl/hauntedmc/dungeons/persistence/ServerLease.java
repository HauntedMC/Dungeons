package nl.hauntedmc.dungeons.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Database-time ownership; every transaction validates ownership before touching gameplay state.
 */
public final class ServerLease {
    static final class Lost extends SQLException {
        private static final long serialVersionUID = 1L;

        Lost(String message) {
            super(message);
        }
    }

    private final String serverId;
    private final UUID bootId;

    public ServerLease(String serverId, UUID bootId) {
        this.serverId = serverId;
        this.bootId = bootId;
    }

    public void acquire(Connection connection) throws SQLException {
        DungeonSql.update(
                connection,
                "INSERT IGNORE INTO dungeon_server(server_id,boot_id,epoch,lease_until)"
                        + " VALUES(?,?,0,UTC_TIMESTAMP(3))",
                serverId,
                bootId);
        int acquired =
                DungeonSql.update(
                        connection,
                        "UPDATE dungeon_server SET boot_id=?,epoch=epoch+1,"
                                + "lease_until=TIMESTAMPADD(SECOND,20,UTC_TIMESTAMP(3)) WHERE server_id=?"
                                + " AND (lease_until<=UTC_TIMESTAMP(3) OR boot_id=?)",
                        bootId,
                        serverId,
                        bootId);
        if (acquired != 1)
            throw new SQLException("Another Dungeons process owns server " + serverId);
    }

    public void check(Connection connection) throws SQLException {
        var owner =
                DungeonSql.rows(
                        connection,
                        "SELECT boot_id FROM dungeon_server WHERE server_id=?"
                                + " AND boot_id=? AND lease_until>UTC_TIMESTAMP(3) FOR SHARE",
                        serverId,
                        bootId);
        if (owner.isEmpty()) throw new Lost("Server lease was lost for " + serverId);
    }

    public void renew(Connection connection) throws SQLException {
        if (DungeonSql.update(
                        connection,
                        "UPDATE dungeon_server SET lease_until=TIMESTAMPADD(SECOND,20,UTC_TIMESTAMP(3))"
                                + " WHERE server_id=? AND boot_id=? AND lease_until>UTC_TIMESTAMP(3)",
                        serverId,
                        bootId)
                != 1) {
            throw new Lost("Server lease renewal failed for " + serverId);
        }
    }

    public void release(Connection connection) throws SQLException {
        DungeonSql.update(
                connection,
                "UPDATE dungeon_server SET lease_until=UTC_TIMESTAMP(3)"
                        + " WHERE server_id=? AND boot_id=?",
                serverId,
                bootId);
    }
}
