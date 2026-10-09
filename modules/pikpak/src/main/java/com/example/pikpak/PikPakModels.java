package com.example.pikpak;

import java.net.URI;
import java.time.Instant;
import java.util.List;

public final class PikPakModels {
    private PikPakModels() {}
    public enum Kind { FOLDER, VIDEO, OTHER }
    public enum Protocol { PROGRESSIVE, HLS, MPEG_TS }
    public record Share(String id, PikPakShareLink link, String name, String note,
                        long version, Instant createdAt, Instant updatedAt, String originalUrl) {}
    public record Entry(String fileId, String parentId, String name, Kind kind, Long sizeBytes) {
        public Entry {
            if (fileId == null || fileId.isBlank() || fileId.length() > 256 || name == null || kind == null
                || (sizeBytes != null && sizeBytes < 0)) throw new IllegalArgumentException("Invalid entry");
        }
    }
    public record Page(List<Entry> entries, String nextCursor) {
        public Page { entries = List.copyOf(entries); }
    }
    /** Server-side only: signed addresses must not be logged or stored as permanent locations. */
    public record Media(String id, String label, URI uri, Protocol protocol, String mimeType, Instant expiresAt,
                        String videoCodec, Long durationSeconds) {
        public Media(String id, String label, URI uri, Protocol protocol, String mimeType, Instant expiresAt) {
            this(id, label, uri, protocol, mimeType, expiresAt, null, null);
        }
        public Media {
            if (id == null || id.isBlank() || label == null || protocol == null || uri == null
                || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("Invalid media descriptor");
        }
        @Override public String toString() { return "Media[id=" + id + ", protocol=" + protocol + ", address=REDACTED]"; }
    }
    public record Playback(String shareId, String fileId, List<Media> variants) {
        public Playback { variants = List.copyOf(variants); }
    }
    public record AddResult(Share share, boolean created) {}
    public record ImportItem(String url, String name, String note, String passcode) {
        @Override public String toString() { return "ImportItem[REDACTED]"; }
    }
    public record ImportResult(int index, AddResult result, PikPakException.Code error) {}
}
