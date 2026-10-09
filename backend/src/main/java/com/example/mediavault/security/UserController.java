package com.example.mediavault.security;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users=users; }
    public record CreateRequest(String username,String password,String role) {}
    public record RoleRequest(String role,Long version) {}
    public record StatusRequest(Boolean enabled,Long version) {}
    public record ResetRequest(String password,Long version) {}
    @GetMapping public List<UserService.UserDto> list() { return users.list(); }
    @PostMapping public ResponseEntity<UserService.UserDto> create(@RequestBody CreateRequest b) { return ResponseEntity.status(201).body(users.create(b.username(),b.password(),b.role())); }
    @PutMapping("/{id}/role") public UserService.UserDto role(@PathVariable String id,@RequestBody RoleRequest b) { return users.role(id,b.role(),b.version()); }
    @PutMapping("/{id}/status") public UserService.UserDto status(@PathVariable String id,@RequestBody StatusRequest b) { return users.status(id,b.enabled(),b.version()); }
    @PostMapping("/{id}/reset-password") public ResponseEntity<Void> reset(@PathVariable String id,@RequestBody ResetRequest b) { users.resetPassword(id,b.password(),b.version());return ResponseEntity.noContent().build(); }
}
