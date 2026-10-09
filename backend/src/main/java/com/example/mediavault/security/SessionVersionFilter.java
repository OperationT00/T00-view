package com.example.mediavault.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class SessionVersionFilter extends OncePerRequestFilter {
    private final UserService users;
    SessionVersionFilter(UserService users) { this.users=users; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof VaultPrincipal old) {
            VaultPrincipal current=users.find(old.id());
            if(current==null || !current.enabled() || current.securityVersion()!=old.securityVersion()) {
                if(request.getSession(false)!=null) request.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
                json(response,401,"UNAUTHENTICATED","登录状态已失效，请重新登录");
                return;
            }
        }
        chain.doFilter(request,response);
    }
    static void json(HttpServletResponse r,int status,String code,String message) throws IOException {
        r.setStatus(status);r.setContentType("application/json");r.setCharacterEncoding("UTF-8");
        r.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\"}");
    }
}
