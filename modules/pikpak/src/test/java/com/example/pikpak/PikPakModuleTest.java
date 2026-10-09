package com.example.pikpak;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.example.pikpak.PikPakModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.pikpak.PikPakException.Code.*;

class PikPakModuleTest {
    static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");
    final InMemoryPikPakShareStore store = new InMemoryPikPakShareStore();
    final AtomicInteger requests = new AtomicInteger();
    String receivedSecret;
    Page response = new Page(List.of(new Entry("file-1", "", "video.mp4", Kind.VIDEO, 100L)), null);
    List<Media> media = List.of(new Media("original", "原始版本", URI.create("https://media.example/video?token=secret"), Protocol.PROGRESSIVE, "video/mp4", NOW.plusSeconds(60)));
    final PikPakShareGateway gateway = new PikPakShareGateway() {
        public Page list(PikPakShareLink link, String passcode, String parent, String cursor) {
            requests.incrementAndGet(); receivedSecret = passcode; return response;
        }
        public List<Media> resolve(PikPakShareLink link, String passcode, String file) {
            requests.incrementAndGet(); receivedSecret = passcode; return media;
        }
    };
    final PikPakModule module = new PikPakModule(store, gateway, Clock.fixed(NOW, ZoneOffset.UTC));
    @Test void retainsSubmittedUrlWhileCanonicalizingIdentity() {
        String url="https://MYPIKPAK.COM:443/s/Original/?view=grid";
        var saved=module.add(url,"original","","").share();
        assertEquals(url,saved.originalUrl());
        assertEquals("https://mypikpak.com/s/Original",saved.link().uri().toString());
        assertEquals(url,module.update(saved.id(),0,"renamed","",null).originalUrl());
        assertEquals(url,module.add("https://mypikpak.com/s/Original","duplicate","","").share().originalUrl());
    }
    @Test void exportIncludesAllPagesAndLegacyLinksWithoutSecrets() {
        for(int i=0;i<105;i++) module.add("https://mypikpak.com/s/export"+i+"?view=grid","item","","secret");
        String exported=module.exportLinks(null);
        assertEquals(105, exported.lines().count());
        assertTrue(exported.contains("https://mypikpak.com/s/export104?view=grid"));
        assertFalse(exported.contains("secret"));
        var old=new InMemoryPikPakShareStore();
        old.addIfAbsent(new PikPakShareLink("legacy"),"old","","",java.time.Instant.now());
        assertEquals("https://mypikpak.com/s/legacy\r\n",new PikPakModule(old,gateway,java.time.Clock.systemUTC()).exportLinks(null));
    }
    Share add() { return module.add("https://mypikpak.com/s/Example_123", "示例", "备注", "secret").share(); }

    @Test void normalizeOnlyKnownPresentationOptions() {
        assertEquals("https://mypikpak.com/s/Ab_12", PikPakShareLink.parse(" https://MYPIKPAK.COM:443/s/Ab_12/?view=grid&order=name_asc ").uri().toString());
        assertNotEquals(PikPakShareLink.parse("https://mypikpak.com/s/a"), PikPakShareLink.parse("https://mypikpak.com/s/A"));
    }
    @Test void rejectHostConfusionTraversalAndHiddenSecrets() {
        for (String input : List.of("http://mypikpak.com/s/abc", "https://mypikpak.com.evil.test/s/abc",
            "https://user@mypikpak.com/s/abc", "https://mypikpak.com:444/s/abc", "https://mypikpak.com/s/a%2Fb",
            "https://mypikpak.com/s/../abc", "https://mypikpak.com/s/abc/child", "https://mypikpak.com/s/abc?pwd=secret",
            "https://mypikpak.com/s/abc#secret", "https://mypikpak.com/s/abc\n")) {
            PikPakException e = assertThrows(PikPakException.class, () -> PikPakShareLink.parse(input));
            assertEquals(INVALID_INPUT, e.code()); assertFalse(e.getMessage().contains("secret"));
        }
    }
    @Test void addDoesNotContactProviderAndDuplicateDoesNotOverwriteSecret() {
        Share first = add();
        AddResult duplicate = module.add("https://mypikpak.com/s/Example_123?view=list", "changed", "", "new-secret");
        assertFalse(duplicate.created()); assertEquals(first, duplicate.share()); assertEquals(0, requests.get());
        module.browse(first.id(), "", ""); assertEquals("secret", receivedSecret);
    }
    @Test void batchRetainsValidRowsAndReportsEachInput() {
        List<ImportResult> results = module.addBatch(Arrays.asList(
            new ImportItem("https://mypikpak.com/s/a", "a", "", "x"),
            new ImportItem("https://evil.test/s/b", "b", "", ""), null,
            new ImportItem("https://mypikpak.com/s/a", "a2", "", "y")));
        assertTrue(results.get(0).result().created()); assertEquals(INVALID_INPUT, results.get(1).error());
        assertEquals(INVALID_INPUT, results.get(2).error()); assertFalse(results.get(3).result().created());
        assertEquals(1, module.list("", 0, 100).size()); assertEquals(0, requests.get());
    }
    @Test void updateDistinguishesPreserveReplaceAndClearSecret() {
        Share row = add(); row = module.update(row.id(), row.version(), "new", "", null);
        module.browse(row.id(), "", ""); assertEquals("secret", receivedSecret);
        row = module.update(row.id(), row.version(), "new", "", "replaced");
        module.browse(row.id(), "", ""); assertEquals("replaced", receivedSecret);
        row = module.update(row.id(), row.version(), "new", "", "");
        module.browse(row.id(), "", ""); assertEquals("", receivedSecret);
    }
    @Test void staleUpdateAndDeleteCannotDestroyNewChanges() {
        Share row = add(); module.update(row.id(), 0, "updated", "", null);
        assertEquals(CONFLICT, assertThrows(PikPakException.class, () -> module.update(row.id(), 0, "stale", "", "bad")).code());
        assertEquals(CONFLICT, assertThrows(PikPakException.class, () -> module.delete(row.id(), 0)).code());
        assertEquals("updated", module.get(row.id()).name()); assertEquals("secret", store.passcode(row.id()));
    }
    @Test void deletionDoesNotContactCloudAndDeletedRecordCannotPlay() {
        Share row = add(); module.delete(row.id(), 0);
        assertEquals(NOT_FOUND, assertThrows(PikPakException.class, () -> module.playback(row.id(), "file")).code());
        assertEquals(0, requests.get());
    }
    @Test void concurrentDuplicateInsertCreatesOneRecord() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int i = 0; i < 20; i++) jobs.add(() -> module.add("https://mypikpak.com/s/same", "same", "", "").created());
            int created = 0; for (Future<Boolean> f : pool.invokeAll(jobs)) if (f.get()) created++;
            assertEquals(1, created); assertEquals(1, module.list("", 0, 100).size());
        }
    }
    @Test void detectRepeatedCursor() {
        Share row = add(); response = new Page(List.of(), "same");
        assertEquals(INVALID_CURSOR, assertThrows(PikPakException.class, () -> module.browse(row.id(), "", "same")).code());
    }
    @Test void expiredMediaIsNotReturnedAndUrlsAreRedactedFromDiagnostics() {
        Share row = add(); Media good = media.getFirst();
        assertFalse(good.toString().contains("secret")); assertFalse(good.toString().contains("media.example"));
        media = List.of(new Media("old", "old", URI.create("https://media.example/old"), Protocol.PROGRESSIVE, "video/mp4", NOW));
        assertEquals(MEDIA_UNAVAILABLE, assertThrows(PikPakException.class, () -> module.playback(row.id(), "file")).code());
        media = List.of(good); assertEquals("original", module.playback(row.id(), "file").variants().getFirst().id());
    }
    @Test void changingShareDuringNetworkRequestRejectsStaleResult() {
        Share row = add();
        PikPakShareGateway mutating = new PikPakShareGateway() {
            public Page list(PikPakShareLink link, String pwd, String parent, String cursor) {
                module.update(row.id(), 0, "changed", "", "new"); return response;
            }
            public List<Media> resolve(PikPakShareLink link, String pwd, String file) { return media; }
        };
        PikPakModule reader = new PikPakModule(store, mutating, Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(CONFLICT, assertThrows(PikPakException.class, () -> reader.browse(row.id(), "", "")).code());
    }
    @Test void paginationAndBatchLimitsValidatedBeforeProviderAccess() {
        assertThrows(PikPakException.class, () -> module.list("", -1, 100));
        assertThrows(PikPakException.class, () -> module.list("", 0, 101));
        assertThrows(PikPakException.class, () -> module.addBatch(Collections.nCopies(21, new ImportItem("", "", "", ""))));
        assertEquals(0, requests.get());
    }
    @Test void publicDtosCannotExposePasscode() {
        Share share = add();
        assertFalse(share.toString().contains("secret"));
        assertFalse(new ImportItem("private-url", "", "", "secret").toString().contains("secret"));
        assertEquals(0, Arrays.stream(Share.class.getRecordComponents()).filter(c -> c.getName().contains("passcode")).count());
    }
}
