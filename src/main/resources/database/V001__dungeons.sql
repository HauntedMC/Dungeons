CREATE TABLE IF NOT EXISTS dungeon_server (
 server_id VARCHAR(96) NOT NULL PRIMARY KEY, boot_id CHAR(36) NOT NULL,
 epoch BIGINT NOT NULL, lease_until DATETIME(3) NOT NULL,
 storage_version INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_definition (
 dungeon_id CHAR(36) NOT NULL PRIMARY KEY, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_deployment (
 server_id VARCHAR(96) NOT NULL, dungeon_id CHAR(36) NOT NULL, local_name VARCHAR(128) NOT NULL,
 fingerprint CHAR(64) NOT NULL, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(server_id,dungeon_id), UNIQUE KEY uq_deployment_name(server_id,local_name),
 FOREIGN KEY(server_id) REFERENCES dungeon_server(server_id),
 FOREIGN KEY(dungeon_id) REFERENCES dungeon_definition(dungeon_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_team (
 team_id CHAR(36) NOT NULL PRIMARY KEY, server_id VARCHAR(96) NOT NULL, boot_id CHAR(36) NOT NULL,
 created_at DATETIME(3) NOT NULL, ended_at DATETIME(3), end_reason VARCHAR(64),
 FOREIGN KEY(server_id) REFERENCES dungeon_server(server_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_team_member (
 membership_id CHAR(36) NOT NULL PRIMARY KEY, team_id CHAR(36) NOT NULL, player_id BIGINT NOT NULL,
 role VARCHAR(16) NOT NULL, joined_at DATETIME(3) NOT NULL, left_at DATETIME(3),
 KEY ix_team_player(player_id,joined_at,membership_id),
 FOREIGN KEY(team_id) REFERENCES dungeon_team(team_id),
 FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_admission (
 admission_id CHAR(36) NOT NULL PRIMARY KEY, server_id VARCHAR(96) NOT NULL, boot_id CHAR(36) NOT NULL,
 dungeon_id CHAR(36) NOT NULL, team_id CHAR(36), difficulty VARCHAR(64) NOT NULL,
 status VARCHAR(32) NOT NULL, requested_at DATETIME(3) NOT NULL, ended_at DATETIME(3), reason VARCHAR(256),
 KEY ix_admission_pending(server_id,status,requested_at),
 FOREIGN KEY(server_id) REFERENCES dungeon_server(server_id),
 FOREIGN KEY(dungeon_id) REFERENCES dungeon_definition(dungeon_id),
 FOREIGN KEY(team_id) REFERENCES dungeon_team(team_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_run (
 run_id CHAR(36) NOT NULL PRIMARY KEY, server_id VARCHAR(96) NOT NULL, boot_id CHAR(36) NOT NULL,
 dungeon_id CHAR(36) NOT NULL, world_name VARCHAR(192) NOT NULL, dungeon_type VARCHAR(32) NOT NULL,
 difficulty VARCHAR(64) NOT NULL, fingerprint CHAR(64) NOT NULL, config_snapshot MEDIUMTEXT NOT NULL,
 status VARCHAR(32) NOT NULL, created_at DATETIME(3) NOT NULL, started_at DATETIME(3), ended_at DATETIME(3),
 telemetry_complete BOOLEAN NOT NULL DEFAULT TRUE, competitive BOOLEAN NOT NULL DEFAULT TRUE,
 exclusion_reason VARCHAR(256),
 KEY ix_run_history(dungeon_id,created_at,run_id), KEY ix_run_server(server_id,status),
 FOREIGN KEY(server_id) REFERENCES dungeon_server(server_id),
 FOREIGN KEY(dungeon_id) REFERENCES dungeon_definition(dungeon_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_participation (
 participation_id CHAR(36) NOT NULL PRIMARY KEY, run_id CHAR(36) NOT NULL, admission_id CHAR(36),
 player_id BIGINT NOT NULL, team_size INT NOT NULL, entered_at DATETIME(3) NOT NULL,
 started_at DATETIME(3), completed_at DATETIME(3), exited_at DATETIME(3), outcome VARCHAR(32) NOT NULL,
 departure_reason VARCHAR(64), elapsed_ms BIGINT NOT NULL DEFAULT 0, online_ms BIGINT NOT NULL DEFAULT 0,
 deaths BIGINT NOT NULL DEFAULT 0, revives BIGINT NOT NULL DEFAULT 0, kills BIGINT NOT NULL DEFAULT 0,
 damage_dealt DECIMAL(24,6) NOT NULL DEFAULT 0, damage_received DECIMAL(24,6) NOT NULL DEFAULT 0,
 flush_sequence BIGINT NOT NULL DEFAULT 0,
 KEY ix_participation_player(player_id,entered_at,participation_id), KEY ix_participation_run(run_id,player_id),
 FOREIGN KEY(run_id) REFERENCES dungeon_run(run_id),
 FOREIGN KEY(admission_id) REFERENCES dungeon_admission(admission_id),
 FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_event (
 event_id CHAR(36) NOT NULL PRIMARY KEY, server_id VARCHAR(96) NOT NULL,
 dungeon_id CHAR(36), player_id BIGINT, run_id CHAR(36), team_id CHAR(36),
 operation_id CHAR(36) NOT NULL, event_type VARCHAR(48) NOT NULL, occurred_at DATETIME(3) NOT NULL,
 detail TEXT NOT NULL,
 KEY ix_event_player(player_id,occurred_at,event_id), KEY ix_event_map(dungeon_id,occurred_at,event_id),
 KEY ix_event_operation(operation_id), KEY ix_event_team(team_id,occurred_at,event_id),
 FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE,
 FOREIGN KEY(dungeon_id) REFERENCES dungeon_definition(dungeon_id),
 FOREIGN KEY(server_id) REFERENCES dungeon_server(server_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_player_progress (
 player_id BIGINT NOT NULL, dungeon_id CHAR(36) NOT NULL, completed BOOLEAN NOT NULL DEFAULT FALSE,
 first_completed_at DATETIME(3), last_completed_at DATETIME(3), checkpoint TEXT,
 checkpoint_fingerprint CHAR(64), revision BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(player_id,dungeon_id), FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE,
 FOREIGN KEY(dungeon_id) REFERENCES dungeon_definition(dungeon_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_cooldown (
 player_id BIGINT NOT NULL, dungeon_id CHAR(36) NOT NULL, cooldown_type VARCHAR(16) NOT NULL,
 source_id VARCHAR(96) NOT NULL, activated_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NOT NULL,
 PRIMARY KEY(player_id,dungeon_id,cooldown_type,source_id), KEY ix_cooldown_expiry(expires_at),
 FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE,
 FOREIGN KEY(dungeon_id) REFERENCES dungeon_definition(dungeon_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_player_guard (
 player_id BIGINT NOT NULL PRIMARY KEY, server_id VARCHAR(96) NOT NULL, boot_id CHAR(36) NOT NULL,
 operation_id CHAR(36) NOT NULL, state VARCHAR(32) NOT NULL, updated_at DATETIME(3) NOT NULL,
 FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE,
 FOREIGN KEY(server_id) REFERENCES dungeon_server(server_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_key_definition (
 dungeon_id CHAR(36) NOT NULL, key_id VARCHAR(96) NOT NULL, revoked BOOLEAN NOT NULL DEFAULT FALSE,
 PRIMARY KEY(dungeon_id,key_id), FOREIGN KEY(dungeon_id) REFERENCES dungeon_definition(dungeon_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_key_instance (
 token_id CHAR(36) NOT NULL PRIMARY KEY, dungeon_id CHAR(36) NOT NULL, key_id VARCHAR(96) NOT NULL,
 state VARCHAR(24) NOT NULL, operation_id CHAR(36), server_id VARCHAR(96),
 issued_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, replacement_id CHAR(36),
 KEY ix_key_definition(dungeon_id,key_id,state),
 FOREIGN KEY(dungeon_id,key_id) REFERENCES dungeon_key_definition(dungeon_id,key_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_reward_grant (
 grant_id CHAR(36) NOT NULL PRIMARY KEY, player_id BIGINT NOT NULL, server_id VARCHAR(96) NOT NULL,
 dungeon_id CHAR(36), participation_id CHAR(36), operation_id CHAR(36) NOT NULL,
 grant_type VARCHAR(24) NOT NULL, payload MEDIUMBLOB NOT NULL, payload_version INT NOT NULL,
 payload_checksum CHAR(64) NOT NULL, status VARCHAR(24) NOT NULL, created_at DATETIME(3) NOT NULL,
 KEY ix_grant_claim(player_id,server_id,status,created_at,grant_id), KEY ix_grant_operation(operation_id),
 FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE,
 FOREIGN KEY(server_id) REFERENCES dungeon_server(server_id),
 FOREIGN KEY(dungeon_id) REFERENCES dungeon_definition(dungeon_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_delivery (
 delivery_id CHAR(36) NOT NULL PRIMARY KEY, grant_id CHAR(36) NOT NULL,
 player_id BIGINT NOT NULL, server_id VARCHAR(96) NOT NULL, status VARCHAR(24) NOT NULL,
 payload MEDIUMBLOB NOT NULL, payload_checksum CHAR(64) NOT NULL, created_at DATETIME(3) NOT NULL,
 acknowledged_at DATETIME(3), KEY ix_delivery_pending(player_id,server_id,status),
 FOREIGN KEY(grant_id) REFERENCES dungeon_reward_grant(grant_id) ON DELETE CASCADE,
 FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_staff_operation (
 operation_id CHAR(36) NOT NULL PRIMARY KEY, server_id VARCHAR(96) NOT NULL, actor_player_id BIGINT,
 actor_name VARCHAR(128) NOT NULL, action VARCHAR(64) NOT NULL, target VARCHAR(256) NOT NULL,
 reason VARCHAR(1024) NOT NULL, status VARCHAR(24) NOT NULL, detail TEXT NOT NULL,
 created_at DATETIME(3) NOT NULL, ended_at DATETIME(3),
 KEY ix_staff_history(server_id,created_at,operation_id),
 FOREIGN KEY(actor_player_id) REFERENCES player_entity(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_maintenance (
 server_id VARCHAR(96) NOT NULL, scope VARCHAR(96) NOT NULL, reason VARCHAR(1024) NOT NULL,
 operation_id CHAR(36) NOT NULL, PRIMARY KEY(server_id,scope),
 FOREIGN KEY(server_id) REFERENCES dungeon_server(server_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_restriction (
 player_id BIGINT NOT NULL, scope VARCHAR(96) NOT NULL, reason VARCHAR(1024) NOT NULL,
 expires_at DATETIME(3), operation_id CHAR(36) NOT NULL, PRIMARY KEY(player_id,scope),
 FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_player_stats (
 player_id BIGINT NOT NULL, dungeon_id CHAR(36) NOT NULL, server_id VARCHAR(96) NOT NULL,
 difficulty VARCHAR(64) NOT NULL, team_size INT NOT NULL,
 visits BIGINT NOT NULL DEFAULT 0, completions BIGINT NOT NULL DEFAULT 0,
 elapsed_ms BIGINT NOT NULL DEFAULT 0, online_ms BIGINT NOT NULL DEFAULT 0,
 deaths BIGINT NOT NULL DEFAULT 0, revives BIGINT NOT NULL DEFAULT 0, kills BIGINT NOT NULL DEFAULT 0,
 damage_dealt DECIMAL(24,6) NOT NULL DEFAULT 0, damage_received DECIMAL(24,6) NOT NULL DEFAULT 0,
 best_time_ms BIGINT,
 PRIMARY KEY(player_id,dungeon_id,server_id,difficulty,team_size),
 KEY ix_stats_ranking(dungeon_id,difficulty,best_time_ms,player_id),
 FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE,
 FOREIGN KEY(dungeon_id) REFERENCES dungeon_definition(dungeon_id),
 FOREIGN KEY(server_id) REFERENCES dungeon_server(server_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_stats (
 dungeon_id CHAR(36) NOT NULL, server_id VARCHAR(96) NOT NULL, difficulty VARCHAR(64) NOT NULL,
 runs BIGINT NOT NULL DEFAULT 0, visits BIGINT NOT NULL DEFAULT 0, completions BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(dungeon_id,server_id,difficulty),
 FOREIGN KEY(dungeon_id) REFERENCES dungeon_definition(dungeon_id),
 FOREIGN KEY(server_id) REFERENCES dungeon_server(server_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_metric_definition (
 dungeon_id CHAR(36) NOT NULL, metric_key VARCHAR(64) NOT NULL, label VARCHAR(128) NOT NULL,
 unit VARCHAR(32) NOT NULL, aggregation VARCHAR(8) NOT NULL, PRIMARY KEY(dungeon_id,metric_key),
 FOREIGN KEY(dungeon_id) REFERENCES dungeon_definition(dungeon_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_participation_metric (
 participation_id CHAR(36) NOT NULL, metric_key VARCHAR(64) NOT NULL, value DECIMAL(24,6) NOT NULL,
 PRIMARY KEY(participation_id,metric_key),
 FOREIGN KEY(participation_id) REFERENCES dungeon_participation(participation_id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dungeon_player_metric (
 player_id BIGINT NOT NULL, dungeon_id CHAR(36) NOT NULL, server_id VARCHAR(96) NOT NULL,
 difficulty VARCHAR(64) NOT NULL, metric_key VARCHAR(64) NOT NULL, value DECIMAL(24,6) NOT NULL,
 PRIMARY KEY(player_id,dungeon_id,server_id,difficulty,metric_key),
 KEY ix_metric_ranking(dungeon_id,metric_key,value,player_id),
 FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE,
 FOREIGN KEY(dungeon_id,metric_key) REFERENCES dungeon_metric_definition(dungeon_id,metric_key)
) ENGINE=InnoDB;
