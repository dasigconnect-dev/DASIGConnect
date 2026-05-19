package com.dasigconnect.backend.security;

import com.dasigconnect.backend.service.JWTService;
import com.dasigconnect.backend.service.TenantScopeService;
import io.jsonwebtoken.Claims;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JWTService jwtService;
    private final TenantScopeService tenantScopeService;

    public JwtAuthenticationFilter(JWTService jwtService, TenantScopeService tenantScopeService) {
        this.jwtService = jwtService;
        this.tenantScopeService = tenantScopeService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                if (jwtService.validateToken(token)) {
                    Claims claims = jwtService.extractClaims(token);
                    String role = claims.getOrDefault("role", "").toString();
                    String userIdStr = claims.getOrDefault("user_id", "").toString();
                    String email = claims.getOrDefault("email", "").toString();
                    Object instClaim = claims.get("institution_id");
                    String instStr = instClaim != null ? instClaim.toString() : null;

                    UUID userId = null;
                    UUID institutionId = null;
                    try {
                        if (!userIdStr.isBlank()) userId = UUID.fromString(userIdStr);
                    } catch (IllegalArgumentException ex) {
                        log.debug("Invalid user_id in token: {}", userIdStr);
                    }
                    try {
                        if (instStr != null && !instStr.isBlank()) institutionId = UUID.fromString(instStr);
                    } catch (IllegalArgumentException ex) {
                        log.debug("Invalid institution_id in token: {}", instStr);
                    }

                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (!role.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                    }

                    JwtUserDetails principal = new JwtUserDetails(userId, email, role, institutionId);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    tenantScopeService.bindTenantScope(institutionId, role);
                }
            } catch (Exception ex) {
                log.debug("JWT validation failed: {}", ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
