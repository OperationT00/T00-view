package com.example.pikpak;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import com.example.pikpak.PikPakModels.*;
import static org.junit.jupiter.api.Assertions.*;

class PikPakDuplicateScannerTest {
    interface Listing { Page list(String share, String parent, String cursor); }
    PikPakModule module(Listing listing, int count) {
        var gateway = new PikPakShareGateway() {
            public Page list(PikPakShareLink link, String pass, String parent, String cursor) { return listing.list(link.shareId(), parent, cursor); }
            public List<Media> resolve(PikPakShareLink link, String pass, String file) { throw new AssertionError("Must never download or resolve media"); }
        };
        var module = new PikPakModule(new InMemoryPikPakShareStore(), gateway, Clock.systemUTC());
        for (int i = 0; i < count; i++) module.add("https://mypikpak.com/s/share" + i, "分享" + i, "", "");
        return module;
    }
    Entry video(String id, String name, Long size) { return new Entry(id, "", name, Kind.VIDEO, size); }
    PikPakDuplicateScanner.Result await(PikPakDuplicateScanner scanner) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (scanner.current().status().equals("RUNNING") && System.nanoTime() < deadline) Thread.sleep(5);
        assertNotEquals("RUNNING", scanner.current().status()); return scanner.current();
    }
    @Test void recursivelyScansPagesAndRetainsExactLocationsWithoutCountingRepeatedEntries() throws Exception {
        var module = module((s,p,c) -> {
            if (p.isEmpty()) return new Page(List.of(new Entry("folder", "", "角色", Kind.FOLDER, null)), "");
            if (c.isEmpty()) return new Page(List.of(video("a", "original.mp4", 123L)), "next");
            return new Page(List.of(video("a", "original.mp4", 123L), video("b", "renamed.mp4", 123L),
                video("unknown", "unknown.mp4", null), video("zero", "zero.mp4", 0L), video("other", "original.mp4", 124L)), "");
        }, 2);
        try (var scanner = new PikPakDuplicateScanner(module)) {
            scanner.start(); var result = await(scanner);
            assertEquals("COMPLETED", result.status()); assertEquals(2, result.completedShares());
            assertEquals(6, result.pages()); assertEquals(10, result.videos()); assertEquals(4, result.unknownSize());
            assertEquals(2, result.groups().size()); assertEquals(4, result.groups().getFirst().videos().size());
            assertEquals(124L, result.groups().getLast().sizeBytes());
            assertEquals("角色", result.groups().getFirst().videos().getFirst().folders().getFirst().name());
            assertTrue(result.groups().getFirst().videos().getFirst().shareUrl().startsWith("https://mypikpak.com/s/"));
        }
    }
    @Test void failedShareDoesNotPreventOtherSharesAndCannotLookLikeCompleteScan() throws Exception {
        var module = module((s,p,c) -> {
            if (s.equals("share0")) throw new PikPakException(PikPakException.Code.WRONG_PASSWORD, "提取码错误");
            return new Page(List.of(video("a", "a.mp4", 10L), video("b", "b.mp4", 10L)), "");
        }, 2);
        try (var scanner = new PikPakDuplicateScanner(module)) {
            scanner.start(); var r = await(scanner);
            assertEquals("PARTIAL", r.status()); assertEquals(1, r.completedShares()); assertEquals(1, r.issues().size()); assertEquals(1, r.groups().size());
        }
    }
    @Test void cursorCyclesAreReportedAndFolderCyclesAreBounded() throws Exception {
        var module = module((s,p,c) -> new Page(List.of(new Entry("folder", "", "loop", Kind.FOLDER, null)), c.equals("a") ? "b" : "a"), 1);
        try (var scanner = new PikPakDuplicateScanner(module)) {
            scanner.start(); var r = await(scanner); assertEquals("PARTIAL", r.status()); assertEquals(3, r.pages());
        }
        var folders = module((s,p,c) -> new Page(List.of(new Entry("folder", "", "loop", Kind.FOLDER, null)), ""), 1);
        try (var scanner = new PikPakDuplicateScanner(folders)) {
            scanner.start(); assertEquals(2, await(scanner).pages());
        }
    }
    @Test void cancellationAndConcurrentStartsShareOneJob() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var module = module((s,p,c) -> {
            entered.countDown(); try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new Page(List.of(), "next");
        }, 1);
        try (var scanner = new PikPakDuplicateScanner(module)) {
            var first = scanner.start(); assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(first.id(), scanner.start().id()); scanner.cancel(first.id()); release.countDown();
            assertEquals("CANCELLED", await(scanner).status());
        } finally { release.countDown(); }
    }
    @Test void scansBeyondFirstHundredSharesAndDoesNotGroupUnknownSizes() throws Exception {
        var module = module((s,p,c) -> new Page(List.of(video("a", "same.mp4", null)), ""), 105);
        try (var scanner = new PikPakDuplicateScanner(module)) {
            scanner.start(); var r = await(scanner); assertEquals(105, r.completedShares()); assertTrue(r.groups().isEmpty());
        }
    }
}
