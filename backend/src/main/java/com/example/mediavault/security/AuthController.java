package com.example.mediavault.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService users;
    public AuthController(UserService users) { this.users=users; }
    public record CsrfDto(String token,String headerName) {}
    public record PasswordRequest(String oldPassword,String newPassword) {}
    @GetMapping("/csrf") public CsrfDto csrf(CsrfToken token) { return new CsrfDto(token.getToken(),token.getHeaderName()); }
    @GetMapping("/me") public UserService.UserDto me(@AuthenticationPrincipal VaultPrincipal principal) { return users.dto(principal); }
    @PutMapping("/password") public ResponseEntity<Void> password(@AuthenticationPrincipal VaultPrincipal p,@RequestBody PasswordRequest body,HttpServletRequest request) {
        users.changePassword(p.id(),body.oldPassword(),body.newPassword());
        request.getSession().invalidate();
        return ResponseEntity.noContent().build();
    }
}
