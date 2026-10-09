package com.example.mediavault.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean static PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
    @Bean SecurityFilterChain chain(HttpSecurity http,UserService users) throws Exception {
        http.authorizeHttpRequests(a->a
            .requestMatchers(HttpMethod.GET,"/","/index.html","/assets/**","/favicon.svg").permitAll()
            .requestMatchers(HttpMethod.GET,"/api/auth/csrf").permitAll()
            .requestMatchers(HttpMethod.GET,"/api/pikpak/duplicate-scan").permitAll()
            .requestMatchers(HttpMethod.POST,"/api/pikpak/duplicate-scan","/api/pikpak/duplicate-scan/*/cancel").permitAll()
            .requestMatchers(HttpMethod.GET,"/api/pikpak/shares","/api/pikpak/shares/*","/api/pikpak/shares/*/entries").permitAll()
            .requestMatchers(HttpMethod.POST,"/api/pikpak/shares","/api/pikpak/shares/batch","/api/pikpak/playback").permitAll()
            .requestMatchers(HttpMethod.PUT,"/api/pikpak/shares/*").permitAll()
            .requestMatchers(HttpMethod.DELETE,"/api/pikpak/shares/*").permitAll()
            .requestMatchers(HttpMethod.POST,"/api/auth/login").permitAll()
            .requestMatchers(HttpMethod.GET,"/api/auth/me").authenticated()
            .requestMatchers(HttpMethod.PUT,"/api/auth/password").authenticated()
            .requestMatchers(HttpMethod.POST,"/api/auth/logout").authenticated()
            .requestMatchers("/api/admin/users","/api/admin/users/*/role","/api/admin/users/*/status","/api/admin/users/*/reset-password").hasRole("ADMIN")
            .requestMatchers("/api/v2/legacy-migration","/api/v2/legacy-migration/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.POST,"/api/v2/parsing/preview").hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET,"/api/v2/catalog/search","/api/v2/catalog/types","/api/v2/catalog/types/*","/api/v2/catalog/work-scopes","/api/v2/catalog/work-scopes/*","/api/v2/catalog/characters","/api/v2/catalog/characters/*","/api/v2/catalog/skins","/api/v2/catalog/skins/*","/api/v2/catalog/clothing","/api/v2/catalog/clothing/*","/api/v2/catalog/creators","/api/v2/catalog/creators/*","/api/v2/assets","/api/v2/assets/*").hasAnyRole("ADMIN","VIEWER")
            .requestMatchers(HttpMethod.POST,"/api/v2/catalog/types","/api/v2/catalog/work-scopes","/api/v2/catalog/characters","/api/v2/catalog/skins","/api/v2/catalog/clothing","/api/v2/catalog/creators","/api/v2/assets","/api/v2/assets/*/locations").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT,"/api/v2/catalog/types/*","/api/v2/catalog/work-scopes/*","/api/v2/catalog/characters/*","/api/v2/catalog/skins/*","/api/v2/catalog/clothing/*","/api/v2/catalog/creators/*","/api/v2/assets/*","/api/v2/assets/*/locations/*").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE,"/api/v2/catalog/types/*","/api/v2/catalog/work-scopes/*","/api/v2/catalog/characters/*","/api/v2/catalog/skins/*","/api/v2/catalog/clothing/*","/api/v2/catalog/creators/*","/api/v2/assets/*","/api/v2/assets/*/locations/*").hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET,"/api/assets","/api/assets/*","/api/tags","/api/tags/tree","/api/sources").hasAnyRole("ADMIN","VIEWER")
            .requestMatchers(HttpMethod.POST,"/api/assets","/api/assets/*/locations","/api/tags","/api/sources").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT,"/api/assets/*","/api/assets/*/locations/*","/api/tags/*","/api/sources/*","/api/sources/*/reported-status").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE,"/api/assets/*","/api/assets/*/locations/*","/api/tags/*","/api/sources/*").hasRole("ADMIN")
            .anyRequest().denyAll())
            .formLogin(f->f.loginProcessingUrl("/api/auth/login")
                .successHandler((req,res,auth)->{res.setContentType("application/json");res.getWriter().write("{\"ok\":true}");})
                .failureHandler((req,res,e)->SessionVersionFilter.json(res,401,"INVALID_CREDENTIALS","用户名或密码不正确")))
            .logout(l->l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204)))
            .requestCache(c->c.requestCache(new NullRequestCache()))
            .exceptionHandling(e->e
                .authenticationEntryPoint((req,res,x)->SessionVersionFilter.json(res,401,"UNAUTHENTICATED","请先登录"))
                .accessDeniedHandler((req,res,x)->SessionVersionFilter.json(res,403,"FORBIDDEN","没有权限或请求校验未通过，请刷新后重试")))
            .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; media-src 'self' blob: https://*.mypikpak.com https://*.mypikpak.net; connect-src 'self' https://*.mypikpak.com https://*.mypikpak.net; worker-src 'self' blob:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")))
            .addFilterAfter(new SessionVersionFilter(users),SecurityContextHolderFilter.class);
        return http.build();
    }
}
