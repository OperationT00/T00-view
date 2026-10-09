package com.example.mediavault.security;

import com.example.mediavault.common.ApiException;
import com.example.mediavault.common.GuardService;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final GuardService guards;
    private final PasswordEncoder encoder;
    public record UserDto(String id, String username, String role, boolean enabled, long version) {}
    public UserService(JdbcTemplate jdbc, GuardService guards, PasswordEncoder encoder) {
        this.jdbc=jdbc; this.guards=guards; this.encoder=encoder;
    }
    @Override public UserDetails loadUserByUsername(String username) {
        return jdbc.query("SELECT * FROM app_user WHERE normalized_username=?",this::map,normalize(username))
            .stream().findFirst().orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));
    }
    public VaultPrincipal find(long id) {
        return jdbc.query("SELECT * FROM app_user WHERE id=?",this::map,id).stream().findFirst().orElse(null);
    }
    public UserDto dto(VaultPrincipal p) { return new UserDto(Long.toString(p.id()),p.username(),p.role(),p.enabled(),p.version()); }
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> list() { return jdbc.query("SELECT * FROM app_user ORDER BY id",this::map).stream().map(this::dto).toList(); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void bootstrap(String username, String password) {
        guards.lockAdmins();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Long.class)>0) return;
        if (username==null || username.isBlank() || password==null || password.isBlank())
            throw new IllegalStateException("Empty user database: set BOOTSTRAP_USERNAME and BOOTSTRAP_PASSWORD for first startup");
        insert(username,password,"ADMIN");
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public UserDto create(String username,String password,String role) {
        guards.lockAdmins();
        long id=insert(username,password,role==null?"VIEWER":role);
        return dto(find(id));
    }
    private long insert(String username,String password,String role) {
        String name=validName(username); validatePassword(password); validateRole(role);
        jdbc.update("INSERT INTO app_user(username,normalized_username,password_hash,role) VALUES (?,?,?,?)",
            name,normalize(name),encoder.encode(password),role);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public UserDto role(String id,String role,Long version) {
        validateRole(role); guards.lockAdmins(); VaultPrincipal target=locked(id,version);
        protectAdmin(target,role,target.enabled());
        jdbc.update("UPDATE app_user SET role=?,security_version=security_version+1,version=version+1 WHERE id=?",role,target.id());
        return dto(find(target.id()));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public UserDto status(String id,Boolean enabled,Long version) {
        if (enabled==null) throw ApiException.badRequest("启用状态不能为空");
        guards.lockAdmins(); VaultPrincipal target=locked(id,version);
        protectAdmin(target,target.role(),enabled);
        jdbc.update("UPDATE app_user SET enabled=?,security_version=security_version+1,version=version+1 WHERE id=?",enabled,target.id());
        return dto(find(target.id()));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void resetPassword(String id,String password,Long version) {
        validatePassword(password); guards.lockAdmins(); VaultPrincipal target=locked(id,version);
        updatePassword(target.id(),password);
    }
    @PreAuthorize("isAuthenticated()")
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void changePassword(long id,String oldPassword,String newPassword) {
        validatePassword(newPassword);
        VaultPrincipal target=jdbc.query("SELECT * FROM app_user WHERE id=? FOR UPDATE",this::map,id).stream().findFirst()
            .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","请重新登录"));
        if (oldPassword==null || !encoder.matches(oldPassword,target.passwordHash()))
            throw ApiException.badRequest("旧密码不正确");
        updatePassword(id,newPassword);
    }
    private void updatePassword(long id,String password) {
        jdbc.update("UPDATE app_user SET password_hash=?,security_version=security_version+1,version=version+1 WHERE id=?",encoder.encode(password),id);
    }
    private VaultPrincipal locked(String id,Long version) {
        if(version==null || version<0) throw ApiException.badRequest("需要有效的记录版本");
        VaultPrincipal p=jdbc.query("SELECT * FROM app_user WHERE id=? FOR UPDATE",this::map,parseId(id)).stream().findFirst()
            .orElseThrow(()->ApiException.notFound("账号不存在"));
        if(p.version()!=version) throw ApiException.conflict("账号已发生变化，请刷新后重试");
        return p;
    }
    private void protectAdmin(VaultPrincipal target,String role,boolean enabled) {
        if(target.enabled() && target.role().equals("ADMIN") && (!enabled || !role.equals("ADMIN")) &&
            jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE enabled=TRUE AND role='ADMIN'",Long.class)<=1)
            throw ApiException.conflict("至少需要保留一个启用的管理员");
    }
    private VaultPrincipal map(ResultSet rs,int row) throws SQLException {
        return new VaultPrincipal(rs.getLong("id"),rs.getString("username"),rs.getString("password_hash"),rs.getString("role"),
            rs.getBoolean("enabled"),rs.getLong("security_version"),rs.getLong("version"));
    }
    public static long parseId(String id) {
        try { long n=Long.parseLong(id); if(n>0) return n; } catch(RuntimeException ignored) {}
        throw ApiException.badRequest("无效的记录 ID");
    }
    public static String normalize(String s) { return Normalizer.normalize(s==null?"":s.strip(),Normalizer.Form.NFC).toLowerCase(Locale.ROOT); }
    private static String validName(String s) {
        if(s==null) throw ApiException.badRequest("用户名需要 1 至 64 个字符");
        String name=Normalizer.normalize(s.strip(),Normalizer.Form.NFC);
        String normalized=normalize(name);
        if(name.isEmpty() || name.codePointCount(0,name.length())>64 || normalized.codePointCount(0,normalized.length())>64
            || name.codePoints().anyMatch(Character::isISOControl))
            throw ApiException.badRequest("用户名需要 1 至 64 个字符");
        return name;
    }
    private static void validateRole(String role) {
        if(!"ADMIN".equals(role) && !"VIEWER".equals(role)) throw ApiException.badRequest("无效的用户角色");
    }
    private static void validatePassword(String password) {
        if(password==null || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw ApiException.badRequest("密码至少 12 个字符，UTF-8 编码不超过 72 字节");
    }
}
