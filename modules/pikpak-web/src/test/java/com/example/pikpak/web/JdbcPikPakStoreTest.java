package com.example.pikpak.web;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.example.pikpak.*;
import java.time.Instant;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class JdbcPikPakStoreTest {
    JdbcTemplate jdbc;
    JdbcPikPakStore store;
    final String key=Base64.getEncoder().encodeToString(new byte[32]);
    @BeforeEach void setup(){
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE pikpak_share(id VARCHAR(36) PRIMARY KEY,share_id VARCHAR(128) NOT NULL UNIQUE,name VARCHAR(255),note TEXT,passcode_cipher TEXT,version BIGINT,created_at TIMESTAMP(6),updated_at TIMESTAMP(6),original_url VARCHAR(4096))");
        store=new JdbcPikPakStore(jdbc,key);
    }
    @Test void storesEncryptedSecretAndSurvivesAdapterReconstruction(){
        var row=store.addIfAbsent(new PikPakShareLink("test"),"name","note","secret-pass",Instant.now()).share();
        assertFalse(jdbc.queryForObject("SELECT passcode_cipher FROM pikpak_share",String.class).contains("secret-pass"));
        assertEquals("secret-pass",new JdbcPikPakStore(jdbc,key).passcode(row.id()));
    }
    @Test void deduplicatesWithoutOverwriting(){
        var first=store.addIfAbsent(new PikPakShareLink("test"),"name","","old",Instant.now());
        var second=store.addIfAbsent(new PikPakShareLink("test"),"new","","new",Instant.now());
        assertFalse(second.created());assertEquals(first.share().id(),second.share().id());assertEquals("old",store.passcode(first.share().id()));
    }
    @Test void updatesAreOptimisticAndSecretModesWork(){
        var row=store.addIfAbsent(new PikPakShareLink("test"),"name","","old",Instant.now()).share();
        store.update(row.id(),0,"new","",null,Instant.now());assertEquals("old",store.passcode(row.id()));
        assertThrows(PikPakException.class,()->store.delete(row.id(),0));
        store.update(row.id(),1,"new","","",Instant.now());assertEquals("",store.passcode(row.id()));
        store.delete(row.id(),2);assertTrue(store.get(row.id()).isEmpty());
    }
    @Test void ciphertextCannotBeMovedToAnotherShare(){
        var a=store.addIfAbsent(new PikPakShareLink("a"),"a","","x",Instant.now()).share();
        var b=store.addIfAbsent(new PikPakShareLink("b"),"b","","y",Instant.now()).share();
        String cipher=jdbc.queryForObject("SELECT passcode_cipher FROM pikpak_share WHERE id=?",String.class,a.id());
        jdbc.update("UPDATE pikpak_share SET passcode_cipher=? WHERE id=?",cipher,b.id());
        assertThrows(PikPakException.class,()->store.passcode(b.id()));
    }
    @Test void preservesOriginalUrlAcrossReloadEditAndDuplicateImport(){
        String original="https://MYPIKPAK.COM:443/s/test/?view=grid&order=name_asc";
        var first=store.addIfAbsent(new PikPakShareLink("test"),"name","","",Instant.now(),original).share();
        var reloaded=new JdbcPikPakStore(jdbc,key);
        assertEquals(original,reloaded.get(first.id()).orElseThrow().originalUrl());
        assertEquals(original,reloaded.update(first.id(),0,"renamed","",null,Instant.now()).originalUrl());
        assertEquals(original,reloaded.addIfAbsent(new PikPakShareLink("test"),"duplicate","","",Instant.now(),"https://mypikpak.com/s/test").share().originalUrl());
        assertEquals(original,reloaded.list("",0,50).getFirst().originalUrl());
    }
    @Test void searchTreatsWildcardsAsText(){
        store.addIfAbsent(new PikPakShareLink("a"),"100%","","",Instant.now());
        store.addIfAbsent(new PikPakShareLink("b"),"other","","",Instant.now());
        assertEquals(1,store.list("%",0,50).size());
    }
}
