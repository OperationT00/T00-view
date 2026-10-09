INSERT INTO app_guard (guard_key) VALUES ('FACET_CATALOG');

CREATE TABLE content_type (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(128) NOT NULL,
    note TEXT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_content_type_name (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE work_scope (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(128) NOT NULL,
    note TEXT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_work_scope_name (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE catalog_character (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    work_scope_id BIGINT NULL,
    work_scope_key BIGINT GENERATED ALWAYS AS (COALESCE(work_scope_id, 0)) STORED,
    name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(128) NOT NULL,
    disambiguation VARCHAR(128) NOT NULL DEFAULT '',
    normalized_disambiguation VARCHAR(128) NOT NULL DEFAULT '',
    note TEXT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_character_identity (work_scope_key, normalized_name, normalized_disambiguation),
    KEY ix_character_work (work_scope_id, id),
    CONSTRAINT fk_character_work FOREIGN KEY (work_scope_id) REFERENCES work_scope(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE skin (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    character_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(128) NOT NULL,
    note TEXT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_skin_identity (character_id, normalized_name),
    CONSTRAINT fk_skin_character FOREIGN KEY (character_id) REFERENCES catalog_character(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE clothing_tag (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(128) NOT NULL,
    note TEXT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_clothing_name (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE creator (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(128) NOT NULL,
    disambiguation VARCHAR(128) NOT NULL DEFAULT '',
    normalized_disambiguation VARCHAR(128) NOT NULL DEFAULT '',
    note TEXT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_creator_identity (normalized_name, normalized_disambiguation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE creator_account (
    creator_id BIGINT NOT NULL,
    platform VARCHAR(64) NOT NULL,
    account VARCHAR(128) NOT NULL,
    homepage VARCHAR(2048) NULL,
    PRIMARY KEY (creator_id, platform, account),
    CONSTRAINT fk_creator_account_owner FOREIGN KEY (creator_id) REFERENCES creator(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE asset_content_type (
    asset_id BIGINT NOT NULL,
    entity_id BIGINT NOT NULL,
    PRIMARY KEY (asset_id, entity_id),
    KEY ix_asset_type_entity (entity_id, asset_id),
    CONSTRAINT fk_asset_type_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_type_entity FOREIGN KEY (entity_id) REFERENCES content_type(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE asset_work_scope (
    asset_id BIGINT NOT NULL,
    entity_id BIGINT NOT NULL,
    PRIMARY KEY (asset_id, entity_id),
    KEY ix_asset_work_entity (entity_id, asset_id),
    CONSTRAINT fk_asset_work_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_work_entity FOREIGN KEY (entity_id) REFERENCES work_scope(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE asset_character (
    asset_id BIGINT NOT NULL,
    entity_id BIGINT NOT NULL,
    PRIMARY KEY (asset_id, entity_id),
    KEY ix_asset_character_entity (entity_id, asset_id),
    CONSTRAINT fk_asset_character_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_character_entity FOREIGN KEY (entity_id) REFERENCES catalog_character(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE asset_skin (
    asset_id BIGINT NOT NULL,
    entity_id BIGINT NOT NULL,
    PRIMARY KEY (asset_id, entity_id),
    KEY ix_asset_skin_entity (entity_id, asset_id),
    CONSTRAINT fk_asset_skin_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_skin_entity FOREIGN KEY (entity_id) REFERENCES skin(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE asset_clothing (
    asset_id BIGINT NOT NULL,
    entity_id BIGINT NOT NULL,
    PRIMARY KEY (asset_id, entity_id),
    KEY ix_asset_clothing_entity (entity_id, asset_id),
    CONSTRAINT fk_asset_clothing_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_clothing_entity FOREIGN KEY (entity_id) REFERENCES clothing_tag(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE asset_creator (
    asset_id BIGINT NOT NULL,
    entity_id BIGINT NOT NULL,
    PRIMARY KEY (asset_id, entity_id),
    KEY ix_asset_creator_entity (entity_id, asset_id),
    CONSTRAINT fk_asset_creator_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_creator_entity FOREIGN KEY (entity_id) REFERENCES creator(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE legacy_facet_mapping (
    legacy_tag_id BIGINT NOT NULL,
    dimension VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entity_id BIGINT NOT NULL,
    PRIMARY KEY (legacy_tag_id, dimension, entity_id),
    KEY ix_legacy_mapping_target (dimension, entity_id),
    CONSTRAINT fk_legacy_mapping_tag FOREIGN KEY (legacy_tag_id) REFERENCES tag(id) ON DELETE RESTRICT,
    CONSTRAINT ck_legacy_mapping_dimension CHECK (dimension IN ('TYPE','WORK','CHARACTER','SKIN','CLOTHING','CREATOR'))
) ENGINE=InnoDB;
