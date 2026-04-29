package com.settlr.settlr_api.security;

import com.settlr.settlr_api.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request. Reads the Authorization header, validates the JWT,
 * checks the Redis blacklist (for logged-out tokens), and sets the
 * authenticated user in the SecurityContext.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Skip if no Bearer token — Spring Security will handle it as unauthenticated
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            // ── Blacklist check — reject tokens from logged-out sessions ─────
            // This check runs BEFORE signature verification for performance:
            // a quick Redis GET (O(1), sub-millisecond) short-circuits the
            // more expensive HMAC verification + DB lookup.
            if (tokenBlacklistService.isBlacklisted(jwt)) {
                log.warn("[JWT FILTER] Blacklisted token (logged out) | path={}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Token has been invalidated (logged out)\"}");
                return;   // do NOT continue the filter chain
            }

            String email = jwtService.extractEmail(jwt);

            // Only set authentication if not already set for this request
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtService.isTokenValid(jwt)) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("[JWT FILTER] Authenticated | email={} | path={}",
                            email, request.getRequestURI());
                } else {
                    log.warn("[JWT FILTER] Invalid/expired token | path={}", request.getRequestURI());
                }
            }
        } catch (Exception ex) {
            log.warn("[JWT FILTER] Token processing failed | path={} | reason={}",
                    request.getRequestURI(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
