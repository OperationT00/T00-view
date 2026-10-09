package com.example.pikpak;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.example.pikpak.PikPakModels.*;

/** Read-only, metadata-only scan. Exact byte size is evidence for a candidate, never proof. */
public final class PikPakDuplicateScanner implements AutoCloseable {
    public record Folder(String id, String name) {}
    public record Video(String shareId, String shareName, String shareUrl, String fileId,
                        String name, long sizeBytes, List<Folder> folders) {}
    public record Group(long sizeBytes, List<Video> videos) {}
    public record Issue(String shareId, String shareName, String message) {}
    public record Result(String id, String status, Instant startedAt, Instant finishedAt,
                         int totalShares, int completedShares, int pages, int videos, int unknownSize,
                         List<Group> groups, List<Issue> issues) {}
    private final PikPakModule module;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pikpak-duplicate-scan"); t.setDaemon(true); return t;
    });
    private volatile boolean cancelled;
    private volatile Result result;
    public PikPakDuplicateScanner(PikPakModule module) { this.module = module; }
    public Result current() { return result; }
    public synchronized Result start() {
        if (result != null && result.status().equals("RUNNING")) return result;
        cancelled = false;
        result = new Result(UUID.randomUUID().toString(), "RUNNING", Instant.now(), null, 0, 0, 0, 0, 0, List.of(), List.of());
        executor.submit(this::scan);
        return result;
    }
    public synchronized Result cancel(String id) {
        if (result != null && result.id().equals(id) && result.status().equals("RUNNING")) cancelled = true;
        return result;
    }
    private void check() {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
    private void scan() {
        Result initial = result;
        List<Share> shares = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        Map<Long, List<Video>> candidates = new TreeMap<>();
        int completed = 0, pages = 0, videos = 0, unknown = 0, entries = 0;
        String status = "COMPLETED";
        try {
            for (int offset = 0; ; offset += 100) {
                check();
                var batch = module.list("", offset, 100);
                shares.addAll(batch);
                if (shares.size() > 5000) throw new IllegalStateException("扫描最多支持 5000 条分享，请缩小范围");
                if (batch.size() < 100) break;
            }
            for (Share share : shares) {
                check();
                try {
                    ArrayDeque<List<Folder>> queue = new ArrayDeque<>();
                    queue.add(List.of());
                    Set<String> seenFiles = new HashSet<>(), seenFolders = new HashSet<>();
                    while (!queue.isEmpty()) {
                        check();
                        List<Folder> path = queue.removeFirst();
                        String parent = path.isEmpty() ? "" : path.getLast().id();
                        String cursor = "";
                        Set<String> cursors = new HashSet<>();
                        do {
                            check();
                            if (++pages > 10000) throw new IllegalStateException("已达到 10000 页扫描上限，结果不完整");
                            if (module.get(share.id()).version() != share.version())
                                throw new PikPakException(PikPakException.Code.CONFLICT, "分享在扫描期间被修改，请重新扫描");
                            Page page = module.browse(share.id(), parent, cursor);
                            for (Entry entry : page.entries()) {
                                if (++entries > 100000) throw new IllegalStateException("已达到 100000 项扫描上限，结果不完整");
                                if (entry.kind() == Kind.FOLDER && seenFolders.add(entry.fileId())) {
                                    if (path.size() >= 32) throw new PikPakException(PikPakException.Code.INVALID_INPUT, "目录超过 32 层，未能完整扫描");
                                    var next = new ArrayList<>(path); next.add(new Folder(entry.fileId(), entry.name()));
                                    queue.add(List.copyOf(next));
                                } else if (entry.kind() == Kind.VIDEO && seenFiles.add(entry.fileId())) {
                                    videos++;
                                    if (entry.sizeBytes() == null || entry.sizeBytes() <= 0) { unknown++; continue; }
                                    candidates.computeIfAbsent(entry.sizeBytes(), k -> new ArrayList<>()).add(new Video(
                                        share.id(), share.name(), share.originalUrl() == null ? share.link().uri().toString() : share.originalUrl(),
                                        entry.fileId(), entry.name(), entry.sizeBytes(), path));
                                }
                            }
                            cursor = page.nextCursor();
                            if (cursor != null && !cursor.isEmpty() && !cursors.add(cursor))
                                throw new PikPakException(PikPakException.Code.INVALID_CURSOR, "分页游标循环，未能完整扫描");
                            result = new Result(initial.id(), "RUNNING", initial.startedAt(), null, shares.size(), completed,
                                pages, videos, unknown, List.of(), List.copyOf(issues));
                        } while (cursor != null && !cursor.isEmpty());
                    }
                    completed++;
                } catch (PikPakException e) {
                    issues.add(new Issue(share.id(), share.name(), e.getMessage()));
                }
            }
            if (!issues.isEmpty()) status = "PARTIAL";
        } catch (CancellationException e) { status = "CANCELLED";
        } catch (Exception e) {
            status = "PARTIAL";
            issues.add(new Issue("", "扫描任务", e instanceof IllegalStateException ? e.getMessage() : "扫描异常中断，请重试"));
        } finally {
            var groups = candidates.entrySet().stream().filter(e -> e.getValue().size() > 1)
                .map(e -> new Group(e.getKey(), List.copyOf(e.getValue()))).toList();
            result = new Result(initial.id(), status, initial.startedAt(), Instant.now(), shares.size(), completed,
                pages, videos, unknown, groups, List.copyOf(issues));
        }
    }
    @Override public void close() { cancelled = true; executor.shutdownNow(); }
}
