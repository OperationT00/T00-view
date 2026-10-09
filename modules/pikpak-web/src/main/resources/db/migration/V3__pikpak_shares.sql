CREATE TABLE pikpak_share (
  id VARCHAR(36) PRIMARY KEY,
  share_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  note TEXT NOT NULL,
  passcode_cipher TEXT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL
);
