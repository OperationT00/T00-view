package com.example.pikpak;

import java.time.Instant;
import java.util.*;
import com.example.pikpak.PikPakModels.*;
import static com.example.pikpak.PikPakException.Code.*;

/** For isolated embedding and tests. Not durable; use a persistent adapter in production. */
public final class InMemoryPikPakShareStore implements PikPakShareStore {
    private final Map<String, Share> rows = new LinkedHashMap<>();
    private final Map<String, String> secrets = new HashMap<>();
    @Override public synchronized AddResult addIfAbsent(PikPakShareLink link, String name, String note, String passcode, Instant now, String originalUrl) {
        for (Share row : rows.values()) if (row.link().equals(link)) return new AddResult(row, false);
        Share row = new Share(UUID.randomUUID().toString(), link, name, note, 0, now, now, originalUrl);
        rows.put(row.id(), row); secrets.put(row.id(), passcode == null ? "" : passcode);
        return new AddResult(row, true);
    }
    @Override public synchronized Optional<Share> get(String id) { return Optional.ofNullable(rows.get(id)); }
    @Override public synchronized List<Share> list(String query, int offset, int limit) {
        String q = query.toLowerCase(Locale.ROOT);
        return rows.values().stream().filter(r -> (r.name() + " " + r.note()).toLowerCase(Locale.ROOT).contains(q))
            .skip(offset).limit(limit).toList();
    }
    @Override public synchronized String passcode(String id) { require(id); return secrets.get(id); }
    @Override public synchronized Share update(String id, long version, String name, String note, String passcode, Instant now) {
        Share old = version(id, version);
        Share row = new Share(id, old.link(), name, note, old.version() + 1, old.createdAt(), now, old.originalUrl());
        rows.put(id, row); if (passcode != null) secrets.put(id, passcode); return row;
    }
    @Override public synchronized void delete(String id, long version) {
        version(id, version); rows.remove(id); secrets.remove(id);
    }
    private Share require(String id) {
        Share row = rows.get(id); if (row == null) throw new PikPakException(NOT_FOUND, "分享记录不存在"); return row;
    }
    private Share version(String id, long expected) {
        Share row = require(id);
        if (row.version() != expected) throw new PikPakException(CONFLICT, "分享已被修改，请刷新后重试");
        return row;
    }
}
