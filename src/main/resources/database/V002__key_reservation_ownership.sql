CREATE TABLE IF NOT EXISTS dungeon_key_reservation (
 token_id CHAR(36) NOT NULL PRIMARY KEY, server_id VARCHAR(96) NOT NULL,
 boot_id CHAR(36) NOT NULL, operation_id CHAR(36) NOT NULL,
 FOREIGN KEY(token_id) REFERENCES dungeon_key_instance(token_id) ON DELETE CASCADE,
 FOREIGN KEY(server_id) REFERENCES dungeon_server(server_id)
) ENGINE=InnoDB;
