CREATE TABLE app_guard (
    guard_key VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY
) ENGINE=InnoDB;
INSERT INTO app_guard (guard_key) VALUES ('ADMIN_INVARIANT'), ('TAG_TREE');

CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    normalized_username VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'VIEWER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    security_version BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_username (normalized_username),
    CONSTRAINT ck_user_role CHECK (role IN ('ADMIN', 'VIEWER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE asset (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    kind VARCHAR(24) NOT NULL,
    description TEXT NULL,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_asset_order (created_at, id),
    KEY ix_asset_kind_order (kind, created_at, id),
    CONSTRAINT ck_asset_kind CHECK (kind IN ('VIDEO', 'IMAGE_SET', 'OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE tag (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT NULL,
    name VARCHAR(64) NOT NULL,
    normalized_name VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
    parent_scope BIGINT GENERATED ALWAYS AS (COALESCE(parent_id, 0)) STORED,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_tag_sibling (parent_scope, normalized_name),
    KEY ix_tag_parent (parent_id, id),
    CONSTRAINT fk_tag_parent FOREIGN KEY (parent_id) REFERENCES tag(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE asset_tag (
    asset_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (asset_id, tag_id),
    KEY ix_asset_tag_reverse (tag_id, asset_id),
    CONSTRAINT fk_at_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE CASCADE,
    CONSTRAINT fk_at_tag FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE storage_source (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(24) NOT NULL,
    reported_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    status_reported_at DATETIME(6) NULL,
    note TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_source_type CHECK (type IN ('LOCAL_DISK', 'PIKPAK', 'BAIDU')),
    CONSTRAINT ck_source_status CHECK (reported_status IN ('UNKNOWN', 'ONLINE', 'OFFLINE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE asset_location (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    locator_type VARCHAR(24) NOT NULL,
    locator TEXT NOT NULL,
    note TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_location_asset (asset_id, id),
    KEY ix_location_source (source_id, asset_id),
    CONSTRAINT fk_location_asset FOREIGN KEY (asset_id) REFERENCES asset(id) ON DELETE CASCADE,
    CONSTRAINT fk_location_source FOREIGN KEY (source_id) REFERENCES storage_source(id) ON DELETE RESTRICT,
    CONSTRAINT ck_locator_type CHECK (locator_type IN ('LOCAL_PATH', 'CLOUD_PATH', 'WEB_URL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
