package com.example.pikpak.web;

import com.example.pikpak.*;
import com.example.pikpak.PikPakModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DuplicateKeyException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static com.example.pikpak.PikPakException.Code.*;

public final class JdbcPikPakStore implements PikPakShareStore {
    private final JdbcTemplate jdbc;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    private static final RowMapper<Share> ROW = (rs, n) -> new Share(rs.getString("id"), new PikPakShareLink(rs.getString("share_id")),
        rs.getString("name"), rs.getString("note"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getString("original_url"));
    public JdbcPikPakStore(JdbcTemplate jdbc, String encodedKey) {
        this.jdbc = jdbc;
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("PikPak encryption key must be base64"); }
        if (decoded.length != 32) throw new IllegalArgumentException("PikPak encryption key must contain 32 bytes");
        key = new SecretKeySpec(decoded, "AES");
    }
    @Override public AddResult addIfAbsent(PikPakShareLink link, String name, String note, String passcode, Instant now, String originalUrl) {
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO pikpak_share(id,share_id,name,note,passcode_cipher,version,created_at,updated_at,original_url) VALUES(?,?,?,?,?,0,?,?,?)",
                id, link.shareId(), name, note, encrypt(id, passcode == null ? "" : passcode), Timestamp.from(now), Timestamp.from(now), originalUrl);
            return new AddResult(get(id).orElseThrow(), true);
        } catch (DuplicateKeyException e) {
            return new AddResult(jdbc.queryForObject("SELECT * FROM pikpak_share WHERE share_id=?", ROW, link.shareId()), false);
        }
    }
    @Override public Optional<Share> get(String id) { return jdbc.query("SELECT * FROM pikpak_share WHERE id=?", ROW, id).stream().findFirst(); }
    @Override public List<Share> list(String query, int offset, int limit) {
        return jdbc.query("SELECT * FROM pikpak_share WHERE LOCATE(LOWER(?),LOWER(CONCAT(name,' ',note)))>0 ORDER BY created_at DESC,id LIMIT ? OFFSET ?", ROW, query, limit, offset);
    }
    @Override public String passcode(String id) {
        List<String> result = jdbc.query("SELECT passcode_cipher FROM pikpak_share WHERE id=?", (rs,n)->rs.getString(1), id);
        if (result.isEmpty()) throw new PikPakException(NOT_FOUND,"分享不存在");
        return decrypt(id, result.getFirst());
    }
    @Override public Share update(String id, long version, String name, String note, String passcode, Instant now) {
        int count = passcode == null
            ? jdbc.update("UPDATE pikpak_share SET name=?,note=?,version=version+1,updated_at=? WHERE id=? AND version=?", name,note,Timestamp.from(now),id,version)
            : jdbc.update("UPDATE pikpak_share SET name=?,note=?,passcode_cipher=?,version=version+1,updated_at=? WHERE id=? AND version=?", name,note,encrypt(id,passcode),Timestamp.from(now),id,version);
        changed(count); return get(id).orElseThrow(()->new PikPakException(NOT_FOUND,"分享已移除"));
    }
    @Override public void delete(String id, long version) { changed(jdbc.update("DELETE FROM pikpak_share WHERE id=? AND version=?",id,version)); }
    private void changed(int count) { if(count!=1)throw new PikPakException(CONFLICT,"记录已变化，请刷新后重试"); }
    private String encrypt(String id, String value) {
        if (value.isEmpty()) return "";
        byte[] iv = new byte[12]; random.nextBytes(iv);
        try { Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv)); c.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=c.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(encrypted);
        } catch(Exception e) { throw new IllegalStateException("PikPak secret encryption failed"); }
    }
    private String decrypt(String id,String value) {
        if(value.isEmpty())return "";
        try { String[] pieces=value.split(":",2); Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(pieces[0]))); c.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(Base64.getDecoder().decode(pieces[1])),StandardCharsets.UTF_8);
        } catch(Exception e) { throw new PikPakException(UPSTREAM_UNAVAILABLE,"提取码无法解密，请检查服务器密钥"); }
    }
}
