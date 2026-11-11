package nl.hauntedmc.dungeons.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * ORM schema for mandatory database persistence. JDBC repositories share these exact table/column
 * names.
 */
public final class DungeonEntities {
    private DungeonEntities() {}

    public static Class<?>[] types() {
        return new Class<?>[] {
            RepoEntities.Entry.class,
            RepoEntities.Release.class,
            RepoEntities.File.class,
            RepoEntities.Chunk.class,
            RepoEntities.FileChunk.class,
            StateDocumentRow.class,
            PlayerReference.class,
            Server.class,
            Definition.class,
            Deployment.class,
            Team.class,
            TeamMember.class,
            Admission.class,
            Run.class,
            Participation.class,
            Event.class,
            PlayerProgress.class,
            Cooldown.class,
            PlayerGuard.class,
            KeyDefinition.class,
            KeyInstance.class,
            StaffOperation.class,
            Maintenance.class,
            Restriction.class,
            PlayerStats.class,
            Stats.class,
            MetricDefinition.class,
            ParticipationMetric.class,
            PlayerMetric.class,
            KeyReservation.class
        };
    }

    /**
     * Read-only reference to the existing DataRegistry identity; never creates player identities.
     */
    @Entity(name = "DungeonPlayerReference")
    @Table(name = "player_entity")
    @Immutable
    public static class PlayerReference {
        @Id
        @Column(name = "id", nullable = false, columnDefinition = "BIGINT")
        protected Long id;

        public PlayerReference() {}
    }

    @Entity(name = "DungeonServer")
    @Table(name = "dungeon_server")
    public static class Server {
        public Server() {}

        @Id
        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Column(name = "boot_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String boot_id;

        @Column(name = "epoch", nullable = false, columnDefinition = "BIGINT")
        protected Long epoch;

        @Column(name = "lease_until", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime lease_until;

        @Column(name = "storage_version", nullable = false, columnDefinition = "INT")
        @ColumnDefault("0")
        protected Integer storage_version;
    }

    @Entity(name = "DungeonDefinition")
    @Table(name = "dungeon_definition")
    public static class Definition {
        public Definition() {}

        @Id
        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
        @ColumnDefault("CURRENT_TIMESTAMP(3)")
        protected LocalDateTime created_at;
    }

    @Entity(name = "DungeonDeployment")
    @Table(
            name = "dungeon_deployment",
            uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_deployment_name",
                        columnNames = {"server_id", "local_name"})
            })
    @IdClass(Deployment.Key.class)
    public static class Deployment {
        public Deployment() {}

        @Id
        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Id
        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Column(name = "local_name", nullable = false, columnDefinition = "VARCHAR(128)")
        protected String local_name;

        @Column(name = "fingerprint", nullable = false, columnDefinition = "CHAR(64)")
        protected String fingerprint;

        @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)")
        @ColumnDefault("CURRENT_TIMESTAMP(3)")
        protected LocalDateTime updated_at;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "server_id",
                            referencedColumnName = "server_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_deployment_0"))
        protected Server reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_deployment_1"))
        protected Definition reference1;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public String server_id;
            public String dungeon_id;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof Key that)) return false;
                return Objects.equals(server_id, that.server_id)
                        && Objects.equals(dungeon_id, that.dungeon_id);
            }

            @Override
            public int hashCode() {
                return Objects.hash(server_id, dungeon_id);
            }
        }
    }

    @Entity(name = "DungeonTeam")
    @Table(name = "dungeon_team")
    public static class Team {
        public Team() {}

        @Id
        @Column(name = "team_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String team_id;

        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Column(name = "boot_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String boot_id;

        @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime created_at;

        @Column(name = "ended_at", nullable = true, columnDefinition = "DATETIME(3)")
        protected LocalDateTime ended_at;

        @Column(name = "end_reason", nullable = true, columnDefinition = "VARCHAR(64)")
        protected String end_reason;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "server_id",
                            referencedColumnName = "server_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_team_0"))
        protected Server reference0;
    }

    @Entity(name = "DungeonTeamMember")
    @Table(
            name = "dungeon_team_member",
            indexes = {
                @Index(name = "ix_team_player", columnList = "player_id,joined_at,membership_id")
            })
    public static class TeamMember {
        public TeamMember() {}

        @Id
        @Column(name = "membership_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String membership_id;

        @Column(name = "team_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String team_id;

        @Column(name = "player_id", nullable = false, columnDefinition = "BIGINT")
        protected Long player_id;

        @Column(name = "role", nullable = false, columnDefinition = "VARCHAR(16)")
        protected String role;

        @Column(name = "joined_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime joined_at;

        @Column(name = "left_at", nullable = true, columnDefinition = "DATETIME(3)")
        protected LocalDateTime left_at;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "team_id",
                            referencedColumnName = "team_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_team_member_0"))
        protected Team reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "player_id",
                            referencedColumnName = "id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_team_member_1"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected PlayerReference reference1;
    }

    @Entity(name = "DungeonAdmission")
    @Table(
            name = "dungeon_admission",
            indexes = {
                @Index(name = "ix_admission_pending", columnList = "server_id,status,requested_at")
            })
    public static class Admission {
        public Admission() {}

        @Id
        @Column(name = "admission_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String admission_id;

        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Column(name = "boot_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String boot_id;

        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Column(name = "team_id", nullable = true, columnDefinition = "CHAR(36)")
        protected String team_id;

        @Column(name = "difficulty", nullable = false, columnDefinition = "VARCHAR(64)")
        protected String difficulty;

        @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(32)")
        protected String status;

        @Column(name = "requested_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime requested_at;

        @Column(name = "ended_at", nullable = true, columnDefinition = "DATETIME(3)")
        protected LocalDateTime ended_at;

        @Column(name = "reason", nullable = true, columnDefinition = "VARCHAR(256)")
        protected String reason;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "server_id",
                            referencedColumnName = "server_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_admission_0"))
        protected Server reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_admission_1"))
        protected Definition reference1;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "team_id",
                            referencedColumnName = "team_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_admission_2"))
        protected Team reference2;
    }

    @Entity(name = "DungeonRun")
    @Table(
            name = "dungeon_run",
            indexes = {
                @Index(name = "ix_run_history", columnList = "dungeon_id,created_at,run_id"),
                @Index(name = "ix_run_server", columnList = "server_id,status")
            })
    public static class Run {
        public Run() {}

        @Id
        @Column(name = "run_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String run_id;

        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Column(name = "boot_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String boot_id;

        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Column(name = "world_name", nullable = false, columnDefinition = "VARCHAR(192)")
        protected String world_name;

        @Column(name = "dungeon_type", nullable = false, columnDefinition = "VARCHAR(32)")
        protected String dungeon_type;

        @Column(name = "difficulty", nullable = false, columnDefinition = "VARCHAR(64)")
        protected String difficulty;

        @Column(name = "fingerprint", nullable = false, columnDefinition = "CHAR(64)")
        protected String fingerprint;

        @Column(name = "config_snapshot", nullable = false, columnDefinition = "MEDIUMTEXT")
        protected String config_snapshot;

        @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(32)")
        protected String status;

        @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime created_at;

        @Column(name = "started_at", nullable = true, columnDefinition = "DATETIME(3)")
        protected LocalDateTime started_at;

        @Column(name = "ended_at", nullable = true, columnDefinition = "DATETIME(3)")
        protected LocalDateTime ended_at;

        @Column(name = "telemetry_complete", nullable = false, columnDefinition = "BOOLEAN")
        @ColumnDefault("TRUE")
        protected Boolean telemetry_complete;

        @Column(name = "competitive", nullable = false, columnDefinition = "BOOLEAN")
        @ColumnDefault("TRUE")
        protected Boolean competitive;

        @Column(name = "exclusion_reason", nullable = true, columnDefinition = "VARCHAR(256)")
        protected String exclusion_reason;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "server_id",
                            referencedColumnName = "server_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_run_0"))
        protected Server reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_run_1"))
        protected Definition reference1;
    }

    @Entity(name = "DungeonParticipation")
    @Table(
            name = "dungeon_participation",
            indexes = {
                @Index(
                        name = "ix_participation_player",
                        columnList = "player_id,entered_at,participation_id"),
                @Index(name = "ix_participation_run", columnList = "run_id,player_id")
            })
    public static class Participation {
        public Participation() {}

        @Id
        @Column(name = "participation_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String participation_id;

        @Column(name = "run_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String run_id;

        @Column(name = "admission_id", nullable = true, columnDefinition = "CHAR(36)")
        protected String admission_id;

        @Column(name = "player_id", nullable = false, columnDefinition = "BIGINT")
        protected Long player_id;

        @Column(name = "team_size", nullable = false, columnDefinition = "INT")
        protected Integer team_size;

        @Column(name = "entered_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime entered_at;

        @Column(name = "started_at", nullable = true, columnDefinition = "DATETIME(3)")
        protected LocalDateTime started_at;

        @Column(name = "completed_at", nullable = true, columnDefinition = "DATETIME(3)")
        protected LocalDateTime completed_at;

        @Column(name = "exited_at", nullable = true, columnDefinition = "DATETIME(3)")
        protected LocalDateTime exited_at;

        @Column(name = "outcome", nullable = false, columnDefinition = "VARCHAR(32)")
        protected String outcome;

        @Column(name = "departure_reason", nullable = true, columnDefinition = "VARCHAR(64)")
        protected String departure_reason;

        @Column(name = "elapsed_ms", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long elapsed_ms;

        @Column(name = "online_ms", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long online_ms;

        @Column(name = "deaths", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long deaths;

        @Column(name = "revives", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long revives;

        @Column(name = "kills", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long kills;

        @Column(name = "damage_dealt", nullable = false, columnDefinition = "DECIMAL(24,6)")
        @ColumnDefault("0")
        protected BigDecimal damage_dealt;

        @Column(name = "damage_received", nullable = false, columnDefinition = "DECIMAL(24,6)")
        @ColumnDefault("0")
        protected BigDecimal damage_received;

        @Column(name = "flush_sequence", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long flush_sequence;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "run_id",
                            referencedColumnName = "run_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_participation_0"))
        protected Run reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "admission_id",
                            referencedColumnName = "admission_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_participation_1"))
        protected Admission reference1;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "player_id",
                            referencedColumnName = "id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_participation_2"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected PlayerReference reference2;
    }

    @Entity(name = "DungeonEvent")
    @Table(
            name = "dungeon_event",
            indexes = {
                @Index(name = "ix_event_player", columnList = "player_id,occurred_at,event_id"),
                @Index(name = "ix_event_map", columnList = "dungeon_id,occurred_at,event_id"),
                @Index(name = "ix_event_operation", columnList = "operation_id"),
                @Index(name = "ix_event_team", columnList = "team_id,occurred_at,event_id")
            })
    public static class Event {
        public Event() {}

        @Id
        @Column(name = "event_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String event_id;

        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Column(name = "dungeon_id", nullable = true, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Column(name = "player_id", nullable = true, columnDefinition = "BIGINT")
        protected Long player_id;

        @Column(name = "run_id", nullable = true, columnDefinition = "CHAR(36)")
        protected String run_id;

        @Column(name = "team_id", nullable = true, columnDefinition = "CHAR(36)")
        protected String team_id;

        @Column(name = "operation_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String operation_id;

        @Column(name = "event_type", nullable = false, columnDefinition = "VARCHAR(48)")
        protected String event_type;

        @Column(name = "occurred_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime occurred_at;

        @Column(name = "detail", nullable = false, columnDefinition = "TEXT")
        protected String detail;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "player_id",
                            referencedColumnName = "id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_event_0"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected PlayerReference reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_event_1"))
        protected Definition reference1;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "server_id",
                            referencedColumnName = "server_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_event_2"))
        protected Server reference2;
    }

    @Entity(name = "DungeonPlayerProgress")
    @Table(name = "dungeon_player_progress")
    @IdClass(PlayerProgress.Key.class)
    public static class PlayerProgress {
        public PlayerProgress() {}

        @Id
        @Column(name = "player_id", nullable = false, columnDefinition = "BIGINT")
        protected Long player_id;

        @Id
        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Column(name = "completed", nullable = false, columnDefinition = "BOOLEAN")
        @ColumnDefault("FALSE")
        protected Boolean completed;

        @Column(name = "first_completed_at", nullable = true, columnDefinition = "DATETIME(3)")
        protected LocalDateTime first_completed_at;

        @Column(name = "last_completed_at", nullable = true, columnDefinition = "DATETIME(3)")
        protected LocalDateTime last_completed_at;

        @Column(name = "checkpoint", nullable = true, columnDefinition = "TEXT")
        protected String checkpoint;

        @Column(name = "checkpoint_fingerprint", nullable = true, columnDefinition = "CHAR(64)")
        protected String checkpoint_fingerprint;

        @Column(name = "revision", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long revision;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "player_id",
                            referencedColumnName = "id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_player_progress_0"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected PlayerReference reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_player_progress_1"))
        protected Definition reference1;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public Long player_id;
            public String dungeon_id;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof Key that)) return false;
                return Objects.equals(player_id, that.player_id)
                        && Objects.equals(dungeon_id, that.dungeon_id);
            }

            @Override
            public int hashCode() {
                return Objects.hash(player_id, dungeon_id);
            }
        }
    }

    @Entity(name = "DungeonCooldown")
    @Table(
            name = "dungeon_cooldown",
            indexes = {@Index(name = "ix_cooldown_expiry", columnList = "expires_at")})
    @IdClass(Cooldown.Key.class)
    public static class Cooldown {
        public Cooldown() {}

        @Id
        @Column(name = "player_id", nullable = false, columnDefinition = "BIGINT")
        protected Long player_id;

        @Id
        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Id
        @Column(name = "cooldown_type", nullable = false, columnDefinition = "VARCHAR(16)")
        protected String cooldown_type;

        @Id
        @Column(name = "source_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String source_id;

        @Column(name = "activated_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime activated_at;

        @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime expires_at;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "player_id",
                            referencedColumnName = "id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_cooldown_0"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected PlayerReference reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_cooldown_1"))
        protected Definition reference1;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public Long player_id;
            public String dungeon_id;
            public String cooldown_type;
            public String source_id;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof Key that)) return false;
                return Objects.equals(player_id, that.player_id)
                        && Objects.equals(dungeon_id, that.dungeon_id)
                        && Objects.equals(cooldown_type, that.cooldown_type)
                        && Objects.equals(source_id, that.source_id);
            }

            @Override
            public int hashCode() {
                return Objects.hash(player_id, dungeon_id, cooldown_type, source_id);
            }
        }
    }

    @Entity(name = "DungeonPlayerGuard")
    @Table(name = "dungeon_player_guard")
    public static class PlayerGuard {
        public PlayerGuard() {}

        @Id
        @Column(name = "player_id", nullable = false, columnDefinition = "BIGINT")
        protected Long player_id;

        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Column(name = "boot_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String boot_id;

        @Column(name = "operation_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String operation_id;

        @Column(name = "state", nullable = false, columnDefinition = "VARCHAR(32)")
        protected String state;

        @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime updated_at;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "player_id",
                            referencedColumnName = "id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_player_guard_0"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected PlayerReference reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "server_id",
                            referencedColumnName = "server_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_player_guard_1"))
        protected Server reference1;
    }

    @Entity(name = "DungeonKeyDefinition")
    @Table(name = "dungeon_key_definition")
    @IdClass(KeyDefinition.Key.class)
    public static class KeyDefinition {
        public KeyDefinition() {}

        @Id
        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Id
        @Column(name = "key_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String key_id;

        @Column(name = "revoked", nullable = false, columnDefinition = "BOOLEAN")
        @ColumnDefault("FALSE")
        protected Boolean revoked;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_key_definition_0"))
        protected Definition reference0;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public String dungeon_id;
            public String key_id;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof Key that)) return false;
                return Objects.equals(dungeon_id, that.dungeon_id)
                        && Objects.equals(key_id, that.key_id);
            }

            @Override
            public int hashCode() {
                return Objects.hash(dungeon_id, key_id);
            }
        }
    }

    @Entity(name = "DungeonKeyInstance")
    @Table(
            name = "dungeon_key_instance",
            indexes = {@Index(name = "ix_key_definition", columnList = "dungeon_id,key_id,state")})
    public static class KeyInstance {
        public KeyInstance() {}

        @Id
        @Column(name = "token_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String token_id;

        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Column(name = "key_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String key_id;

        @Column(name = "state", nullable = false, columnDefinition = "VARCHAR(24)")
        protected String state;

        @Column(name = "operation_id", nullable = true, columnDefinition = "CHAR(36)")
        protected String operation_id;

        @Column(name = "server_id", nullable = true, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Column(name = "issued_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime issued_at;

        @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime updated_at;

        @Column(name = "replacement_id", nullable = true, columnDefinition = "CHAR(36)")
        protected String replacement_id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false),
                    @JoinColumn(
                            name = "key_id",
                            referencedColumnName = "key_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_key_instance_0"))
        protected KeyDefinition reference0;
    }

    @Entity(name = "DungeonStaffOperation")
    @Table(
            name = "dungeon_staff_operation",
            indexes = {
                @Index(name = "ix_staff_history", columnList = "server_id,created_at,operation_id")
            })
    public static class StaffOperation {
        public StaffOperation() {}

        @Id
        @Column(name = "operation_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String operation_id;

        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Column(name = "actor_player_id", nullable = true, columnDefinition = "BIGINT")
        protected Long actor_player_id;

        @Column(name = "actor_name", nullable = false, columnDefinition = "VARCHAR(128)")
        protected String actor_name;

        @Column(name = "action", nullable = false, columnDefinition = "VARCHAR(64)")
        protected String action;

        @Column(name = "target", nullable = false, columnDefinition = "VARCHAR(256)")
        protected String target;

        @Column(name = "reason", nullable = false, columnDefinition = "VARCHAR(1024)")
        protected String reason;

        @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(24)")
        protected String status;

        @Column(name = "detail", nullable = false, columnDefinition = "TEXT")
        protected String detail;

        @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime created_at;

        @Column(name = "ended_at", nullable = true, columnDefinition = "DATETIME(3)")
        protected LocalDateTime ended_at;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "actor_player_id",
                            referencedColumnName = "id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_staff_operation_0"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected PlayerReference reference0;
    }

    @Entity(name = "DungeonMaintenance")
    @Table(name = "dungeon_maintenance")
    @IdClass(Maintenance.Key.class)
    public static class Maintenance {
        public Maintenance() {}

        @Id
        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Id
        @Column(name = "scope", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String scope;

        @Column(name = "reason", nullable = false, columnDefinition = "VARCHAR(1024)")
        protected String reason;

        @Column(name = "operation_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String operation_id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "server_id",
                            referencedColumnName = "server_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_maintenance_0"))
        protected Server reference0;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public String server_id;
            public String scope;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof Key that)) return false;
                return Objects.equals(server_id, that.server_id)
                        && Objects.equals(scope, that.scope);
            }

            @Override
            public int hashCode() {
                return Objects.hash(server_id, scope);
            }
        }
    }

    @Entity(name = "DungeonRestriction")
    @Table(name = "dungeon_restriction")
    @IdClass(Restriction.Key.class)
    public static class Restriction {
        public Restriction() {}

        @Id
        @Column(name = "player_id", nullable = false, columnDefinition = "BIGINT")
        protected Long player_id;

        @Id
        @Column(name = "scope", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String scope;

        @Column(name = "reason", nullable = false, columnDefinition = "VARCHAR(1024)")
        protected String reason;

        @Column(name = "expires_at", nullable = true, columnDefinition = "DATETIME(3)")
        protected LocalDateTime expires_at;

        @Column(name = "operation_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String operation_id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "player_id",
                            referencedColumnName = "id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_restriction_0"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected PlayerReference reference0;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public Long player_id;
            public String scope;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof Key that)) return false;
                return Objects.equals(player_id, that.player_id)
                        && Objects.equals(scope, that.scope);
            }

            @Override
            public int hashCode() {
                return Objects.hash(player_id, scope);
            }
        }
    }

    @Entity(name = "DungeonPlayerStats")
    @Table(
            name = "dungeon_player_stats",
            indexes = {
                @Index(
                        name = "ix_stats_ranking",
                        columnList = "dungeon_id,difficulty,best_time_ms,player_id")
            })
    @IdClass(PlayerStats.Key.class)
    public static class PlayerStats {
        public PlayerStats() {}

        @Id
        @Column(name = "player_id", nullable = false, columnDefinition = "BIGINT")
        protected Long player_id;

        @Id
        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Id
        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Id
        @Column(name = "difficulty", nullable = false, columnDefinition = "VARCHAR(64)")
        protected String difficulty;

        @Id
        @Column(name = "team_size", nullable = false, columnDefinition = "INT")
        protected Integer team_size;

        @Column(name = "visits", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long visits;

        @Column(name = "completions", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long completions;

        @Column(name = "elapsed_ms", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long elapsed_ms;

        @Column(name = "online_ms", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long online_ms;

        @Column(name = "deaths", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long deaths;

        @Column(name = "revives", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long revives;

        @Column(name = "kills", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long kills;

        @Column(name = "damage_dealt", nullable = false, columnDefinition = "DECIMAL(24,6)")
        @ColumnDefault("0")
        protected BigDecimal damage_dealt;

        @Column(name = "damage_received", nullable = false, columnDefinition = "DECIMAL(24,6)")
        @ColumnDefault("0")
        protected BigDecimal damage_received;

        @Column(name = "best_time_ms", nullable = true, columnDefinition = "BIGINT")
        protected Long best_time_ms;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "player_id",
                            referencedColumnName = "id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_player_stats_0"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected PlayerReference reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_player_stats_1"))
        protected Definition reference1;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "server_id",
                            referencedColumnName = "server_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_player_stats_2"))
        protected Server reference2;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public Long player_id;
            public String dungeon_id;
            public String server_id;
            public String difficulty;
            public Integer team_size;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof Key that)) return false;
                return Objects.equals(player_id, that.player_id)
                        && Objects.equals(dungeon_id, that.dungeon_id)
                        && Objects.equals(server_id, that.server_id)
                        && Objects.equals(difficulty, that.difficulty)
                        && Objects.equals(team_size, that.team_size);
            }

            @Override
            public int hashCode() {
                return Objects.hash(player_id, dungeon_id, server_id, difficulty, team_size);
            }
        }
    }

    @Entity(name = "DungeonStats")
    @Table(name = "dungeon_stats")
    @IdClass(Stats.Key.class)
    public static class Stats {
        public Stats() {}

        @Id
        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Id
        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Id
        @Column(name = "difficulty", nullable = false, columnDefinition = "VARCHAR(64)")
        protected String difficulty;

        @Column(name = "runs", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long runs;

        @Column(name = "visits", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long visits;

        @Column(name = "completions", nullable = false, columnDefinition = "BIGINT")
        @ColumnDefault("0")
        protected Long completions;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_stats_0"))
        protected Definition reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "server_id",
                            referencedColumnName = "server_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_stats_1"))
        protected Server reference1;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public String dungeon_id;
            public String server_id;
            public String difficulty;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof Key that)) return false;
                return Objects.equals(dungeon_id, that.dungeon_id)
                        && Objects.equals(server_id, that.server_id)
                        && Objects.equals(difficulty, that.difficulty);
            }

            @Override
            public int hashCode() {
                return Objects.hash(dungeon_id, server_id, difficulty);
            }
        }
    }

    @Entity(name = "DungeonMetricDefinition")
    @Table(name = "dungeon_metric_definition")
    @IdClass(MetricDefinition.Key.class)
    public static class MetricDefinition {
        public MetricDefinition() {}

        @Id
        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Id
        @Column(name = "metric_key", nullable = false, columnDefinition = "VARCHAR(64)")
        protected String metric_key;

        @Column(name = "label", nullable = false, columnDefinition = "VARCHAR(128)")
        protected String label;

        @Column(name = "unit", nullable = false, columnDefinition = "VARCHAR(32)")
        protected String unit;

        @Column(name = "aggregation", nullable = false, columnDefinition = "VARCHAR(8)")
        protected String aggregation;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_metric_definition_0"))
        protected Definition reference0;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public String dungeon_id;
            public String metric_key;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof Key that)) return false;
                return Objects.equals(dungeon_id, that.dungeon_id)
                        && Objects.equals(metric_key, that.metric_key);
            }

            @Override
            public int hashCode() {
                return Objects.hash(dungeon_id, metric_key);
            }
        }
    }

    @Entity(name = "DungeonParticipationMetric")
    @Table(name = "dungeon_participation_metric")
    @IdClass(ParticipationMetric.Key.class)
    public static class ParticipationMetric {
        public ParticipationMetric() {}

        @Id
        @Column(name = "participation_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String participation_id;

        @Id
        @Column(name = "metric_key", nullable = false, columnDefinition = "VARCHAR(64)")
        protected String metric_key;

        @Column(name = "value", nullable = false, columnDefinition = "DECIMAL(24,6)")
        protected BigDecimal value;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "participation_id",
                            referencedColumnName = "participation_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_participation_metric_0"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected Participation reference0;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public String participation_id;
            public String metric_key;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof Key that)) return false;
                return Objects.equals(participation_id, that.participation_id)
                        && Objects.equals(metric_key, that.metric_key);
            }

            @Override
            public int hashCode() {
                return Objects.hash(participation_id, metric_key);
            }
        }
    }

    @Entity(name = "DungeonPlayerMetric")
    @Table(
            name = "dungeon_player_metric",
            indexes = {
                @Index(
                        name = "ix_metric_ranking",
                        columnList = "dungeon_id,metric_key,value,player_id")
            })
    @IdClass(PlayerMetric.Key.class)
    public static class PlayerMetric {
        public PlayerMetric() {}

        @Id
        @Column(name = "player_id", nullable = false, columnDefinition = "BIGINT")
        protected Long player_id;

        @Id
        @Column(name = "dungeon_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String dungeon_id;

        @Id
        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Id
        @Column(name = "difficulty", nullable = false, columnDefinition = "VARCHAR(64)")
        protected String difficulty;

        @Id
        @Column(name = "metric_key", nullable = false, columnDefinition = "VARCHAR(64)")
        protected String metric_key;

        @Column(name = "value", nullable = false, columnDefinition = "DECIMAL(24,6)")
        protected BigDecimal value;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "player_id",
                            referencedColumnName = "id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_player_metric_0"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected PlayerReference reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "dungeon_id",
                            referencedColumnName = "dungeon_id",
                            insertable = false,
                            updatable = false),
                    @JoinColumn(
                            name = "metric_key",
                            referencedColumnName = "metric_key",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_player_metric_1"))
        protected MetricDefinition reference1;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public Long player_id;
            public String dungeon_id;
            public String server_id;
            public String difficulty;
            public String metric_key;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (!(other instanceof Key that)) return false;
                return Objects.equals(player_id, that.player_id)
                        && Objects.equals(dungeon_id, that.dungeon_id)
                        && Objects.equals(server_id, that.server_id)
                        && Objects.equals(difficulty, that.difficulty)
                        && Objects.equals(metric_key, that.metric_key);
            }

            @Override
            public int hashCode() {
                return Objects.hash(player_id, dungeon_id, server_id, difficulty, metric_key);
            }
        }
    }

    @Entity(name = "DungeonKeyReservation")
    @Table(name = "dungeon_key_reservation")
    public static class KeyReservation {
        public KeyReservation() {}

        @Id
        @Column(name = "token_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String token_id;

        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Column(name = "boot_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String boot_id;

        @Column(name = "operation_id", nullable = false, columnDefinition = "CHAR(36)")
        protected String operation_id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "token_id",
                            referencedColumnName = "token_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_key_reservation_0"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected KeyInstance reference0;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "server_id",
                            referencedColumnName = "server_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_key_reservation_1"))
        protected Server reference1;
    }

    @Entity(name = "DungeonStateDocument")
    @Table(
            name = "dungeon_state_document",
            indexes = {@Index(name = "ix_state_player", columnList = "server_id,player_id")})
    @IdClass(StateDocumentRow.Key.class)
    public static class StateDocumentRow {
        public StateDocumentRow() {}

        @Id
        @Column(name = "server_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String server_id;

        @Id
        @Column(name = "scope", nullable = false, columnDefinition = "VARCHAR(128)")
        protected String scope;

        @Id
        @Column(name = "record_id", nullable = false, columnDefinition = "VARCHAR(96)")
        protected String record_id;

        @Column(name = "player_id", columnDefinition = "BIGINT")
        protected Long player_id;

        @Column(name = "revision", nullable = false, columnDefinition = "BIGINT")
        protected Long revision;

        @Column(name = "payload", nullable = false, columnDefinition = "MEDIUMTEXT")
        protected String payload;

        @Column(name = "checksum", nullable = false, columnDefinition = "CHAR(64)")
        protected String checksum;

        @Column(name = "operation_id", columnDefinition = "CHAR(36)")
        protected String operation_id;

        @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)")
        protected LocalDateTime updated_at;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "player_id",
                            referencedColumnName = "id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_state_player"))
        @OnDelete(action = OnDeleteAction.CASCADE)
        protected PlayerReference player;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(
                value = {
                    @JoinColumn(
                            name = "server_id",
                            referencedColumnName = "server_id",
                            insertable = false,
                            updatable = false)
                },
                foreignKey = @ForeignKey(name = "fk_dungeon_state_server"))
        protected Server server;

        public static class Key implements Serializable {
            private static final long serialVersionUID = 1L;
            public String server_id;
            public String scope;
            public String record_id;

            public Key() {}

            @Override
            public boolean equals(Object other) {
                return other instanceof Key key
                        && Objects.equals(server_id, key.server_id)
                        && Objects.equals(scope, key.scope)
                        && Objects.equals(record_id, key.record_id);
            }

            @Override
            public int hashCode() {
                return Objects.hash(server_id, scope, record_id);
            }
        }
    }
}
