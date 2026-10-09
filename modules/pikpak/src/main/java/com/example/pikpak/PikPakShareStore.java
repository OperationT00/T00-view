package com.example.pikpak;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.example.pikpak.PikPakModels.*;

/** Persistence port; mutations must be atomic, share IDs unique and updates version checked. */
public interface PikPakShareStore {
    default AddResult addIfAbsent(PikPakShareLink link, String name, String note, String passcode, Instant now) {
        return addIfAbsent(link, name, note, passcode, now, null);
    }
    AddResult addIfAbsent(PikPakShareLink link, String name, String note, String passcode, Instant now, String originalUrl);
    Optional<Share> get(String id);
    List<Share> list(String query, int offset, int limit);
    /** Never expose this result through an HTTP response. */
    String passcode(String id);
    /** null passcode preserves, empty string clears, otherwise replaces. */
    Share update(String id, long version, String name, String note, String passcode, Instant now);
    void delete(String id, long version);
}
