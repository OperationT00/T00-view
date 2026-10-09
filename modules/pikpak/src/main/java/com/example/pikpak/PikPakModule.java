package com.example.pikpak;

import java.time.Clock;
import java.util.*;
import com.example.pikpak.PikPakModels.*;
import static com.example.pikpak.PikPakException.Code.*;

/** Public facade. No accounts, roles, catalogue, tag parser, Spring or filesystem dependency. */
public final class PikPakModule {
    private final PikPakShareStore store;
    private final PikPakShareGateway gateway;
    private final Clock clock;
    public PikPakModule(PikPakShareStore store, PikPakShareGateway gateway, Clock clock) {
        this.store = Objects.requireNonNull(store); this.gateway = Objects.requireNonNull(gateway); this.clock = Objects.requireNonNull(clock);
    }
    public AddResult add(String url, String name, String note, String passcode) {
        PikPakShareLink link = PikPakShareLink.parse(url);
        return store.addIfAbsent(link, text(name, 255, true), text(note, 4000, false), secret(passcode), clock.instant(), url.strip());
    }
    public List<ImportResult> addBatch(List<ImportItem> items) {
        if (items == null || items.isEmpty() || items.size() > 20) throw new PikPakException(INVALID_INPUT, "每批需1至20条链接");
        List<ImportResult> results = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ImportItem item = items.get(i);
            if (item == null) { results.add(new ImportResult(i, null, INVALID_INPUT)); continue; }
            try { results.add(new ImportResult(i, add(item.url(), item.name(), item.note(), item.passcode()), null)); }
            catch (PikPakException e) { results.add(new ImportResult(i, null, e.code())); }
        }
        return List.copyOf(results);
    }
    public Share get(String id) { return store.get(id).orElseThrow(() -> new PikPakException(NOT_FOUND, "分享记录不存在")); }
    public List<Share> list(String query, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) throw new PikPakException(INVALID_INPUT, "分页参数不合法");
        return List.copyOf(store.list(text(query, 255, false), offset, limit));
    }
    public String exportLinks(String id) {
        List<Share> shares = id == null ? store.list("", 0, Integer.MAX_VALUE) : List.of(get(id));
        return shares.stream().map(s -> s.originalUrl() == null ? s.link().uri().toString() : s.originalUrl())
            .collect(java.util.stream.Collectors.joining("\r\n", "", shares.isEmpty() ? "" : "\r\n"));
    }
    public Share update(String id, long version, String name, String note, String passcode) {
        return store.update(id, version, text(name, 255, true), text(note, 4000, false), secret(passcode), clock.instant());
    }
    public void delete(String id, long version) { store.delete(id, version); }
    public Page browse(String id, String parentId, String cursor) {
        Share row = get(id);
        Page page = gateway.list(row.link(), store.passcode(id), text(parentId, 256, false), text(cursor, 2048, false));
        requireUnchanged(row);
        if (page.entries().size() > 100) throw new PikPakException(UPSTREAM_UNAVAILABLE, "分享目录返回数量异常");
        if (cursor != null && !cursor.isEmpty() && cursor.equals(page.nextCursor()))
            throw new PikPakException(INVALID_CURSOR, "目录分页未前进，请重新读取");
        return page;
    }
    public Playback playback(String id, String fileId) {
        Share row = get(id);
        List<Media> media = gateway.resolve(row.link(), store.passcode(id), text(fileId, 256, true));
        requireUnchanged(row);
        List<Media> usable = media.stream().filter(m -> m.expiresAt() == null || m.expiresAt().isAfter(clock.instant())).toList();
        if (usable.isEmpty()) throw new PikPakException(MEDIA_UNAVAILABLE, "没有可用的播放地址");
        return new Playback(id, fileId, usable);
    }
    private void requireUnchanged(Share before) {
        if (get(before.id()).version() != before.version()) throw new PikPakException(CONFLICT, "分享已被修改，请重新解析");
    }
    private static String text(String value, int max, boolean required) {
        String out = value == null ? "" : value.strip();
        if (out.length() > max || (required && out.isEmpty()) || out.codePoints().anyMatch(Character::isISOControl))
            throw new PikPakException(INVALID_INPUT, "输入为空、超长或含控制字符");
        return out;
    }
    private static String secret(String value) {
        if (value != null && (value.length() > 128 || value.codePoints().anyMatch(Character::isISOControl)))
            throw new PikPakException(INVALID_INPUT, "提取码不合法");
        return value;
    }
}
